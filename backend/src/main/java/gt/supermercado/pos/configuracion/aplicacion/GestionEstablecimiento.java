package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.configuracion.dominio.RepositorioEstablecimientos;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HU-12.02 Configurar el establecimiento registrado en la SAT.
 * <ul>
 *   <li>Crit. 1: código de establecimiento, nombre y dirección. HU-08.02 agrega el código postal, el municipio y
 *       el departamento, que el DTE pide como dirección del emisor.</li>
 *   <li>Crit. 2: la facturación (EP-08) lee los datos con {@link #deLaSucursal(long)} y los copia en cada factura.</li>
 *   <li>Crit. 3: un establecimiento por sucursal. En V1 solo existe la sucursal "Matriz"; EP-13 agrega las demás.</li>
 * </ul>
 * Cada cambio queda en la bitácora con el valor anterior y el nuevo (HU-12.15 crit. 4).
 */
@Service
public class GestionEstablecimiento {

    /** El número de establecimiento que asigna la SAT: 1, 2, 3... (hasta 6 dígitos, sin ceros a la izquierda). */
    private static final Pattern CODIGO_SAT = Pattern.compile("[1-9]\\d{0,5}");
    private static final Pattern CODIGO_POSTAL = Pattern.compile("\\d{5}");

    private final RepositorioEstablecimientos repositorio;
    private final Bitacora bitacora;

    public GestionEstablecimiento(RepositorioEstablecimientos repositorio, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.bitacora = bitacora;
    }

    public record SolicitudEstablecimiento(String codigoSat, String nombre, String direccion, String codigoPostal,
                                           String municipio, String departamento) {}

    /** La sucursal de V1 (EP-13 agrega la elección de sucursal). */
    @Transactional(readOnly = true)
    public long sucursalPrincipal() {
        return repositorio.sucursalPrincipal();
    }

    /** Crit. 2: el establecimiento de la sucursal para sus facturas, o vacío si todavía no se configuró. */
    @Transactional(readOnly = true)
    public Optional<Establecimiento> deLaSucursal(long sucursalId) {
        return repositorio.deLaSucursal(sucursalId);
    }

    @Transactional
    public Establecimiento guardar(long sucursalId, SolicitudEstablecimiento solicitud, SesionActiva sesion) {
        String codigo;
        String nombre;
        String direccion;
        String codigoPostal;
        String municipio;
        String departamento;
        try {
            codigo = codigoSat(solicitud.codigoSat());
            nombre = requerido(solicitud.nombre(), 200, "NOMBRE_ESTABLECIMIENTO_REQUERIDO",
                    "el nombre del establecimiento");
            direccion = requerido(solicitud.direccion(), 300, "DIRECCION_ESTABLECIMIENTO_REQUERIDA",
                    "la dirección del establecimiento");
            codigoPostal = solicitud.codigoPostal() == null ? "" : solicitud.codigoPostal().strip();
            if (!CODIGO_POSTAL.matcher(codigoPostal).matches()) {
                throw new NegocioException("CODIGO_POSTAL_INVALIDO", "El código postal tiene 5 dígitos, por ejemplo 01001.");
            }
            municipio = requerido(solicitud.municipio(), 100, "MUNICIPIO_REQUERIDO", "el municipio");
            departamento = requerido(solicitud.departamento(), 100, "DEPARTAMENTO_REQUERIDO", "el departamento");
        } catch (NegocioException error) {
            throw rechazar(sesion, sucursalId, error);
        }
        Optional<Establecimiento> anterior = repositorio.deLaSucursal(sucursalId);
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambio(cambios, "codigoSat", anterior.map(Establecimiento::codigoSat).orElse(null), codigo);
        cambio(cambios, "nombre", anterior.map(Establecimiento::nombre).orElse(null), nombre);
        cambio(cambios, "direccion", anterior.map(Establecimiento::direccion).orElse(null), direccion);
        cambio(cambios, "codigoPostal", anterior.map(Establecimiento::codigoPostal).orElse(null), codigoPostal);
        cambio(cambios, "municipio", anterior.map(Establecimiento::municipio).orElse(null), municipio);
        cambio(cambios, "departamento", anterior.map(Establecimiento::departamento).orElse(null), departamento);
        if (cambios.isEmpty()) {
            return anterior.orElseThrow();
        }
        try {
            repositorio.guardar(sucursalId, codigo, nombre, direccion, codigoPostal, municipio, departamento,
                    sesion.usuarioId());
        } catch (DuplicateKeyException e) {
            throw rechazar(sesion, sucursalId, new NegocioException("CODIGO_ESTABLECIMIENTO_DUPLICADO",
                    "El código de establecimiento %s ya es de otra sucursal.".formatted(codigo)));
        }
        bitacora.registrar(evento(sesion, sucursalId, true, "Establecimiento SAT %s: %s".formatted(
                anterior.isPresent() ? "modificado" : "configurado", String.join(", ", cambios.keySet())), cambios));
        return repositorio.deLaSucursal(sucursalId).orElseThrow();
    }

    private static String codigoSat(String valor) {
        String limpio = valor == null ? "" : valor.strip();
        if (!CODIGO_SAT.matcher(limpio).matches()) {
            throw new NegocioException("CODIGO_ESTABLECIMIENTO_INVALIDO",
                    "El código de establecimiento es el número que asignó la SAT (1, 2, 3...), sin ceros a la izquierda.");
        }
        return limpio;
    }

    private static String requerido(String valor, int maximo, String codigo, String campo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty() || limpio.length() > maximo) {
            throw new NegocioException(codigo, "Ingrese %s (hasta %d caracteres).".formatted(campo, maximo));
        }
        return limpio;
    }

    private static void cambio(Map<String, Object> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues));
        }
    }

    private NegocioException rechazar(SesionActiva sesion, long sucursalId, NegocioException error) {
        bitacora.registrarIntentoFallido(evento(sesion, sucursalId, false,
                "Cambio del establecimiento SAT rechazado: " + error.getMessage(), null));
        return error;
    }

    private static EventoBitacora evento(SesionActiva sesion, long sucursalId, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "establecimiento", sucursalId, cambios);
    }
}
