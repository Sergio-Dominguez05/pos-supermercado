package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.configuracion.dominio.RepositorioImpresoras;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HU-12.08 Configurar las impresoras.
 * <ul>
 *   <li>Crit. 1: la impresora (su dirección en la red) y el ancho de papel, 58 u 80 mm. Cada caja elige la suya en
 *       Configuración › Cajas (HU-12.05 crit. 2).</li>
 *   <li>Crit. 2: la página de prueba la imprime el módulo {@code impresion}.</li>
 *   <li>Crit. 3: las copias de cada factura y de cada cierre de caja, de 1 a 5.</li>
 * </ul>
 * Cada cambio queda en la bitácora como parámetro modificado, con el valor anterior y el nuevo (HU-12.15 crit. 4).
 */
@Service
public class GestionImpresoras {

    /** Una dirección IP o un nombre de equipo de la red, sin protocolo ni puerto. */
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?");
    private static final int COPIAS_MAXIMAS = 5;

    private final RepositorioImpresoras repositorio;
    private final Bitacora bitacora;

    public GestionImpresoras(RepositorioImpresoras repositorio, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.bitacora = bitacora;
    }

    /** Lo que envía el Administrador. Sin puerto se usa el 9100; sin copias, una. */
    public record SolicitudImpresora(String nombre, String host, Integer puerto, Integer anchoMm, Integer copiasFactura,
                                     Integer copiasCierre) {}

    @Transactional(readOnly = true)
    public List<Impresora> listar() {
        return repositorio.listar();
    }

    @Transactional(readOnly = true)
    public Impresora obtener(long id) {
        return repositorio.buscarPorId(id).orElseThrow(GestionImpresoras::noEncontrada);
    }

    /** La impresora de la caja, para imprimir sus tickets (HU-08.05), o vacío si no tiene. */
    @Transactional(readOnly = true)
    public Optional<Impresora> deLaCaja(long cajaId) {
        return repositorio.deLaCaja(cajaId);
    }

    /** {@code true} si existe; lo usan las cajas al asignar su impresora. */
    @Transactional(readOnly = true)
    public boolean existe(long id) {
        return repositorio.buscarPorId(id).isPresent();
    }

    @Transactional
    public Impresora crear(SolicitudImpresora solicitud, SesionActiva sesion) {
        Validada datos = validar(solicitud, sesion, null);
        long id;
        try {
            id = repositorio.crear(datos.nombre(), datos.host(), datos.puerto(), datos.anchoMm(), datos.copiasFactura(),
                    datos.copiasCierre(), sesion.usuarioId());
        } catch (DuplicateKeyException e) {
            throw rechazar(sesion, null, duplicada());
        }
        Map<String, Object> cambios = new LinkedHashMap<>();
        datos.registrarCambios(cambios, null);
        bitacora.registrar(evento(sesion, id, true, "Impresora '%s' agregada".formatted(datos.nombre()), cambios));
        return obtener(id);
    }

    @Transactional
    public Impresora editar(long id, SolicitudImpresora solicitud, SesionActiva sesion) {
        Impresora actual = obtener(id);
        Validada datos = validar(solicitud, sesion, id);
        Map<String, Object> cambios = new LinkedHashMap<>();
        datos.registrarCambios(cambios, actual);
        if (cambios.isEmpty()) {
            return actual;
        }
        try {
            repositorio.actualizar(id, datos.nombre(), datos.host(), datos.puerto(), datos.anchoMm(),
                    datos.copiasFactura(), datos.copiasCierre(), sesion.usuarioId());
        } catch (DuplicateKeyException e) {
            throw rechazar(sesion, id, duplicada());
        }
        bitacora.registrar(evento(sesion, id, true, "Impresora '%s' modificada: %s".formatted(datos.nombre(),
                String.join(", ", cambios.keySet())), cambios));
        return obtener(id);
    }

    private record Validada(String nombre, String host, int puerto, int anchoMm, int copiasFactura, int copiasCierre) {

        void registrarCambios(Map<String, Object> cambios, Impresora antes) {
            cambio(cambios, "nombre", antes == null ? null : antes.nombre(), nombre);
            cambio(cambios, "host", antes == null ? null : antes.host(), host);
            cambio(cambios, "puerto", antes == null ? null : antes.puerto(), puerto);
            cambio(cambios, "anchoMm", antes == null ? null : antes.anchoMm(), anchoMm);
            cambio(cambios, "copiasFactura", antes == null ? null : antes.copiasFactura(), copiasFactura);
            cambio(cambios, "copiasCierre", antes == null ? null : antes.copiasCierre(), copiasCierre);
        }

        private static void cambio(Map<String, Object> cambios, String campo, Object antes, Object despues) {
            if (!Objects.equals(antes, despues)) {
                cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues));
            }
        }
    }

    private Validada validar(SolicitudImpresora solicitud, SesionActiva sesion, Long id) {
        try {
            String nombre = solicitud.nombre() == null ? "" : solicitud.nombre().strip();
            if (nombre.isEmpty() || nombre.length() > 60) {
                throw new NegocioException("NOMBRE_IMPRESORA_REQUERIDO",
                        "Ingrese el nombre de la impresora (hasta 60 caracteres).");
            }
            String host = solicitud.host() == null ? "" : solicitud.host().strip();
            if (!HOST.matcher(host).matches()) {
                throw new NegocioException("HOST_IMPRESORA_INVALIDO",
                        "Ingrese la dirección IP o el nombre de la impresora en la red, por ejemplo 192.168.1.50.");
            }
            int puerto = solicitud.puerto() == null ? Impresora.PUERTO_POR_OMISION : solicitud.puerto();
            if (puerto < 1 || puerto > 65535) {
                throw new NegocioException("PUERTO_IMPRESORA_INVALIDO", "El puerto va de 1 a 65535 (normalmente 9100).");
            }
            Integer ancho = solicitud.anchoMm();
            if (ancho == null || (ancho != 58 && ancho != 80)) {
                throw new NegocioException("ANCHO_PAPEL_INVALIDO", "El ancho del papel es de 58 mm o de 80 mm.");
            }
            return new Validada(nombre, host, puerto, ancho, copias(solicitud.copiasFactura(), "factura"),
                    copias(solicitud.copiasCierre(), "cierre de caja"));
        } catch (NegocioException error) {
            throw rechazar(sesion, id, error);
        }
    }

    private static int copias(Integer valor, String documento) {
        int copias = valor == null ? 1 : valor;
        if (copias < 1 || copias > COPIAS_MAXIMAS) {
            throw new NegocioException("COPIAS_INVALIDAS",
                    "Las copias de %s van de 1 a %d.".formatted(documento, COPIAS_MAXIMAS));
        }
        return copias;
    }

    private static NegocioException duplicada() {
        return new NegocioException("IMPRESORA_DUPLICADA", "Ya existe una impresora con ese nombre.");
    }

    private static RecursoNoEncontradoException noEncontrada() {
        return new RecursoNoEncontradoException("IMPRESORA_NO_ENCONTRADA", "La impresora no existe.");
    }

    private NegocioException rechazar(SesionActiva sesion, Long id, NegocioException error) {
        bitacora.registrarIntentoFallido(evento(sesion, id, false,
                "Cambio de impresora rechazado: " + error.getMessage(), null));
        return error;
    }

    private static EventoBitacora evento(SesionActiva sesion, Long id, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "impresora", id, cambios);
    }
}
