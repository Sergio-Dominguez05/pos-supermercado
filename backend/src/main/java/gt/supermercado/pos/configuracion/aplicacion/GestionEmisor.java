package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.Nit;
import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.RepositorioEmisor;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HU-12.01 Configurar los datos del emisor.
 * <ul>
 *   <li>Crit. 1: NIT, razón social, nombre comercial, dirección fiscal, teléfono y correo (los dos últimos
 *       opcionales). HU-08.02 agrega la afiliación al IVA, que pide el DTE.</li>
 *   <li>Crit. 2: el NIT se valida con el algoritmo de la SAT ({@link Nit}, HU-05.03) y se guarda normalizado.</li>
 *   <li>Crit. 3: la facturación (EP-08) lee los datos con {@link #emisor()}.</li>
 *   <li>Crit. 4: las facturas guardan una copia del emisor al emitirse (HU-08.02), así que un cambio no las altera.</li>
 * </ul>
 * Cada cambio queda en la bitácora con el valor anterior y el nuevo (HU-12.15 crit. 4).
 */
@Service
public class GestionEmisor {

    private static final Pattern TELEFONO = Pattern.compile("[0-9+ -]{8,20}");
    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final RepositorioEmisor repositorio;
    private final Bitacora bitacora;

    public GestionEmisor(RepositorioEmisor repositorio, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.bitacora = bitacora;
    }

    /** Lo que registra el Administrador. */
    public record SolicitudEmisor(String nit, String razonSocial, String nombreComercial, String direccionFiscal,
                                  String telefono, String correo, AfiliacionIva afiliacionIva) {}

    /** HU-12.01 crit. 3: los datos del emisor para las facturas, o vacío si todavía no se configuraron. */
    @Transactional(readOnly = true)
    public Optional<Emisor> emisor() {
        return repositorio.emisor();
    }

    @Transactional
    public Emisor guardar(SolicitudEmisor solicitud, SesionActiva sesion) {
        Emisor nuevo;
        try {
            nuevo = validar(solicitud);
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false,
                    "Cambio de los datos del emisor rechazado: " + error.getMessage(), null));
            throw error;
        }
        Optional<Emisor> anterior = repositorio.emisor();
        Map<String, Object> cambios = cambios(anterior.orElse(null), nuevo);
        if (cambios.isEmpty()) {
            return anterior.orElseThrow();
        }
        repositorio.guardar(nuevo, sesion.usuarioId());
        bitacora.registrar(evento(sesion, true, "Datos del emisor %s: %s".formatted(
                anterior.isPresent() ? "modificados" : "configurados", String.join(", ", cambios.keySet())), cambios));
        return repositorio.emisor().orElseThrow();
    }

    private static Emisor validar(SolicitudEmisor solicitud) {
        String nit = Nit.validar(solicitud.nit()).orElseThrow(() -> new NegocioException("NIT_INVALIDO",
                "El NIT no es válido: revise los dígitos y el verificador (puede terminar en K)."));
        String razonSocial = requerido(solicitud.razonSocial(), 200, "RAZON_SOCIAL_REQUERIDA", "la razón social");
        String nombreComercial = requerido(solicitud.nombreComercial(), 200, "NOMBRE_COMERCIAL_REQUERIDO",
                "el nombre comercial");
        String direccion = requerido(solicitud.direccionFiscal(), 300, "DIRECCION_FISCAL_REQUERIDA",
                "la dirección fiscal");
        String telefono = opcional(solicitud.telefono());
        if (telefono != null && !TELEFONO.matcher(telefono).matches()) {
            throw new NegocioException("TELEFONO_INVALIDO", "El teléfono debe tener de 8 a 20 dígitos.");
        }
        String correo = opcional(solicitud.correo());
        if (correo != null && (correo.length() > 254 || !CORREO.matcher(correo).matches())) {
            throw new NegocioException("CORREO_INVALIDO", "El correo no es válido, por ejemplo ventas@empresa.com.");
        }
        if (solicitud.afiliacionIva() == null) {
            throw new NegocioException("AFILIACION_IVA_REQUERIDA",
                    "Indique la afiliación al IVA: régimen General o Pequeño Contribuyente.");
        }
        return new Emisor(nit, razonSocial, nombreComercial, direccion, telefono, correo, solicitud.afiliacionIva(),
                null);
    }

    private static String requerido(String valor, int maximo, String codigo, String campo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty() || limpio.length() > maximo) {
            throw new NegocioException(codigo, "Ingrese %s (hasta %d caracteres).".formatted(campo, maximo));
        }
        return limpio;
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static Map<String, Object> cambios(Emisor antes, Emisor despues) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambio(cambios, "nit", antes == null ? null : antes.nit(), despues.nit());
        cambio(cambios, "razonSocial", antes == null ? null : antes.razonSocial(), despues.razonSocial());
        cambio(cambios, "nombreComercial", antes == null ? null : antes.nombreComercial(), despues.nombreComercial());
        cambio(cambios, "direccionFiscal", antes == null ? null : antes.direccionFiscal(), despues.direccionFiscal());
        cambio(cambios, "telefono", antes == null ? null : antes.telefono(), despues.telefono());
        cambio(cambios, "correo", antes == null ? null : antes.correo(), despues.correo());
        cambio(cambios, "afiliacionIva", antes == null || antes.afiliacionIva() == null ? null
                : antes.afiliacionIva().name(), despues.afiliacionIva().name());
        return cambios;
    }

    private static void cambio(Map<String, Object> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues == null ? "" : despues));
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "emisor", 1L, cambios);
    }
}
