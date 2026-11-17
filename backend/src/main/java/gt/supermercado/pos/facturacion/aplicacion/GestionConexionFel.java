package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.CifradorSecretos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConexionFel;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.RepositorioConexionFel;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * HU-08.01, HU-12.03 y HU-12.15: la conexión con el certificador FEL que configura el Administrador.
 * <ul>
 *   <li>HU-12.03 crit. 1 y 4: certificador, dirección del servicio, usuario, llave o token, ambiente y tiempo límite
 *       antes de pasar a contingencia.</li>
 *   <li>HU-08.01 crit. 2: solo se acepta una dirección {@code https}.</li>
 *   <li>HU-12.03 crit. 2 y HU-08.01 crit. 3: se puede probar la conexión antes de guardar, sin emitir facturas.</li>
 *   <li>HU-12.03 crit. 3: pasar de pruebas a producción exige una confirmación explícita.</li>
 *   <li>HU-12.15 crit. 1 y 2: la llave se guarda cifrada y nunca se devuelve; crit. 4: cada cambio queda en la
 *       bitácora, sin la llave.</li>
 * </ul>
 * Solo el Administrador tiene {@code CONFIGURACION_MODIFICAR} (HU-12.15 crit. 3); los endpoints lo exigen.
 */
@Service
public class GestionConexionFel {

    static final int TIMEOUT_MINIMO_MS = 1_000;
    static final int TIMEOUT_MAXIMO_MS = 60_000;
    static final int TIMEOUT_POR_DEFECTO_MS = 8_000;

    private final RepositorioConexionFel repositorio;
    private final CifradorSecretos cifrador;
    private final CertificadorFel certificador;
    private final Bitacora bitacora;

    public GestionConexionFel(RepositorioConexionFel repositorio, CifradorSecretos cifrador,
                              CertificadorFel certificador, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
        this.certificador = certificador;
        this.bitacora = bitacora;
    }

    /**
     * Lo que pide el Administrador. {@code secreto} vacío conserva el guardado; {@code timeoutMs} vacío usa 8 s.
     * {@code confirmarProduccion} es la confirmación explícita para pasar a producción (HU-12.03 crit. 3).
     */
    public record SolicitudConexionFel(String certificador, String urlServicio, String usuario, String secreto,
                                       AmbienteFel ambiente, Integer timeoutMs, Boolean confirmarProduccion) {

        @Override
        public String toString() {
            return "SolicitudConexionFel[certificador=%s, urlServicio=%s, usuario=%s, ambiente=%s, timeoutMs=%s]"
                    .formatted(certificador, urlServicio, usuario, ambiente, timeoutMs);
        }
    }

    /** La configuración guardada, sin la llave, o vacío si todavía no se configuró. */
    @Transactional(readOnly = true)
    public Optional<ConfiguracionFel> configuracion() {
        return repositorio.configuracion();
    }

    /**
     * La conexión guardada con la llave descifrada, para el adaptador del certificador. Vacío si no se configuró.
     */
    @Transactional(readOnly = true)
    public Optional<ConexionFel> conexionVigente() {
        return repositorio.configuracion().map(guardada -> new ConexionFel(guardada.certificador(),
                guardada.urlServicio(), guardada.usuario(), cifrador.descifrar(guardada.secretoCifrado()),
                guardada.ambiente(), guardada.timeoutMs()));
    }

    /** HU-12.03 crit. 2: prueba los datos del formulario, guardados o no, sin emitir facturas ni guardar nada. */
    @Transactional(readOnly = true)
    public ResultadoPruebaConexion probar(SolicitudConexionFel solicitud) {
        ConexionFel conexion = validar(solicitud, repositorio.configuracion());
        try {
            return certificador.probarConexion(conexion);
        } catch (CertificadorNoDisponibleException error) {
            return ResultadoPruebaConexion.fallida("No hubo respuesta del certificador: " + error.getMessage());
        }
    }

