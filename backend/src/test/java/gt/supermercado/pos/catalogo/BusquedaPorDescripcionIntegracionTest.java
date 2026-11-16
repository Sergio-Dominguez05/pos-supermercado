package gt.supermercado.pos.catalogo;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03.07 Buscar producto por descripción, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + BusquedaPorDescripcionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class BusquedaPorDescripcionIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String cookieAdmin;
    /** Marca única de cada prueba: los productos de otras pruebas comparten la base de datos. */
    private String marca;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN)), "POS_SESION");
        marca = "qw" + SIGUIENTE.incrementAndGet() + "k" + System.nanoTime() % 100_000;
    }

    // Crit. 1: con al menos 3 caracteres, los productos activos cuya descripción contiene el texto

    @Test
    void muestraLosProductosActivosQueContienenElTexto() throws Exception {
        crear(marca + " Jabón de tocador");
        crear(marca + " Detergente con jabón");
        String inactivo = crear(marca + " Jabón descontinuado");
        jdbc.update("UPDATE producto SET activo = false WHERE codigo_barras = ?", inactivo);
        crear(marca + " Shampoo");

        assertThat(descripciones(buscar(marca + " jabón"))).as("el inactivo no aparece")
                .containsExactly(marca + " Jabón de tocador");
        assertThat(descripciones(buscar("con jabón"))).as("el texto puede estar en cualquier parte")
                .contains(marca + " Detergente con jabón");
        assertThat(descripciones(buscar(marca))).containsExactlyInAnyOrder(
                marca + " Jabón de tocador", marca + " Detergente con jabón", marca + " Shampoo");
    }

    @Test
    void conMenosDeTresCaracteresNoBusca() throws Exception {
        HttpResponse<String> respuesta = obtener("/api/productos/busqueda?q=" + enc(" ab "));

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("BUSQUEDA_MUY_CORTA");
        assertThat(obtener("/api/productos/busqueda?q=abc").statusCode()).isEqualTo(200);
    }

    @Test
    void primeroLosQueEmpiezanConElTexto() throws Exception {
        String palabra = "pz" + marca;
        crear("Galleta de " + palabra);
        crear(palabra + " natural");

        assertThat(descripciones(buscar(palabra))).containsExactly(palabra + " natural", "Galleta de " + palabra);
    }

    @Test
    void devuelveComoMaximoVeinteResultados() throws Exception {
        jdbc.update("""
                INSERT INTO producto (codigo_barras, descripcion, precio)
                SELECT 'MAX' || ? || '-' || n, 'Vela ' || ? || ' ' || n, 1 FROM generate_series(1, 30) AS n""",
                marca.toUpperCase(), marca);

        assertThat(buscar("vela " + marca)).hasSize(20);
    }

    // Crit. 2: sin distinguir mayúsculas, minúsculas ni tildes

    @Test
    void noDistingueMayusculasNiTildes() throws Exception {
        crear(marca + " Café Molido Ñandú");

        assertThat(descripciones(buscar((marca + " cafe molido").toUpperCase()))).containsExactly(marca + " Café Molido Ñandú");
        assertThat(descripciones(buscar(marca + " CAFÉ MOLIDO NANDU"))).containsExactly(marca + " Café Molido Ñandú");
    }

    // Crit. 3: los resultados muestran código, descripción y precio

    @Test
    void losResultadosTraenCodigoDescripcionYPrecio() throws Exception {
        String codigo = crear("Arroz blanco " + marca);

        JsonNode fila = buscar("arroz blanco " + marca).get(0);

        assertThat(fila.get("codigoBarras").asString()).isEqualTo(codigo);
        assertThat(fila.get("descripcion").asString()).isEqualTo("Arroz blanco " + marca);
        assertThat(fila.get("precio").decimalValue()).isEqualByComparingTo("4.75");
    }

    @Test
    void losComodinesSeBuscanLiteralmente() throws Exception {
        crear("Cien por ciento " + marca);

        assertThat(buscar("%%% " + marca)).isEmpty();
    }

    // Apoyo

    private String crear(String descripcion) throws Exception {
        String codigo = "D" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> respuesta = enviar("/api/productos",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":4.75}".formatted(codigo, descripcion));
        assertThat(respuesta.statusCode()).isEqualTo(201);
        return codigo;
    }

    private JsonNode buscar(String texto) throws Exception {
        HttpResponse<String> respuesta = obtener("/api/productos/busqueda?q=" + enc(texto));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private static List<String> descripciones(JsonNode resultados) {
        List<String> lista = new ArrayList<>();
        resultados.forEach(fila -> lista.add(fila.get("descripcion").asString()));
        return lista;
    }

    private static String enc(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> obtener(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET()
                .header("Cookie", cookieAdmin).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> enviar(String ruta, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Content-Type", "application/json");
        if (cookieAdmin != null) {
            peticion.header("Cookie", cookieAdmin);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<String> respuesta, String nombre) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(nombre + "=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
