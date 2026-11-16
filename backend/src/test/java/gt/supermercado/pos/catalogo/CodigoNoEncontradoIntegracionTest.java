package gt.supermercado.pos.catalogo;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03.08 Manejar producto no encontrado, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CodigoNoEncontradoIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CodigoNoEncontradoIntegracionTest {

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
        cookieAdmin = cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN), null), "POS_SESION");
    }

    // Crit. 4: el código no encontrado queda registrado con la caja y el usuario

    @Test
    void unCodigoInexistenteQuedaRegistradoConLaCajaYElUsuario() throws Exception {
        String codigo = nuevoCodigo();
        String nombreCaja = "Caja NE" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombreCaja), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = nuevoCajero();
        String sesion = cookie(enviar("/api/auth/login-pin", "POST",
                "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(cajero, PIN), equipo), "POS_SESION");

        HttpResponse<String> respuesta = enviar("/api/productos/codigo/" + codigo.toLowerCase(), "GET", null, sesion);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT n.caja_id, u.username, n.resuelto FROM codigo_no_encontrado n JOIN usuario u ON u.id = n.usuario_id
                WHERE n.codigo = ?""", codigo);
        assertThat(registro).containsEntry("caja_id", cajaId).containsEntry("username", cajero)
                .containsEntry("resuelto", false);
    }

    @Test
    void elAdministradorVeLosCodigosPendientesAgrupados() throws Exception {
        String codigo = nuevoCodigo();
        buscar(codigo);
        buscar(codigo);

        HttpResponse<String> lista = enviar("/api/productos/codigos-no-encontrados", "GET", null, cookieAdmin);

        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).contains("{\"codigo\":\"%s\",\"veces\":2,".formatted(codigo));
    }

    @Test
    void alRegistrarElProductoElCodigoDejaDeEstarPendiente() throws Exception {
        String codigo = nuevoCodigo();
        buscar(codigo);

        enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Ya registrado\",\"precio\":3.00}".formatted(codigo), cookieAdmin);

        assertThat(jdbc.queryForObject("SELECT bool_and(resuelto) FROM codigo_no_encontrado WHERE codigo = ?",
                Boolean.class, codigo)).isTrue();
        assertThat(enviar("/api/productos/codigos-no-encontrados", "GET", null, cookieAdmin).body()).doesNotContain(codigo);
    }

    @Test
    void unCodigoExistenteOConFormatoInvalidoNoSeRegistra() throws Exception {
        String codigo = nuevoCodigo();
        enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Existe\",\"precio\":3.00}".formatted(codigo), cookieAdmin);

        buscar(codigo);
        buscar("CODIGO%20CON%20ESPACIOS%20Y%20%C3%91");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_no_encontrado WHERE codigo = ?", Integer.class, codigo))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_no_encontrado WHERE codigo LIKE '%Ñ%'", Integer.class))
                .isZero();
    }

    @Test
    void soloQuienRegistraProductosVeLosCodigosPendientes() throws Exception {
        String cajero = nuevoCajero();
        String sesion = cookie(enviar("/api/auth/login-pin", "POST",
                "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(cajero, PIN), null), "POS_SESION");

        assertThat(enviar("/api/productos/codigos-no-encontrados", "GET", null, sesion).statusCode()).isEqualTo(403);
    }

    // Apoyo

    private static String nuevoCodigo() {
        return "NE" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
    }

    private void buscar(String codigo) throws Exception {
        enviar("/api/productos/codigo/" + codigo, "GET", null, cookieAdmin);
    }

    private String nuevoCajero() {
        String username = "ne.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero que escanea', 3, ?, false)""", username, codificador.encode(PIN));
        return username;
    }

    private static long idDe(HttpResponse<String> creada) {
        String cuerpo = creada.body();
        int inicio = cuerpo.indexOf("\"id\":") + 5;
        return Long.parseLong(cuerpo.substring(inicio, cuerpo.indexOf(',', inicio)));
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
