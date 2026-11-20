package gt.supermercado.pos.clientes.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.clientes.dominio.Cliente;
import gt.supermercado.pos.clientes.dominio.FiltroClientes;
import gt.supermercado.pos.clientes.dominio.PaginaClientes;
import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import gt.supermercado.pos.compartido.dominio.Correo;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.Nit;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.facturacion.aplicacion.ConsultaContribuyentes;
import gt.supermercado.pos.facturacion.aplicacion.ConsultaContribuyentes.ConsultaNit;
import gt.supermercado.pos.facturacion.aplicacion.ReenvioFacturas;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * EP-05: el cliente de la venta.
 * <ul>
 *   <li>HU-05.01: sin cliente, la venta es a Consumidor Final; volver a CF es una sola acción.</li>
 *   <li>HU-05.02: con un NIT registrado se cargan su nombre y su dirección; el NIT se acepta con o sin guion.</li>
 *   <li>HU-05.03: un NIT con el dígito verificador incorrecto no se usa ({@code NIT_INVALIDO}).</li>
 *   <li>HU-05.04: un NIT válido que no está registrado se consulta en el certificador; si aparece, el cliente se
 *       guarda para las próximas compras. Si no aparece o el servicio no responde, se avisa y el cajero escribe el
 *       nombre o factura como CF.</li>
 *   <li>HU-05.05: el registro pide NIT y nombre; la dirección es "Ciudad" si no se indica y el correo es opcional.
 *       Un NIT ya registrado no se duplica. Queda en la bitácora con quien lo registró.</li>
 * </ul>
 * El CUI (HU-05.06), el límite para CF (HU-05.07) y la edición de clientes (HU-05.09) llegan después.
 */
@Service
public class GestionClientes {

    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final RepositorioClientes clientes;
    private final ConsultaContribuyentes contribuyentes;
    private final GestionVentas ventas;
    private final Bitacora bitacora;
    private final ReenvioFacturas reenvio;
    private final TransactionOperations transacciones;

    public GestionClientes(RepositorioClientes clientes, ConsultaContribuyentes contribuyentes, GestionVentas ventas,
                           Bitacora bitacora, ReenvioFacturas reenvio, TransactionOperations transacciones) {
        this.clientes = clientes;
        this.contribuyentes = contribuyentes;
        this.ventas = ventas;
        this.bitacora = bitacora;
        this.reenvio = reenvio;
        this.transacciones = transacciones;
    }

    /** Cómo terminó la búsqueda por NIT. */
    public enum Origen {
        /** Ya estaba registrado (HU-05.02 crit. 1). */
        REGISTRADO,
        /** No estaba registrado; el certificador dio el nombre y se registró (HU-05.04 crit. 2). */
        CONSULTADO,
        /** El certificador no conoce el NIT (HU-05.04 crit. 3). */
        NO_EXISTE,
        /** El certificador no respondió a tiempo (HU-05.04 crit. 3 y 4). */
        SIN_CONSULTA
    }

    /** El resultado de buscar un NIT: el cliente si se encontró, o qué decirle al cajero. */
    public record BusquedaNit(Origen origen, String nit, Cliente cliente, String mensaje) {}

    public record SolicitudCliente(String nit, String nombre, String direccion, String correo) {}

    /** HU-05.10: clientes registrados, con búsqueda y paginación. */
    @Transactional(readOnly = true)
    public PaginaClientes listar(FiltroClientes filtro) {
        return clientes.listar(filtro);
    }

    @Transactional
    public BusquedaNit buscar(String nit, SesionActiva sesion) {
        String valido = nitValido(nit);
        Optional<Cliente> registrado = clientes.porNit(valido);
        if (registrado.isPresent()) {
            return new BusquedaNit(Origen.REGISTRADO, valido, registrado.get(), null);
        }
        ConsultaNit consulta = contribuyentes.porNit(valido);
        return switch (consulta.resultado()) {
            case ENCONTRADO -> new BusquedaNit(Origen.CONSULTADO, valido,
                    registrar(valido, consulta.nombre(), Cliente.DIRECCION_POR_OMISION, null, sesion,
                            "consultado en el certificador"), null);
            case NO_EXISTE -> new BusquedaNit(Origen.NO_EXISTE, valido, null,
                    "El certificador no encontró el NIT %s. Escriba el nombre del cliente o facture como CF."
                            .formatted(valido));
            case NO_DISPONIBLE -> new BusquedaNit(Origen.SIN_CONSULTA, valido, null,
                    "No se pudo consultar el NIT en este momento. Escriba el nombre del cliente o facture como CF.");
        };
    }

