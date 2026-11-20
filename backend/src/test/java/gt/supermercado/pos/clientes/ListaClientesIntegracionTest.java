package gt.supermercado.pos.clientes;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-05.10: consulta paginada de clientes contra PostgreSQL real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ListaClientesIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ListaClientesIntegracionTest {

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
    private String marca;
    private long adminId;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN), null));
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        marca = "lc" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 100_000;
    }

    @Test
    void muestraIdentificacionDatosYLaUltimaCompra() throws Exception {   // crit. 1
        long nit = cliente("NIT", identificacionNit(), "Comercial " + marca, "ventas@cliente.test");
        long cui = cliente("CUI", identificacionCui(), "Ana " + marca, null);
        OffsetDateTime anterior = OffsetDateTime.parse("2026-11-18T14:00:00Z");
        OffsetDateTime reciente = OffsetDateTime.parse("2026-11-19T16:30:00Z");
        compra(nit, anterior);
        compra(nit, reciente);

        JsonNode pagina = listar("q=" + marca);
        JsonNode filaNit = buscar(pagina, nit);
        JsonNode filaCui = buscar(pagina, cui);

        assertThat(filaNit.get("tipoIdentificacion").asString()).isEqualTo("NIT");
        assertThat(filaNit.get("nombre").asString()).isEqualTo("Comercial " + marca);
        assertThat(filaNit.get("direccion").asString()).isEqualTo("Ciudad");
        assertThat(filaNit.get("correo").asString()).isEqualTo("ventas@cliente.test");
        assertThat(OffsetDateTime.parse(filaNit.get("ultimaCompra").asString())).isEqualTo(reciente);
        assertThat(filaCui.get("tipoIdentificacion").asString()).isEqualTo("CUI");
        assertThat(filaCui.get("ultimaCompra").isNull()).isTrue();
    }

    @Test
    void buscaPorNitCuiONombreSinTildesNiMayusculas() throws Exception {   // crit. 2
        String nit = identificacionNit();
        String cui = identificacionCui();
        cliente("NIT", nit, "Azúcar Ñandú " + marca, null);
        cliente("CUI", cui, "Otra persona " + marca, null);

        assertThat(nombres(listar("q=" + enc("azucar nandu " + marca)))).containsExactly("Azúcar Ñandú " + marca);
        assertThat(nombres(listar("q=" + enc(nit.substring(0, nit.length() - 1) + "-" + nit.substring(nit.length() - 1)))))
                .containsExactly("Azúcar Ñandú " + marca);
        assertThat(nombres(listar("q=" + cui))).containsExactly("Otra persona " + marca);
        assertThat(listar("q=" + enc(marca + "%")).get("total").asLong()).isZero();
    }

    @Test
    void paginaLaListaConUnTotalYUnTamanoMaximo() throws Exception {   // crit. 3
        for (int i = 1; i <= 7; i++) {
            cliente("NIT", identificacionNit(), "Cliente %02d %s".formatted(i, marca), null);
        }

        JsonNode primera = listar("q=" + marca + "&tamano=3");
        JsonNode tercera = listar("q=" + marca + "&tamano=3&pagina=2");
        JsonNode fuera = listar("q=" + marca + "&tamano=3&pagina=9");

        assertThat(primera.get("total").asLong()).isEqualTo(7);
        assertThat(nombres(primera)).hasSize(3).first().asString().startsWith("Cliente 01");
        assertThat(nombres(tercera)).containsExactly("Cliente 07 " + marca);
        assertThat(nombres(fuera)).isEmpty();
        assertThat(fuera.get("total").asLong()).isEqualTo(7);
        assertThat(listar("tamano=100000").get("tamano").asInt()).isEqualTo(100);
    }

    @Test
    void elSupervisorPuedeConsultarYSeExigeSesion() throws Exception {
        assertThat(enviar("/api/clientes", "GET", null, sesionSupervisor()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/clientes", "GET", null, null).statusCode()).isEqualTo(401);
    }

    private long cliente(String tipo, String identificacion, String nombre, String correo) {
        return jdbc.queryForObject("""
                INSERT INTO cliente (tipo_id, identificacion, nombre, direccion, correo, registrado_por)
                VALUES (?, ?, ?, 'Ciudad', ?, ?) RETURNING id""", Long.class,
                tipo, identificacion, nombre, correo, adminId);
    }

    private void compra(long clienteId, OffsetDateTime fecha) {
        String caja = "Caja " + marca + " " + SIGUIENTE.incrementAndGet();
        long cajaId = jdbc.queryForObject(
                "INSERT INTO caja (sucursal_id, nombre) VALUES (1, ?) RETURNING id", Long.class, caja);
        long turnoId = jdbc.queryForObject("""
                INSERT INTO turno (caja_id, usuario_id, estado, cerrado_en, cerrado_por,
                                   total_registrado, total_lote, diferencia)
                VALUES (?, ?, 'CERRADO', now(), ?, 1, 1, 0) RETURNING id""", Long.class,
                cajaId, adminId, adminId);
        jdbc.update("""
                INSERT INTO venta (turno_id, caja_id, usuario_id, estado, tasa_iva, subtotal, iva, total,
                                   finalizada_en, cliente_id)
                VALUES (?, ?, ?, 'FINALIZADA', 0.12, 0.89, 0.11, 1.00, ?, ?)""",
                turnoId, cajaId, adminId, fecha, clienteId);
    }

    private JsonNode listar(String consulta) throws Exception {
        HttpResponse<String> respuesta = enviar("/api/clientes?" + consulta, "GET", null, cookieAdmin);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private static JsonNode buscar(JsonNode pagina, long id) {
        for (JsonNode cliente : pagina.get("clientes")) {
            if (cliente.get("id").asLong() == id) return cliente;
        }
        throw new AssertionError("No se encontró el cliente " + id);
    }

    private static List<String> nombres(JsonNode pagina) {
        List<String> nombres = new ArrayList<>();
        pagina.get("clientes").forEach(cliente -> nombres.add(cliente.get("nombre").asString()));
        return nombres;
    }

    private String identificacionNit() {
        return "8%09dK".formatted(SIGUIENTE.incrementAndGet());
    }

    private String identificacionCui() {
        return "3%012d".formatted(SIGUIENTE.incrementAndGet());
    }

    private static String enc(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private String sesionSupervisor() throws Exception {
        String username = "lc.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor de clientes', 2, ?, false)""", username, codificador.encode(CONTRASENA));
        return cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, CONTRASENA), null));
    }

    private static String cookie(HttpResponse<String> respuesta) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("POS_SESION=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviar(String ruta, String metodo, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) peticion.header("Content-Type", "application/json");
        if (cookie != null) peticion.header("Cookie", cookie);
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
