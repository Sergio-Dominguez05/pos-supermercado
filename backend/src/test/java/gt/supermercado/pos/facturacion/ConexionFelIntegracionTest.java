package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-08.01, HU-12.03 y HU-12.15: la conexión con el certificador FEL, contra PostgreSQL real con las migraciones y el
 * certificador simulado. La configuración es una sola fila: cada prueba guarda primero la que necesita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ConexionFelIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ConexionFelIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
    }

    @Test
    void seGuardaConLaLlaveCifradaYNuncaSeDevuelve() throws Exception {   // HU-12.03 crit. 1, HU-12.15 crit. 1 y 2
        String llave = "llave-" + System.nanoTime();

        HttpResponse<String> guardada = guardar(conexion("PRUEBAS", llave, 5000), cookieAdmin);

        assertThat(guardada.statusCode()).isEqualTo(200);
        assertThat(guardada.body()).contains("\"configurada\":true", "\"secretoGuardado\":true",
                "\"ambiente\":\"PRUEBAS\"", "\"timeoutMs\":5000", "\"urlServicio\":\"https://certificador.test/api\"")
                .doesNotContain(llave);
        assertThat(enviar("/api/configuracion/fel", "GET", null, cookieAdmin).body()).doesNotContain(llave);
        byte[] cifrado = jdbc.queryForObject("SELECT secreto_cifrado FROM config_fel", byte[].class);
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(llave);
        // Sin llave nueva se conserva la guardada: la prueba de conexión la sigue usando.
        assertThat(guardar(conexion("PRUEBAS", "", 6000), cookieAdmin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT secreto_cifrado FROM config_fel", byte[].class)).isEqualTo(cifrado);
    }

    @Test
    void soloSeAceptaUnaDireccionHttps() throws Exception {   // HU-08.01 crit. 2
        for (String url : new String[] {"http://certificador.test/api", "certificador.test", "ftp://certificador.test"}) {
            HttpResponse<String> respuesta = guardar(conexion("PRUEBAS", "llave", 5000).replace(
                    "https://certificador.test/api", url), cookieAdmin);
            assertThat(respuesta.statusCode()).as(url).isEqualTo(422);
            assertThat(respuesta.body()).as(url).contains("URL_NO_SEGURA");
        }
    }

    @Test
    void elTiempoLimiteVaDeUnoASesentaSegundos() throws Exception {   // HU-12.03 crit. 4
        assertThat(guardar(conexion("PRUEBAS", "llave", 500), cookieAdmin).body()).contains("TIEMPO_LIMITE_INVALIDO");
        assertThat(guardar(conexion("PRUEBAS", "llave", 60_001), cookieAdmin).body())
                .contains("TIEMPO_LIMITE_INVALIDO");
        assertThat(guardar(conexion("PRUEBAS", "llave", 60_000), cookieAdmin).statusCode()).isEqualTo(200);
    }

    @Test
    void pasarAProduccionExigeConfirmacionExplicita() throws Exception {   // HU-12.03 crit. 3
        guardar(conexion("PRUEBAS", "llave", 5000), cookieAdmin);

        HttpResponse<String> sinConfirmar = guardar(conexion("PRODUCCION", "", 5000), cookieAdmin);
        assertThat(sinConfirmar.statusCode()).isEqualTo(422);
        assertThat(sinConfirmar.body()).contains("CONFIRMAR_PRODUCCION");
        assertThat(jdbc.queryForObject("SELECT ambiente FROM config_fel", String.class)).isEqualTo("PRUEBAS");

        String confirmada = conexion("PRODUCCION", "", 5000).replace("}", ",\"confirmarProduccion\":true}");
        assertThat(guardar(confirmada, cookieAdmin).body()).contains("\"ambiente\":\"PRODUCCION\"");
        // Ya en producción, guardar otro cambio no vuelve a pedir la confirmación.
        assertThat(guardar(conexion("PRODUCCION", "", 7000), cookieAdmin).statusCode()).isEqualTo(200);
        guardar(conexion("PRUEBAS", "", 5000), cookieAdmin);
    }

    @Test
    void sePuedeProbarLaConexionAntesDeGuardarSinEmitirFacturas() throws Exception {   // HU-12.03 crit. 2, HU-08.01 crit. 3
        guardar(conexion("PRUEBAS", "llave", 5000), cookieAdmin);
        Map<String, Object> antes = jdbc.queryForMap("SELECT usuario, modificado_en FROM config_fel");

        HttpResponse<String> prueba = enviar("/api/configuracion/fel/probar", "POST",
                conexion("PRUEBAS", "otra-llave", 5000).replace("\"usuario-fel\"", "\"otro-usuario\""), cookieAdmin);

        assertThat(prueba.statusCode()).isEqualTo(200);
        assertThat(prueba.body()).contains("\"exitosa\":true", "ambiente de pruebas");
        assertThat(jdbc.queryForMap("SELECT usuario, modificado_en FROM config_fel")).isEqualTo(antes);
        assertThat(enviar("/api/configuracion/fel/probar", "POST",
                conexion("PRUEBAS", "llave", 5000).replace("https://", "http://"), cookieAdmin).body())
                .contains("URL_NO_SEGURA");
    }

    @Test
    void soloQuienModificaLaConfiguracionVeOCambiaLaConexion() throws Exception {   // HU-12.15 crit. 3
        String supervisor = sesionDe(usuario(2));
        String cajero = sesionDe(usuario(3));

        for (String sesion : new String[] {supervisor, cajero}) {
            assertThat(enviar("/api/configuracion/fel", "GET", null, sesion).statusCode()).isEqualTo(403);
            assertThat(guardar(conexion("PRUEBAS", "llave", 5000), sesion).statusCode()).isEqualTo(403);
            assertThat(enviar("/api/configuracion/fel/probar", "POST", conexion("PRUEBAS", "llave", 5000), sesion)
                    .statusCode()).isEqualTo(403);
        }
    }

    @Test
    void cadaCambioQuedaEnLaBitacoraSinLaLlave() throws Exception {   // HU-12.15 crit. 4
        guardar(conexion("PRUEBAS", "llave", 5000), cookieAdmin);
        String llave = "llave-auditada-" + System.nanoTime();

        guardar(conexion("PRUEBAS", llave, 9000), cookieAdmin);

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT modulo, entidad, descripcion, cambios::text AS cambios FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND exitoso ORDER BY secuencia DESC LIMIT 1""");
        assertThat(registro).containsEntry("modulo", "configuracion").containsEntry("entidad", "config_fel");
        assertThat((String) registro.get("cambios")).contains("\"timeoutMs\"", "\"antes\": 5000", "\"despues\": 9000",
                "\"secreto\": \"modificado\"").doesNotContain(llave);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE cambios::text LIKE ?", Integer.class,
                "%" + llave + "%")).isZero();
    }

    @Test
    void elNitDelCertificadorEsOpcionalYSeValida() throws Exception {   // HU-08.05 crit. 1: va en la factura impresa
        String conNit = conexion("PRUEBAS", "llave", 5000).replace("}", ",\"nitCertificador\":\"%s\"}");

        assertThat(guardar(conNit.formatted("576937-0"), cookieAdmin).body()).contains("NIT_CERTIFICADOR_INVALIDO");
        assertThat(guardar(conNit.formatted(" 576937-k "), cookieAdmin).body())
                .contains("\"nitCertificador\":\"576937K\"");
        assertThat(guardar(conexion("PRUEBAS", "", 5000), cookieAdmin).body()).contains("\"nitCertificador\":null");
    }

    // Apoyo

    private static String conexion(String ambiente, String llave, int timeoutMs) {
        return """
                {"certificador":"Certificador de prueba","urlServicio":"https://certificador.test/api",\
                "usuario":"usuario-fel","secreto":"%s","ambiente":"%s","timeoutMs":%d}""".formatted(llave, ambiente,
                timeoutMs);
    }

    private HttpResponse<String> guardar(String cuerpo, String sesion) throws Exception {
        return enviar("/api/configuracion/fel", "PUT", cuerpo, sesion);
    }

    private String usuario(int rolId) {
        String username = "fel.u" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Usuario FEL', ?, ?, false)""", username, rolId, codificador.encode(
                rolId == 3 ? PIN : "Clave2026segura"));
        return username;
    }

    /** Supervisor con contraseña; Cajero con PIN en una caja vinculada. */
    private String sesionDe(String username) throws Exception {
        int rol = jdbc.queryForObject("SELECT rol_id FROM usuario WHERE username = ?", Integer.class, username);
        if (rol != 3) {
            return cookie(enviar("/api/auth/login", "POST", login(username, "contrasena", "Clave2026segura"), null),
                    "POS_SESION");
        }
        String nombre = "Caja F" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        String creada = enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin).body();
        int inicio = creada.indexOf("\"id\":") + 5;
        long caja = Long.parseLong(creada.substring(inicio, creada.indexOf(',', inicio)));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        return cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
    }

    private static String cookie(HttpResponse<String> respuesta, String nombre) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(nombre + "=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviar(String ruta, String metodo, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
