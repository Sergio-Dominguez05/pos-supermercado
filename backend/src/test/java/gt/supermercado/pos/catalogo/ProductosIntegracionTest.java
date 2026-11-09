package gt.supermercado.pos.catalogo;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-03.01 Registrar un producto, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ProductosIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ProductosIntegracionTest {

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
        cookieAdmin = cookie(login("/api/auth/login", "admin", "contrasena", CONTRASENA_ADMIN), "POS_SESION");
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('cat.cajera', 'Cajera del catálogo', 3, ?, false) ON CONFLICT (username) DO NOTHING""",
                codificador.encode(PIN));
    }

    // Crit. 1 y 6: código, descripción y precio; queda activo de inmediato

    @Test
    void seRegistraUnProductoActivoConCodigoDescripcionYPrecio() throws Exception {
        String codigo = nuevoCodigo();

        HttpResponse<String> respuesta = crear(codigo, "Leche entera 1 L", "11.2");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body()).contains("\"codigoBarras\":\"%s\"".formatted(codigo),
                "\"descripcion\":\"Leche entera 1 L\"", "\"precio\":11.20", "\"activo\":true");
        Map<String, Object> fila = jdbc.queryForMap("SELECT descripcion, precio, activo FROM producto WHERE codigo_barras = ?",
                codigo);
        assertThat(fila).containsEntry("descripcion", "Leche entera 1 L").containsEntry("activo", true);
        assertThat(fila.get("precio")).isEqualTo(new BigDecimal("11.20"));
    }

    @Test
    void elProductoRegistradoSePuedeConsultarPorSuId() throws Exception {
        long id = idDe(crear(nuevoCodigo(), "Arroz 1 lb", "6.50"));

        HttpResponse<String> consulta = enviar("/api/productos/" + id, "GET", null, cookieAdmin);

        assertThat(consulta.statusCode()).isEqualTo(200);
        assertThat(consulta.body()).contains("\"descripcion\":\"Arroz 1 lb\"", "\"activo\":true");
    }

    // Crit. 2: el código no se repite

    @Test
    void noSePermiteUnCodigoYaAsignadoYSeDiceAQueProducto() throws Exception {
        String codigo = nuevoCodigo();
        crear(codigo, "Azúcar 5 lb", "22.00");

        HttpResponse<String> repetido = crear(codigo, "Otro producto", "1.00");

        assertThat(repetido.statusCode()).isEqualTo(422);
        assertThat(repetido.body()).contains("CODIGO_DUPLICADO",
                "El código de barras ya está asignado al producto 'Azúcar 5 lb'.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM producto WHERE codigo_barras = ?", Integer.class, codigo))
                .isEqualTo(1);
    }

    @Test
    void laBaseDeDatosTambienImpideCodigosRepetidos() {
        String codigo = nuevoCodigo();
        jdbc.update("INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, 'Directo', 1)", codigo);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, 'Repetido', 1)", codigo))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // Crit. 3: el lector escribe el código con espacios o salto de línea; se guarda limpio

    @Test
    void elCodigoEscaneadoSeGuardaSinEspaciosNiSaltoDeLinea() throws Exception {
        String codigo = nuevoCodigo();

        HttpResponse<String> respuesta = crear(" " + codigo.toLowerCase() + "\\r\\n", "Frijol negro 1 lb", "9.75");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body()).contains("\"codigoBarras\":\"%s\"".formatted(codigo));
    }

    // Crit. 4: precio mayor que cero, máximo 2 decimales

    @Test
    void elPrecioDebeSerMayorQueCeroYTenerComoMaximoDosDecimales() throws Exception {
        assertThat(crear(nuevoCodigo(), "Precio cero", "0").body()).contains("PRECIO_INVALIDO");
        assertThat(crear(nuevoCodigo(), "Precio negativo", "-5.00").body()).contains("PRECIO_INVALIDO");
        assertThat(crear(nuevoCodigo(), "Tres decimales", "5.105").body()).contains("PRECIO_INVALIDO");
        assertThat(crear(nuevoCodigo(), "Sin precio", null).body()).contains("PRECIO_INVALIDO");
    }

    @Test
    void laBaseDeDatosTambienRechazaPreciosNoPositivos() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, 'Gratis', 0)", nuevoCodigo()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Crit. 5: descripción obligatoria, máximo 100 caracteres

    @Test
    void laDescripcionEsObligatoriaYTieneComoMaximo100Caracteres() throws Exception {
        assertThat(crear(nuevoCodigo(), "   ", "1.00").body()).contains("DESCRIPCION_INVALIDA");
        assertThat(crear(nuevoCodigo(), "x".repeat(101), "1.00").body()).contains("DESCRIPCION_INVALIDA");
        assertThat(crear(nuevoCodigo(), "x".repeat(100), "1.00").statusCode()).isEqualTo(201);
    }

    @Test
    void elCodigoEsObligatorio() throws Exception {
        HttpResponse<String> sinCodigo = crear("  ", "Sin código", "1.00");

        assertThat(sinCodigo.statusCode()).isEqualTo(422);
        assertThat(sinCodigo.body()).contains("CODIGO_INVALIDO");
    }

    // Crit. 7: la bitácora registra quién lo hizo

    @Test
    void elRegistroQuedaEnLaBitacoraConElUsuarioQueLoHizo() throws Exception {
        String codigo = nuevoCodigo();
        long id = idDe(crear(codigo, "Aceite 1 L", "28.50"));

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT u.username, b.descripcion, b.exitoso FROM bitacora b JOIN usuario u ON u.id = b.usuario_id
                WHERE b.accion = 'PRODUCTO_CREADO' AND b.entidad = 'producto' AND b.entidad_id = ?""", id);
        assertThat(registro).containsEntry("username", "admin").containsEntry("exitoso", true)
                .containsEntry("descripcion", "Producto 'Aceite 1 L' (%s) creado con precio Q28.50".formatted(codigo));
    }

    // HU-03.02 Editar la descripción y el precio

    @Test
    void seEditanLaDescripcionYElPrecio() throws Exception {   // HU-03.02 crit. 1
        String codigo = nuevoCodigo();
        long id = idDe(crear(codigo, "Café 400 g", "35.00"));
        jdbc.update("UPDATE producto SET modificado_en = now() - interval '1 day' WHERE id = ?", id);

        HttpResponse<String> respuesta = editar(id, "  Café molido   400 g ", "37.5");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"codigoBarras\":\"%s\"".formatted(codigo),
                "\"descripcion\":\"Café molido 400 g\"", "\"precio\":37.50");
        assertThat(jdbc.queryForObject("SELECT modificado_en > now() - interval '1 minute' FROM producto WHERE id = ?",
                Boolean.class, id)).as("se actualiza la fecha de modificación").isTrue();
    }

    @Test
    void elCodigoNoSeModificaAunqueSeEnvie() throws Exception {   // HU-03.02 crit. 2
        String codigo = nuevoCodigo();
        long id = idDe(crear(codigo, "Sal 1 lb", "3.00"));

        HttpResponse<String> respuesta = enviar("/api/productos/" + id, "PUT",
                "{\"codigoBarras\":\"OTRO-CODIGO\",\"descripcion\":\"Sal yodada 1 lb\",\"precio\":3.00}", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT codigo_barras FROM producto WHERE id = ?", String.class, id)).isEqualTo(codigo);
    }

    @Test
    void laBaseDeDatosImpideCambiarElCodigo() throws Exception {   // HU-03.02 crit. 2
        long id = idDe(crear(nuevoCodigo(), "Inmutable", "1.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE producto SET codigo_barras = 'CAMBIADO' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void cadaCambioQuedaEnLaBitacoraConElValorAnteriorYElNuevo() throws Exception {   // HU-03.02 crit. 6
        long id = idDe(crear(nuevoCodigo(), "Harina 1 lb", "5.00"));

        editar(id, "Harina de trigo 1 lb", "5.75");

        Map<String, Object> descripcion = jdbc.queryForMap("""
                SELECT cambios->'descripcion'->>'antes' AS antes, cambios->'descripcion'->>'despues' AS despues
                FROM bitacora WHERE accion = 'PRODUCTO_EDITADO' AND entidad_id = ? AND exitoso""", id);
        Map<String, Object> precio = jdbc.queryForMap("""
                SELECT cambios->'precio'->>'antes' AS antes, cambios->'precio'->>'despues' AS despues
                FROM bitacora WHERE accion = 'PRECIO_CAMBIADO' AND entidad_id = ? AND exitoso""", id);
        assertThat(descripcion).containsEntry("antes", "Harina 1 lb").containsEntry("despues", "Harina de trigo 1 lb");
        assertThat(precio).containsEntry("antes", "5.00").containsEntry("despues", "5.75");
    }

    @Test
    void siNoCambiaNadaNoSeRegistraNada() throws Exception {
        long id = idDe(crear(nuevoCodigo(), "Sin cambios", "2.00"));

        assertThat(editar(id, "Sin cambios", "2").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE entidad = 'producto' AND entidad_id = ?
                  AND accion IN ('PRODUCTO_EDITADO', 'PRECIO_CAMBIADO')""", Integer.class, id)).isZero();
    }

    @Test
    void alEditarSeAplicanLasMismasReglasQueAlRegistrar() throws Exception {
        long id = idDe(crear(nuevoCodigo(), "Reglas", "2.00"));

        assertThat(editar(id, "   ", "2.00").body()).contains("DESCRIPCION_INVALIDA");
        assertThat(editar(id, "x".repeat(101), "2.00").body()).contains("DESCRIPCION_INVALIDA");
        assertThat(editar(id, "Reglas", "0").body()).contains("PRECIO_INVALIDO");
        assertThat(editar(id, "Reglas", "2.005").body()).contains("PRECIO_INVALIDO");
        assertThat(editar(id, "Reglas", null).body()).contains("PRECIO_INVALIDO");
        assertThat(editar(999_999_999, "Nadie", "1.00").statusCode()).isEqualTo(404);
    }

    @Test
    void soloQuienPuedeCambiarPreciosEditaProductos() throws Exception {
        String cajera = cookie(login("/api/auth/login-pin", "cat.cajera", "pin", PIN), "POS_SESION");
        long id = idDe(crear(nuevoCodigo(), "Protegido", "1.00"));

        assertThat(enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"Hackeado\",\"precio\":0.01}", cajera)
                .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT precio FROM producto WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("1.00");
    }

    // Permisos

    @Test
    void soloQuienPuedeCrearProductosLosRegistra() throws Exception {
        String cajera = cookie(login("/api/auth/login-pin", "cat.cajera", "pin", PIN), "POS_SESION");
        long id = idDe(crear(nuevoCodigo(), "Consultable", "1.00"));

        assertThat(enviar("/api/productos", "POST", cuerpo(nuevoCodigo(), "Intruso", "1.00"), cajera).statusCode())
                .isEqualTo(403);
        assertThat(enviar("/api/productos/" + id, "GET", null, cajera).statusCode())
                .as("la cajera sí puede consultar el catálogo").isEqualTo(200);
        assertThat(enviar("/api/productos", "POST", cuerpo(nuevoCodigo(), "Anónimo", "1.00"), null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void unProductoQueNoExisteDa404() throws Exception {
        assertThat(enviar("/api/productos/999999999", "GET", null, cookieAdmin).statusCode()).isEqualTo(404);
    }

    // Apoyo

    private static String nuevoCodigo() {
        return "P" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
    }

    private HttpResponse<String> crear(String codigo, String descripcion, String precio) throws Exception {
        return enviar("/api/productos", "POST", cuerpo(codigo, descripcion, precio), cookieAdmin);
    }

    private HttpResponse<String> editar(long id, String descripcion, String precio) throws Exception {
        return enviar("/api/productos/" + id, "PUT",
                "{\"descripcion\":\"%s\",\"precio\":%s}".formatted(descripcion, precio), cookieAdmin);
    }

    private static String cuerpo(String codigo, String descripcion, String precio) {
        return "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":%s}".formatted(codigo, descripcion, precio);
    }

    private static long idDe(HttpResponse<String> creada) {
        String cuerpo = creada.body();
        int inicio = cuerpo.indexOf("\"id\":") + 5;
        return Long.parseLong(cuerpo.substring(inicio, cuerpo.indexOf(',', inicio)));
    }

    private HttpResponse<String> login(String ruta, String username, String campo, String valor) throws Exception {
        return enviar(ruta, "POST", "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null);
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