    /** HU-05.05: registra al cliente; si el NIT ya está registrado, devuelve el existente sin duplicarlo (crit. 2). */
    @Transactional
    public Cliente registrar(SolicitudCliente solicitud, SesionActiva sesion) {
        String nit;
        String nombre;
        String direccion;
        String correo;
        try {
            nit = nitValido(solicitud.nit());
            nombre = texto(solicitud.nombre());
            if (nombre.isEmpty() || nombre.length() > 200) {
                throw new NegocioException("NOMBRE_CLIENTE_REQUERIDO", "Escriba el nombre del cliente (hasta 200 caracteres).");
            }
            direccion = texto(solicitud.direccion());
            if (direccion.isEmpty()) {
                direccion = Cliente.DIRECCION_POR_OMISION;
            } else if (direccion.length() > 300) {
                throw new NegocioException("DIRECCION_CLIENTE_INVALIDA", "La dirección admite hasta 300 caracteres.");
            }
            correo = texto(solicitud.correo());
            if (correo.isEmpty()) {
                correo = null;
            } else if (correo.length() > 254 || !CORREO.matcher(correo).matches()) {
                throw new NegocioException("CORREO_INVALIDO", "El correo no es válido, por ejemplo cliente@correo.com.");
            }
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false, "Registro de cliente rechazado: "
                    + error.getMessage(), null, null));
            throw error;
        }
        Optional<Cliente> existente = clientes.porNit(nit);
        if (existente.isPresent()) {
            return existente.get();
        }
        return registrar(nit, nombre, direccion, correo, sesion, "registrado por el cajero");
    }

    /**
     * El cliente de la venta en curso (vacío si es a Consumidor Final) y el correo al que se le enviará la factura
     * (HU-05.11), que puede faltar.
     */
    public record ClienteYCorreo(Optional<Cliente> cliente, String correoFactura) {}

    @Transactional(readOnly = true)
    public ClienteYCorreo deLaVentaActual(SesionActiva sesion) {
        return ventas.actual(sesion)
                .map(venta -> new ClienteYCorreo(clientes.deLaVenta(venta.id()),
                        ventas.correoFactura(venta.id()).orElse(null)))
                .orElseGet(() -> new ClienteYCorreo(Optional.empty(), null));
    }

    /**
     * Asigna el cliente a la venta en curso; {@code null} vuelve a Consumidor Final (HU-05.01 crit. 2). HU-05.11 crit.
     * 4: la factura se enviará al correo del cliente, si lo tiene; a Consumidor Final, a ninguno.
     */
    @Transactional
    public ClienteYCorreo asignar(Long clienteId, SesionActiva sesion) {
        Optional<Cliente> cliente = clienteId == null
                ? Optional.empty()
                : Optional.of(clientes.porId(clienteId).orElseThrow(() -> new RecursoNoEncontradoException(
                        "CLIENTE_NO_ENCONTRADO", "El cliente no existe.")));
        ventas.asignarCliente(sesion, clienteId);
        String correo = cliente.map(Cliente::correo).orElse(null);
        ventas.asignarCorreoFactura(sesion, correo);
        return new ClienteYCorreo(cliente, correo);
    }

    /**
     * HU-05.11 crit. 1 a 3: el cajero agrega, cambia o quita el correo al que se envía la factura de la venta en curso.
     * Vacío no envía la factura (crit. 2: es opcional, sin avisos). Con un cliente asignado, el correo nuevo también
     * queda en el cliente para sus próximas compras; quitarlo de la venta no lo borra del cliente.
     */
    @Transactional
    public ClienteYCorreo cambiarCorreoFactura(String correo, SesionActiva sesion) {
        String nuevo = Correo.opcional(correo, "CORREO_INVALIDO").orElse(null);
        long ventaId = ventas.asignarCorreoFactura(sesion, nuevo).id();
        Optional<Cliente> cliente = clientes.deLaVenta(ventaId);
        if (nuevo != null && cliente.isPresent() && !nuevo.equalsIgnoreCase(String.valueOf(cliente.get().correo()))) {
            clientes.cambiarCorreo(cliente.get().id(), nuevo);
            cliente = clientes.porId(cliente.get().id());
        }
        return new ClienteYCorreo(cliente, nuevo);
    }

    /**
     * HU-08.06 crit. 2: el cajero corrige el cliente de su venta ya cobrada cuya factura se rechazó por el receptor, y
     * la reenvía sin volver a cobrar. {@code clienteId} vacío la reenvía a Consumidor Final.
     */
    public FacturaDeVenta reenviarFactura(long ventaId, Long clienteId, SesionActiva sesion) {
        Receptor receptor = clienteId == null
                ? Receptor.consumidorFinal()
                : clientes.porId(clienteId)
                        .map(cliente -> new Receptor(Receptor.TipoReceptor.NIT, cliente.nit(), cliente.nombre()))
                        .orElseThrow(() -> new RecursoNoEncontradoException("CLIENTE_NO_ENCONTRADO",
                                "El cliente no existe."));
        transacciones.executeWithoutResult(transaccion -> {
            reenvio.reabrir(ventaId, receptor, true, sesion);
            ventas.corregirCliente(ventaId, clienteId);
        });
        return reenvio.certificar(ventaId, sesion);
    }

    private Cliente registrar(String nit, String nombre, String direccion, String correo, SesionActiva sesion,
                              String como) {
        long id;
        try {
            id = clientes.registrar(nit, nombre, direccion, correo, sesion.usuarioId());
        } catch (DuplicateKeyException e) {
            // Otra caja lo registró al mismo tiempo: se usa el existente (HU-05.05 crit. 2).
            return clientes.porNit(nit).orElseThrow();
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nit", EventoBitacora.cambio("", nit));
        datos.put("nombre", EventoBitacora.cambio("", nombre));
        datos.put("direccion", EventoBitacora.cambio("", direccion));
        if (correo != null) {
            datos.put("correo", EventoBitacora.cambio("", correo));
        }
        bitacora.registrar(evento(sesion, true, "Cliente NIT %s registrado (%s): %s".formatted(nit, como, nombre), id,
                datos));
        return clientes.porId(id).orElseThrow();
    }

    /** HU-05.03: el NIT en formato uniforme, solo si su dígito verificador es correcto (crit. 1 a 3). */
    private static String nitValido(String nit) {
        return Nit.validar(nit).orElseThrow(() -> new NegocioException("NIT_INVALIDO",
                "El NIT no es válido: revise los dígitos y el verificador (puede terminar en K)."));
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion, Long clienteId,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.CLIENTE_REGISTRADO, exitoso,
                descripcion, "cliente", clienteId, cambios);
    }
}
