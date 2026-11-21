package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.FormatoComprobante;
import gt.supermercado.pos.configuracion.dominio.LogoTicket;
import gt.supermercado.pos.configuracion.dominio.RepositorioFormatoComprobante;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HU-12.09 Personalizar el formato del comprobante: el logo y los mensajes del encabezado y del pie.
 * <ul>
 *   <li>Crit. 1: el logo se sube como imagen y se guarda en blanco y negro ({@link LogoMonocromo}).</li>
 *   <li>Crit. 2: los mensajes, en un solo párrafo cada uno y con los caracteres que imprime la impresora (página de
 *       códigos 850); vacíos no se imprimen.</li>
 *   <li>Crit. 3: {@link #validar} arma el formato sin guardarlo, para la vista previa.</li>
 *   <li>Crit. 4: el formato solo agrega el logo y los mensajes; los datos de la SAT y los totales no son
 *       configurables.</li>
 * </ul>
 * Guardar queda en la bitácora con el valor anterior y el nuevo (HU-11.03).
 */
@Service
public class GestionFormatoComprobante {

    private static final Charset PAGINA_850 = Charset.forName("IBM850");

    private final RepositorioFormatoComprobante repositorio;
    private final Bitacora bitacora;

    public GestionFormatoComprobante(RepositorioFormatoComprobante repositorio, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.bitacora = bitacora;
    }

    /**
     * Lo que pide la pantalla. {@code logo}: una imagen nueva en Base64, o vacío para conservar la actual;
     * {@code quitarLogo} la elimina.
     */
    public record SolicitudFormato(String encabezado, String pie, String logo, Boolean quitarLogo) {}

    /** El formato vigente; si nunca se personalizó, el de siempre. */
    @Transactional(readOnly = true)
    public FormatoComprobante actual() {
        return repositorio.obtener().orElse(FormatoComprobante.POR_OMISION);
    }

    /** Crit. 3: el formato que resultaría de la solicitud, sin guardarlo. 422 si algo no es válido. */
    @Transactional(readOnly = true)
    public FormatoComprobante validar(SolicitudFormato solicitud) {
        String encabezado = mensaje(solicitud.encabezado(), FormatoComprobante.LARGO_MAXIMO_ENCABEZADO, "encabezado");
        String pie = mensaje(solicitud.pie(), FormatoComprobante.LARGO_MAXIMO_PIE, "pie");
        LogoTicket logo;
        if (Boolean.TRUE.equals(solicitud.quitarLogo())) {
            logo = null;
        } else if (solicitud.logo() != null && !solicitud.logo().isBlank()) {
            logo = LogoMonocromo.desdeBase64(solicitud.logo());
        } else {
            logo = actual().logo();
        }
        return new FormatoComprobante(encabezado, pie, logo, null);
    }

    @Transactional
    public FormatoComprobante guardar(SolicitudFormato solicitud, SesionActiva sesion) {
        FormatoComprobante anterior = actual();
        FormatoComprobante nuevo;
        try {
            nuevo = validar(solicitud);
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false,
                    "Cambio del formato del comprobante rechazado: " + error.getMessage(), null));
            throw error;
        }
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambio(cambios, "encabezado", anterior.encabezado(), nuevo.encabezado());
        cambio(cambios, "pie", anterior.pie(), nuevo.pie());
        cambio(cambios, "logo", describir(anterior.logo()), describir(nuevo.logo()));
        if (!Objects.equals(anterior.logo(), nuevo.logo()) && !cambios.containsKey("logo")) {
            // Otra imagen con las mismas medidas: también es un cambio.
            cambios.put("logo", EventoBitacora.cambio(describir(anterior.logo()),
                    describir(nuevo.logo()) + " (reemplazado)"));
        }
        if (cambios.isEmpty()) {
            return anterior;
        }
        repositorio.guardar(nuevo.encabezado(), nuevo.pie(), nuevo.logo(), sesion.usuarioId());
        bitacora.registrar(evento(sesion, true, "Formato del comprobante modificado: " + String.join(", ",
                cambios.keySet()), cambios));
        return actual();
    }

    /** Un mensaje en un solo párrafo, sin espacios de más; vacío es {@code null} (no se imprime). */
    private static String mensaje(String texto, int largoMaximo, String cual) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip().replaceAll("\\s+", " ");
        if (limpio.length() > largoMaximo) {
            throw new NegocioException("MENSAJE_COMPROBANTE_LARGO",
                    "El mensaje del %s admite hasta %d caracteres.".formatted(cual, largoMaximo));
        }
        if (!PAGINA_850.newEncoder().canEncode(limpio)) {
            throw new NegocioException("MENSAJE_COMPROBANTE_INVALIDO",
                    "El mensaje del %s tiene caracteres que la impresora no puede imprimir (por ejemplo, emojis)."
                            .formatted(cual));
        }
        return limpio;
    }

    private static String describir(LogoTicket logo) {
        return logo == null ? "Sin logo" : "Logo de %dx%d puntos".formatted(logo.ancho(), logo.alto());
    }

    private static void cambio(Map<String, Object> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues == null ? "" : despues));
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "formato_comprobante", 1L, cambios);
    }
}
