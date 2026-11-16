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

/** HU-03.04 Consultar la lista de productos, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ListaProductosIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ListaProductosIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String CONTRASENA = "Clave2026segura";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String cookieAdmin;
    /** Marca única de cada prueba: los productos de otras pruebas comparten la base de datos. */
    private String marca;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN), null), "POS_SESION");
        marca = "zq" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 100_000;
    }

    // Crit. 1: código, descripción, precio, estado y fecha de modificación

    @Test
    void laListaMuestraCodigoDescripcionPrecioEstadoYFecha() throws Exception {
        String codigo = crear("Leche " + marca, "11.20");

        JsonNode fila = listar("q=" + marca).get("productos").get(0);

        assertThat(fila.get("codigoBarras").asString()).isEqualTo(codigo);
        assertThat(fila.get("descripcion").asString()).isEqualTo("Leche " + marca);
        assertThat(fila.get("precio").decimalValue()).isEqualByComparingTo("11.20");
        assertThat(fila.get("activo").asBoolean()).isTrue();
        assertThat(fila.get("modificadoEn").asString()).isNotBlank();
    }

    // Crit. 2: buscar por código o descripción

    @Test
    void seBuscaPorDescripcionSinImportarMayusculasNiTildes() throws Exception {
        crear("Azúcar morena " + marca, "8.00");
        crear("Café " + marca, "30.00");

        assertThat(descripciones(listar("q=" + enc("AZUCAR MORENA " + marca)))).containsExactly("Azúcar morena " + marca);
        assertThat(descripciones(listar("q=" + enc("cafe " + marca)))).containsExactly("Café " + marca);
    }

    @Test
    void seBuscaPorCodigo() throws Exception {
        String codigo = crear("Por código " + marca, "1.00");

        assertThat(descripciones(listar("q=" + enc(codigo.toLowerCase())))).containsExactly("Por código " + marca);
    }

    @Test
    void losComodinesDeLaBusquedaSeTomanLiteralmente() throws Exception {
        crear("Cien por ciento " + marca, "1.00");

        assertThat(listar("q=" + enc(marca + "%")).get("total").asLong()).isZero();
    }

    // Crit. 3: filtrar por estado

    @Test
    void seFiltraPorEstado() throws Exception {
        crear("Activo " + marca, "1.00");
        String inactivo = crear("Inactivo " + marca, "1.00");
        long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo_barras = ?", Long.class, inactivo);
        enviar("/api/productos/" + id + "/desactivar", "POST", null, cookieAdmin);

        assertThat(descripciones(listar("q=" + marca + "&activo=true"))).containsExactly("Activo " + marca);
        assertThat(descripciones(listar("q=" + marca + "&activo=false"))).containsExactly("Inactivo " + marca);
        assertThat(listar("q=" + marca).get("total").asLong()).isEqualTo(2);
    }

    // Crit. 4: ordenar por descripción, precio o fecha de modificación

    @Test
    void seOrdenaPorDescripcionPrecioOFecha() throws Exception {
        crear("B " + marca, "30.00");
        crear("a " + marca, "10.00");
        crear("C " + marca, "20.00");
        jdbc.update("UPDATE producto SET modificado_en = now() - interval '1 day' WHERE descripcion = ?", "C " + marca);

        assertThat(descripciones(listar("q=" + marca))).as("por defecto, por descripción sin mayúsculas")
                .containsExactly("a " + marca, "B " + marca, "C " + marca);
        assertThat(descripciones(listar("q=" + marca + "&orden=PRECIO&descendente=true")))
                .containsExactly("B " + marca, "C " + marca, "a " + marca);
        assertThat(descripciones(listar("q=" + marca + "&orden=MODIFICADO")).get(0)).isEqualTo("C " + marca);
    }

    @Test
    void unOrdenDesconocidoSeRechaza() throws Exception {
        assertThat(enviar("/api/productos?orden=codigo_barras;DROP", "GET", null, cookieAdmin).statusCode()).isEqualTo(400);
    }

    // Crit. 5: paginación

    @Test
    void laListaSePaginaConElTotalDeProductos() throws Exception {
        for (int i = 1; i <= 7; i++) {
            crear("Item %02d %s".formatted(i, marca), "1.00");
        }

        JsonNode primera = listar("q=" + marca + "&tamano=3");
        JsonNode tercera = listar("q=" + marca + "&tamano=3&pagina=2");
        JsonNode fuera = listar("q=" + marca + "&tamano=3&pagina=9");

        assertThat(primera.get("total").asLong()).isEqualTo(7);
        assertThat(descripciones(primera)).hasSize(3).first().asString().startsWith("Item 01");
        assertThat(descripciones(tercera)).containsExactly("Item 07 " + marca);
        assertThat(descripciones(fuera)).isEmpty();
        assertThat(fuera.get("total").asLong()).as("permite volver a una página válida").isEqualTo(7);
    }

    @Test
    void elTamanoDePaginaTieneUnMaximo() throws Exception {
        assertThat(listar("tamano=100000").get("tamano").asInt()).isEqualTo(100);
    }

    // Crit. 6: todos los que consultan ven la lista; solo quien tiene permiso crea o edita

    @Test
    void elSupervisorConsultaLaListaPeroNoCreaNiEdita() throws Exception {
        String codigo = crear("Consultable " + marca, "1.00");
        long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo_barras = ?", Long.class, codigo);
        String supervisor = sesionDeSupervisor();

        assertThat(enviar("/api/productos?q=" + marca, "GET", null, supervisor).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"X%s\",\"descripcion\":\"No\",\"precio\":1}".formatted(marca), supervisor).statusCode())
                .isEqualTo(403);
        assertThat(enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"No\",\"precio\":1}", supervisor).statusCode())
                .isEqualTo(403);
        assertThat(enviar("/api/productos", "GET", null, null).statusCode()).isEqualTo(401);
    }

    // Apoyo

    private String crear(String descripcion, String precio) throws Exception {
        String codigo = "L" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> respuesta = enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":%s}".formatted(codigo, descripcion, precio),
                cookieAdmin);
        assertThat(respuesta.statusCode()).isEqualTo(201);
        return codigo;
    }

    private JsonNode listar(String consulta) throws Exception {
        HttpResponse<String> respuesta = enviar("/api/productos?" + consulta, "GET", null, cookieAdmin);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private static List<String> descripciones(JsonNode pagina) {
        List<String> lista = new ArrayList<>();
        pagina.get("productos").forEach(fila -> lista.add(fila.get("descripcion").asString()));
        return lista;
    }

    private static String enc(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private String sesionDeSupervisor() throws Exception {
        String username = "lp.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor de lista', 2, ?, false)""", username, codificador.encode(CONTRASENA));
        return cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, CONTRASENA), null), "POS_SESION");
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
