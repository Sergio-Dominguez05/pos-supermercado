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
import gt.supermercado.pos.ventas.dominio.ValidadorClienteDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
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
 * HU-05.06: sin NIT, el cliente se busca y se registra con su CUI (13 dígitos), cuyo nombre también se consulta al
 * certificador; si la consulta falla, el cajero escribe el nombre. El límite para CF (HU-05.07) lo aplica
 * {@code LimiteConsumidorFinal}. La edición de clientes (HU-05.09) llega después.
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
    private final ValidadorClienteDeVenta validador;

    public GestionClientes(RepositorioClientes clientes, ConsultaContribuyentes contribuyentes, GestionVentas ventas,
                           Bitacora bitacora, ReenvioFacturas reenvio, TransactionOperations transacciones,
                           ValidadorClienteDeVenta validador) {
        this.clientes = clientes;
        this.contribuyentes = contribuyentes;
        this.ventas = ventas;
        this.bitacora = bitacora;
        this.reenvio = reenvio;
        this.transacciones = transacciones;
        this.validador = validador;
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

    /**
     * El resultado de buscar un NIT o un CUI: el cliente si se encontró, o qué decirle al cajero. {@code nit} es la
     * identificación normalizada y {@code tipoIdentificacion}, {@code NIT} o {@code CUI}.
     */
    public record BusquedaNit(Origen origen, String nit, Cliente cliente, String mensaje, String tipoIdentificacion) {}

    /** {@code tipoIdentificacion} vacío es NIT; con {@code CUI}, {@code nit} lleva el CUI (HU-05.06). */
    public record SolicitudCliente(String nit, String nombre, String direccion, String correo,
                                   String tipoIdentificacion) {}

    /** HU-05.10: clientes registrados, con búsqueda y paginación. */
    @Transactional(readOnly = true)
    public PaginaClientes listar(FiltroClientes filtro) {
        return clientes.listar(filtro);
    }

    @Transactional
    public BusquedaNit buscar(String nit, SesionActiva sesion) {
        String valido = nitValido(nit);
        return buscar(Cliente.NIT, valido, clientes.porNit(valido), () -> contribuyentes.porNit(valido), sesion);
    }

    /** HU-05.06 crit. 2 y 3: el CUI debe tener 13 dígitos; su nombre se consulta al certificador. */
    @Transactional
    public BusquedaNit buscarCui(String cui, SesionActiva sesion) {
        String valido = cuiValido(cui);
        return buscar(Cliente.CUI, valido, clientes.porCui(valido), () -> contribuyentes.porCui(valido), sesion);
    }

    private BusquedaNit buscar(String tipo, String valido, Optional<Cliente> registrado,
                               java.util.function.Supplier<ConsultaNit> consultar, SesionActiva sesion) {
        if (registrado.isPresent()) {
            return new BusquedaNit(Origen.REGISTRADO, valido, registrado.get(), null, tipo);
        }
        ConsultaNit consulta = consultar.get();
        return switch (consulta.resultado()) {
            case ENCONTRADO -> new BusquedaNit(Origen.CONSULTADO, valido,
                    registrar(tipo, valido, consulta.nombre(), Cliente.DIRECCION_POR_OMISION, null, sesion,
                            "consultado en el certificador"), null, tipo);
            case NO_EXISTE -> new BusquedaNit(Origen.NO_EXISTE, valido, null,
                    "El certificador no encontró el %s %s. Escriba el nombre del cliente o facture como CF."
                            .formatted(tipo, valido), tipo);
            case NO_DISPONIBLE -> new BusquedaNit(Origen.SIN_CONSULTA, valido, null,
                    "No se pudo consultar el %s en este momento. Escriba el nombre del cliente o facture como CF."
                            .formatted(tipo), tipo);
        };
    }

    /** HU-05.05: registra al cliente; si el NIT ya está registrado, devuelve el existente sin duplicarlo (crit. 2). */
    @Transactional
    public Cliente registrar(SolicitudCliente solicitud, SesionActiva sesion) {
        String tipo;
        String nit;
        String nombre;
        String direccion;
        String correo;
        try {
            tipo = Cliente.CUI.equalsIgnoreCase(texto(solicitud.tipoIdentificacion())) ? Cliente.CUI : Cliente.NIT;
            nit = tipo.equals(Cliente.CUI) ? cuiValido(solicitud.nit()) : nitValido(solicitud.nit());
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
        Optional<Cliente> existente = tipo.equals(Cliente.CUI) ? clientes.porCui(nit) : clientes.porNit(nit);
        if (existente.isPresent()) {
            return existente.get();
        }
        return registrar(tipo, nit, nombre, direccion, correo, sesion, "registrado por el cajero");
    }

    /**
     * El cliente de la venta en curso (vacío si es a Consumidor Final) y el correo al que se le enviará la factura
     * (HU-05.11), que puede faltar. {@code aviso}: HU-05.08, si el cambio de cliente impedirá cobrar la venta (por
     * ejemplo, Consumidor Final desde el límite de HU-05.07); {@code null} si no hay nada que advertir.
     */
    public record ClienteYCorreo(Optional<Cliente> cliente, String correoFactura, String aviso) {

        public ClienteYCorreo(Optional<Cliente> cliente, String correoFactura) {
            this(cliente, correoFactura, null);
        }
    }

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
     *
     * <p>HU-05.08 Cambiar el cliente de una venta antes de cobrar:
     * <ul>
     *   <li>Crit. 1: se cambia en cualquier momento mientras la venta esté en curso, también en el cobro con un pago ya
     *       registrado. Si el cambio impide cobrar (Consumidor Final desde el límite, HU-05.07), se acepta igual
     *       (el cajero puede estar corrigiendo) pero se devuelve el {@code aviso}; finalizar se seguirá rechazando.</li>
     *   <li>Crit. 2: una vez finalizada la venta ya no hay venta en curso: el cambio se rechaza con
     *       {@code SIN_VENTA_EN_CURSO} y para corregir el cliente hay que anular la factura (EP-08). La única
     *       excepción es el reenvío de una factura rechazada por el receptor (HU-08.06), que no se emitió.</li>
     * </ul>
     * El cambio queda en la bitácora con el cliente anterior y el nuevo.
     */
    @Transactional
    public ClienteYCorreo asignar(Long clienteId, SesionActiva sesion) {
        Optional<Cliente> cliente = clienteId == null
                ? Optional.empty()
                : Optional.of(clientes.porId(clienteId).orElseThrow(() -> new RecursoNoEncontradoException(
                        "CLIENTE_NO_ENCONTRADO", "El cliente no existe.")));
        Optional<Cliente> anterior = ventas.actual(sesion).flatMap(enCurso -> clientes.deLaVenta(enCurso.id()));
        Venta venta;
        try {
            venta = ventas.asignarCliente(sesion, clienteId);
        } catch (NegocioException error) {
            if ("SIN_VENTA_EN_CURSO".equals(error.getCodigo())) {
                throw new NegocioException("SIN_VENTA_EN_CURSO", "No hay una venta en curso. Si la factura ya se "
                        + "emitió, el cliente ya no se puede cambiar: hay que anular la factura y volver a vender.");
            }
            throw error;
        }
        String correo = cliente.map(Cliente::correo).orElse(null);
        ventas.asignarCorreoFactura(sesion, correo);
        if (!anterior.map(Cliente::id).equals(cliente.map(Cliente::id))) {
            registrarCambioDeCliente(venta, anterior, cliente, sesion);
        }
        return new ClienteYCorreo(cliente, correo, avisoAlCobrar(sesion));
    }

    /** HU-05.08: el motivo por el que la venta, con su cliente actual, no se podrá cobrar; {@code null} si se puede. */
    private String avisoAlCobrar(SesionActiva sesion) {
        try {
            ventas.actual(sesion).ifPresent(validador::validar);
            return null;
        } catch (NegocioException error) {
            return error.getMessage();
        }
    }

    private void registrarCambioDeCliente(Venta venta, Optional<Cliente> anterior, Optional<Cliente> nuevo,
                                          SesionActiva sesion) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("venta", venta.numero());
        cambios.put("cliente", EventoBitacora.cambio(receptor(anterior), receptor(nuevo)));
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.CLIENTE_VENTA_CAMBIADO, true, "Cliente de la venta N.º %d: %s → %s".formatted(
                        venta.numero(), receptor(anterior), receptor(nuevo)), "venta", venta.id(), cambios));
    }

    private static String receptor(Optional<Cliente> cliente) {
        return cliente.map(c -> c.tipoIdentificacion() + " " + c.nit() + " · " + c.nombre())
                .orElse("Consumidor Final");
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
                        .map(cliente -> new Receptor(Receptor.TipoReceptor.valueOf(cliente.tipoIdentificacion()),
                                cliente.nit(), cliente.nombre()))
                        .orElseThrow(() -> new RecursoNoEncontradoException("CLIENTE_NO_ENCONTRADO",
                                "El cliente no existe."));
        transacciones.executeWithoutResult(transaccion -> {
            reenvio.reabrir(ventaId, receptor, true, sesion);
            ventas.corregirCliente(ventaId, clienteId);
        });
        return reenvio.certificar(ventaId, sesion);
    }

    private Cliente registrar(String tipo, String nit, String nombre, String direccion, String correo,
                              SesionActiva sesion, String como) {
        long id;
        try {
            id = clientes.registrar(tipo, nit, nombre, direccion, correo, sesion.usuarioId());
        } catch (DuplicateKeyException e) {
            // Otra caja lo registró al mismo tiempo: se usa el existente (HU-05.05 crit. 2).
            return (tipo.equals(Cliente.CUI) ? clientes.porCui(nit) : clientes.porNit(nit)).orElseThrow();
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put(tipo.equals(Cliente.CUI) ? "cui" : "nit", EventoBitacora.cambio("", nit));
        datos.put("nombre", EventoBitacora.cambio("", nombre));
        datos.put("direccion", EventoBitacora.cambio("", direccion));
        if (correo != null) {
            datos.put("correo", EventoBitacora.cambio("", correo));
        }
        bitacora.registrar(evento(sesion, true, "Cliente %s %s registrado (%s): %s".formatted(tipo, nit, como, nombre), id,
                datos));
        return clientes.porId(id).orElseThrow();
    }

    /** HU-05.03: el NIT en formato uniforme, solo si su dígito verificador es correcto (crit. 1 a 3). */
    /** HU-05.06 crit. 2: 13 dígitos; se aceptan espacios y guiones al escribirlo (por ejemplo "1234 56789 0101"). */
    private static String cuiValido(String cui) {
        String limpio = cui == null ? "" : cui.replaceAll("[\\s-]", "");
        if (!limpio.matches("\\d{13}")) {
            throw new NegocioException("CUI_INVALIDO", "El CUI debe tener 13 dígitos numéricos.");
        }
        return limpio;
    }

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
