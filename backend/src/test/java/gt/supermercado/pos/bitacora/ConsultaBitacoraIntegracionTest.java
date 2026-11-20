package gt.supermercado.pos.bitacora;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-11.06 Consultar la bitácora, HU-11.07 Filtrar la bitácora y HU-11.08 Ver el detalle de un evento, contra
 * PostgreSQL real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ConsultaBitacoraIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ConsultaBitacoraIntegracionTest {

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
    private long adminId;
    private String hoy;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        hoy = jdbc.queryForObject("SELECT (now() AT TIME ZONE 'America/Guatemala')::date::text", String.class);
        FacturacionDePrueba.configurarEmisorYEstablecimiento(jdbc);
    }

    @Test
    void listaYFiltraLosRegistrosDelMasRecienteConPaginas() throws Exception {   // 11.06 crit. 1, 2 y 4; 11.07
        long producto = idDe(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Arroz 1 lb\",\"precio\":8.00}".formatted(codigo()),
                cookieAdmin));
        cambiarPrecio(producto, "8.50");
        cambiarPrecio(producto, "9.25");
        assertThat(cambiarPrecio(producto, "-1").statusCode()).isEqualTo(422);   // un intento fallido
        String filtros = "desde=" + hoy + "&hasta=" + hoy + "&usuarioId=" + adminId + "&modulo=catalogo"
                + "&accion=PRECIO_CAMBIADO";

        HttpResponse<String> exitosos = enviar("/api/bitacora?" + filtros + "&exitoso=true", "GET", null, cookieAdmin);

        assertThat(exitosos.statusCode()).isEqualTo(200);
        String cuerpo = exitosos.body();
        assertThat(cuerpo).contains("\"usuario\":\"", "(admin)\"", "\"modulo\":\"catalogo\"",
                "\"accion\":\"PRECIO_CAMBIADO\"", "\"exitoso\":true", "\"ocurridoEn\"", "\"tamanoPagina\":50")
                .doesNotContain("\"exitoso\":false");
        assertThat(cuerpo.indexOf("a Q9.25")).as("el más reciente primero").isGreaterThan(0)
                .isLessThan(cuerpo.indexOf("a Q8.50"));
        assertThat(enviar("/api/bitacora?" + filtros + "&exitoso=false", "GET", null, cookieAdmin).body())
                .contains("\"exitoso\":false").doesNotContain("\"exitoso\":true");

        // Crit. 4: cada búsqueda queda registrada con sus filtros; cambiar de página no.
        long consultas = consultasDe(adminId);
        enviar("/api/bitacora?" + filtros, "GET", null, cookieAdmin);
        assertThat(consultasDe(adminId)).isEqualTo(consultas + 1);
        assertThat(jdbc.queryForObject("""
                SELECT cambios -> 'filtros' ->> 'accion' FROM bitacora WHERE accion = 'BITACORA_CONSULTADA'
                  AND usuario_id = ? ORDER BY secuencia DESC LIMIT 1""", String.class, adminId))
                .isEqualTo("PRECIO_CAMBIADO");
        enviar("/api/bitacora?" + filtros + "&pagina=1", "GET", null, cookieAdmin);
        assertThat(consultasDe(adminId)).isEqualTo(consultas + 1);

        HttpResponse<String> opciones = enviar("/api/bitacora/opciones", "GET", null, cookieAdmin);
        assertThat(opciones.body()).contains("\"id\":" + adminId, "\"catalogo\"", "\"PRECIO_CAMBIADO\"",
                "\"BITACORA_CONSULTADA\"");
        HttpResponse<String> invalida = enviar("/api/bitacora?accion=NO_EXISTE", "GET", null, cookieAdmin);
        assertThat(invalida.statusCode()).isEqualTo(422);
        assertThat(invalida.body()).contains("ACCION_INEXISTENTE");
    }

    @Test
    void buscaPorNumeroDeVentaDeFacturaYDeCierre() throws Exception {   // 11.07 crit. 2 y 3
        Cajero cajero = cajeroConTurno();
        long venta = vender(cajero);
        long numeroVenta = jdbc.queryForObject("SELECT numero FROM venta WHERE id = ?", Long.class, venta);
        String factura = jdbc.queryForObject("SELECT serie || '-' || numero FROM factura WHERE venta_id = ?",
                String.class, venta);
        String delDia = "desde=" + hoy + "&hasta=" + hoy;

        assertThat(enviar("/api/bitacora?" + delDia + "&documento=" + numeroVenta + "&accion=VENTA_FINALIZADA", "GET",
                null, cookieAdmin).body()).contains("\"accion\":\"VENTA_FINALIZADA\"", "\"total\":1");
        assertThat(enviar("/api/bitacora?documento=" + cajero.turnoId(), "GET", null, cookieAdmin).body())
                .contains("\"accion\":\"TURNO_ABIERTO\"");
        // La serie y el número de la factura (sin importar mayúsculas) llevan a sus eventos, como una reimpresión.
        long facturaId = jdbc.queryForObject("SELECT id FROM factura WHERE venta_id = ?", Long.class, venta);
        jdbc.update("""
                INSERT INTO bitacora (usuario_id, rol, modulo, accion, exitoso, descripcion, entidad, entidad_id)
                VALUES (?, 'ADMIN', 'facturacion', 'FACTURA_REIMPRESA', true, 'Reimpresión de prueba', 'factura', ?)""",
                adminId, facturaId);
        assertThat(enviar("/api/bitacora?" + delDia + "&documento=" + factura.toLowerCase(), "GET", null, cookieAdmin)
                .body()).contains("\"accion\":\"FACTURA_REIMPRESA\"", "\"total\":1");
        assertThat(enviar("/api/bitacora?documento=999999999&" + delDia, "GET", null, cookieAdmin).body())
                .contains("\"total\":0");
    }

    @Test
    void elDetalleMuestraLosValoresAnteriorYNuevoYElDocumento() throws Exception {   // 11.08 crit. 1 y 2
        long producto = idDe(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Frijol 1 lb\",\"precio\":7.00}".formatted(codigo()),
                cookieAdmin));
        cambiarPrecio(producto, "7.75");
        long registro = jdbc.queryForObject("""
                SELECT id FROM bitacora WHERE accion = 'PRECIO_CAMBIADO' AND entidad = 'producto' AND entidad_id = ?""",
                Long.class, producto);
        long consultas = consultasDe(adminId);

        HttpResponse<String> detalle = enviar("/api/bitacora/" + registro, "GET", null, cookieAdmin);

        assertThat(detalle.statusCode()).isEqualTo(200);
        assertThat(detalle.body()).contains("\"entidad\":\"producto\"", "\"entidadId\":" + producto,
                "\"precio\":{", "\"antes\":", "\"despues\":", "7.75",
                "\"documento\":{\"tipo\":\"PRODUCTO\",\"id\":" + producto + ",\"etiqueta\":\"Frijol 1 lb\"}");
        assertThat(consultasDe(adminId)).isEqualTo(consultas + 1);   // abrir el detalle también se registra

        Cajero cajero = cajeroConTurno();
        long venta = vender(cajero);
        long finalizada = jdbc.queryForObject("""
                SELECT id FROM bitacora WHERE accion = 'VENTA_FINALIZADA' AND entidad = 'venta' AND entidad_id = ?""",
                Long.class, venta);
        long factura = jdbc.queryForObject("SELECT id FROM factura WHERE venta_id = ?", Long.class, venta);
        assertThat(enviar("/api/bitacora/" + finalizada, "GET", null, cookieAdmin).body())
                .contains("\"documento\":{\"tipo\":\"FACTURA\",\"id\":" + factura);

        assertThat(enviar("/api/bitacora/999999999", "GET", null, cookieAdmin).statusCode()).isEqualTo(404);
        assertThat(enviar("/api/bitacora", "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/bitacora/" + registro, "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);
    }

    // Apoyo

    record Cajero(String sesion, long turnoId) {}

    private long consultasDe(long usuarioId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'BITACORA_CONSULTADA' AND exitoso AND usuario_id = ?""",
                Long.class, usuarioId);
    }

    private HttpResponse<String> cambiarPrecio(long producto, String precio) throws Exception {
        String descripcion = jdbc.queryForObject("SELECT descripcion FROM producto WHERE id = ?", String.class,
                producto);
        return enviar("/api/productos/" + producto, "PUT",
                "{\"descripcion\":\"%s\",\"precio\":%s}".formatted(descripcion, precio), cookieAdmin);
    }

    private static String codigo() {
        return "BI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
    }

    private Cajero cajeroConTurno() throws Exception {
        String nombre = "Caja B" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "bi.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de la bitácora', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        HttpResponse<String> turno = enviar("/api/turnos", "POST", null, sesion);
        assertThat(turno.statusCode()).isEqualTo(201);
        return new Cajero(sesion, idDe(turno));
    }

    /** Una venta cobrada y finalizada; devuelve su id. */
    private long vender(Cajero cajero) throws Exception {
        String codigo = codigo();
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Azúcar 1 lb\",\"precio\":6.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo),
                cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        return jdbc.queryForObject("SELECT id FROM venta WHERE turno_id = ? ORDER BY id DESC LIMIT 1", Long.class,
                cajero.turnoId());
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
    }

    private static long idDe(HttpResponse<String> respuesta) {
        String cuerpo = respuesta.body();
        int inicio = cuerpo.indexOf("\"id\":") + 5;
        int fin = inicio;
        while (fin < cuerpo.length() && Character.isDigit(cuerpo.charAt(fin))) {
            fin++;
        }
        return Long.parseLong(cuerpo.substring(inicio, fin));
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