    /** Guarda la conexión con la llave cifrada y registra el cambio en la bitácora (HU-12.15 crit. 4). */
    @Transactional
    public ConfiguracionFel guardar(SolicitudConexionFel solicitud, SesionActiva sesion) {
        Optional<ConfiguracionFel> anterior = repositorio.configuracion();
        ConexionFel conexion;
        try {
            conexion = validar(solicitud, anterior);
            boolean pasaAProduccion = conexion.ambiente() == AmbienteFel.PRODUCCION
                    && anterior.map(ConfiguracionFel::ambiente).orElse(AmbienteFel.PRUEBAS) != AmbienteFel.PRODUCCION;
            if (pasaAProduccion && !Boolean.TRUE.equals(solicitud.confirmarProduccion())) {
                throw new NegocioException("CONFIRMAR_PRODUCCION",
                        "Pasar al ambiente de producción emite facturas reales: confírmelo explícitamente.");
            }
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false,
                    "Cambio de la conexión FEL rechazado: " + error.getMessage(), null));
            throw error;
        }
        boolean secretoNuevo = !vacio(solicitud.secreto());
        byte[] secretoCifrado = secretoNuevo
                ? cifrador.cifrar(conexion.secreto())
                : anterior.orElseThrow().secretoCifrado();
        repositorio.guardar(conexion.certificador(), conexion.urlServicio(), conexion.usuario(), secretoCifrado,
                conexion.ambiente(), conexion.timeoutMs(), sesion.usuarioId());
        Map<String, Object> cambios = cambios(anterior.orElse(null), conexion, secretoNuevo);
        bitacora.registrar(evento(sesion, true, "Conexión FEL %s: %s".formatted(
                anterior.isPresent() ? "modificada" : "configurada", String.join(", ", cambios.keySet())), cambios));
        return repositorio.configuracion().orElseThrow();
    }

    private ConexionFel validar(SolicitudConexionFel solicitud, Optional<ConfiguracionFel> guardada) {
        String nombre = texto(solicitud.certificador());
        if (nombre.isEmpty() || nombre.length() > 60) {
            throw new NegocioException("CERTIFICADOR_REQUERIDO", "Indique el certificador (hasta 60 caracteres).");
        }
        String url = urlSegura(texto(solicitud.urlServicio()));
        String usuario = texto(solicitud.usuario());
        if (usuario.isEmpty() || usuario.length() > 100) {
            throw new NegocioException("USUARIO_FEL_REQUERIDO",
                    "Indique el usuario que le asignó el certificador (hasta 100 caracteres).");
        }
        if (solicitud.ambiente() == null) {
            throw new NegocioException("AMBIENTE_REQUERIDO", "Indique si es el ambiente de pruebas o de producción.");
        }
        int timeout = solicitud.timeoutMs() == null ? TIMEOUT_POR_DEFECTO_MS : solicitud.timeoutMs();
        if (timeout < TIMEOUT_MINIMO_MS || timeout > TIMEOUT_MAXIMO_MS) {
            throw new NegocioException("TIEMPO_LIMITE_INVALIDO",
                    "El tiempo límite debe estar entre 1 y 60 segundos.");
        }
        String secreto = vacio(solicitud.secreto())
                ? guardada.map(g -> cifrador.descifrar(g.secretoCifrado())).orElseThrow(() -> new NegocioException(
                        "SECRETO_REQUERIDO", "Ingrese la llave o token que le entregó el certificador."))
                : solicitud.secreto().strip();
        return new ConexionFel(nombre, url, usuario, secreto, solicitud.ambiente(), timeout);
    }

    /** HU-08.01 crit. 2: la comunicación con el certificador va siempre por un canal cifrado. */
    private static String urlSegura(String url) {
        try {
            URI uri = new URI(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()) {
                return url;
            }
        } catch (URISyntaxException ignorada) {
            // Se responde igual que con cualquier dirección no segura.
        }
        throw new NegocioException("URL_NO_SEGURA",
                "La dirección del servicio debe ser https:// (canal cifrado), por ejemplo https://certificador.com/api.");
    }

    /** Los campos que cambiaron, con su valor anterior y el nuevo. De la llave solo se dice si cambió. */
    private static Map<String, Object> cambios(ConfiguracionFel antes, ConexionFel despues, boolean secretoNuevo) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambio(cambios, "certificador", antes == null ? null : antes.certificador(), despues.certificador());
        cambio(cambios, "urlServicio", antes == null ? null : antes.urlServicio(), despues.urlServicio());
        cambio(cambios, "usuario", antes == null ? null : antes.usuario(), despues.usuario());
        cambio(cambios, "ambiente", antes == null ? null : antes.ambiente().name(), despues.ambiente().name());
        cambio(cambios, "timeoutMs", antes == null ? null : antes.timeoutMs(), despues.timeoutMs());
        if (secretoNuevo) {
            cambios.put("secreto", "cambiado");
        }
        return cambios;
    }

    private static void cambio(Map<String, Object> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues));
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "config_fel", 1L, cambios);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
