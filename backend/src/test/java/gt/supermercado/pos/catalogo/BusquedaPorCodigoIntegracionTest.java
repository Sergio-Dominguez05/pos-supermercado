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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03.06 Buscar producto por código, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + BusquedaPorCodigoIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class BusquedaPorCodigoIntegracionTest {

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
        cookieAdmin = cookie(enviar("/api/auth/login",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN), null), "POS_SESION");
    }

    // Crit. 1: el producto se encuentra de inmediato, aunque el catálogo sea grande

    @Test
    void encuentraElProductoEnMenosDeUnSegundoConMilesDeProductos() throws Exception {
        String prefijo = "MIL" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000 + "-";
        jdbc.update("""
                INSERT INTO producto (codigo_barras, descripcion, precio)
                SELECT ? || n, 'Producto de carga ' || n, 1 + n % 100 FROM generate_series(1, 5000) AS n""", prefijo);
        buscar(prefijo + "1");   // primera petición: calienta la conexión

        long inicio = System.nanoTime();
        HttpResponse<String> respuesta = buscar(prefijo + "4321");
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"descripcion\":\"Producto de carga 4321\"");
        assertThat(duracion).isLessThan(Duration.ofSeconds(1));
    }

    // Crit. 2 y 3: lo que envía el lector o lo que escribe el cajero. El Enter del lector lo procesa la pantalla
    // (dispara la búsqueda) y no llega al servidor.

    @Test
    void elCodigoEscritoAManoSeEncuentraAunqueTengaEspaciosOMinusculas() throws Exception {
        String codigo = crear("Atún en agua");

        assertThat(buscar(codigo).body()).as("como lo envía el lector").contains("Atún en agua");
        assertThat(buscar("  " + codigo.toLowerCase() + " ").body()).as("escrito a mano").contains("Atún en agua");
    }

    // Crit. 4: búsqueda exacta

    @Test
    void laBusquedaEsExacta() throws Exception {
        String codigo = crear("Exacto");

        assertThat(buscar(codigo).statusCode()).isEqualTo(200);
        assertThat(buscar(codigo.substring(0, codigo.length() - 1)).statusCode()).as("un código parcial").isEqualTo(404);
        assertThat(buscar(codigo + "9").statusCode()).as("un código más largo").isEqualTo(404);
    }

    @Test
    void unCodigoInexistenteDa404ConElCodigoEnElMensaje() throws Exception {
        HttpResponse<String> respuesta = buscar("NO-EXISTE-123");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).contains("PRODUCTO_NO_ENCONTRADO", "No hay ningún producto con el código NO-EXISTE-123.");
    }

    @Test
    void devuelveTambienLosInactivosConSuEstado() throws Exception {
        String codigo = crear("Inactivo por código");
        jdbc.update("UPDATE producto SET activo = false WHERE codigo_barras = ?", codigo);

        assertThat(buscar(codigo).body()).contains("\"activo\":false");
    }

    @Test
    void elCajeroPuedeBuscarPorCodigo() throws Exception {
        String codigo = crear("Para el cajero");
        String cajero = "bc.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero buscador', 3, ?, false)""", cajero, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(cajero, PIN),
                null), "POS_SESION");

        assertThat(obtener("/api/productos/codigo/" + codigo, sesion).statusCode()).isEqualTo(200);
        assertThat(obtener("/api/productos/codigo/" + codigo, null).statusCode()).isEqualTo(401);
    }

    // Apoyo

    private String crear(String descripcion) throws Exception {
        String codigo = "C" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> respuesta = enviar("/api/productos",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":2.50}".formatted(codigo, descripcion),
                cookieAdmin);
        assertThat(respuesta.statusCode()).isEqualTo(201);
        return codigo;
    }

    private HttpResponse<String> buscar(String codigo) throws Exception {
        return obtener("/api/productos/codigo/" + URLEncoder.encode(codigo, StandardCharsets.UTF_8).replace("+", "%20"),
                cookieAdmin);
    }

    private HttpResponse<String> obtener(String ruta, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> enviar(String ruta, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Content-Type", "application/json");
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<String> respuesta, String nombre) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(nombre + "=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
