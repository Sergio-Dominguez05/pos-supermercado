package gt.supermercado.pos.impresion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado;
import org.junit.jupiter.api.AfterEach;
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
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-08.12 Reimprimir una factura, contra PostgreSQL real, el certificador y la impresora simulados. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ReimpresionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ReimpresionIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";
    static final String CONTRASENA = "Supervisor2026";
    private static final Charset PAGINA_850 = Charset.forName("IBM850");

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    CanalImpresionSimulado impresoraSimulada;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
        FacturacionDePrueba.configurarEmisorYEstablecimiento(jdbc);
    }

    @AfterEach
    void impresoraQueResponde() {
        impresoraSimulada.fallar(false);
    }

    // Crit. 1, 3 y 4

    @Test
    void conF9SeReimprimeLaUltimaFacturaDeLaCajaComoCopia() throws Exception {
        Caja caja = cajaConImpresora();
        Cajero cajero = cajeroEn(caja);
        vender(cajero);
        long ultima = vender(cajero);
        long ventaUltima = jdbc.queryForObject("SELECT numero FROM venta WHERE id = (SELECT venta_id FROM factura "
                + "WHERE id = ?)", Long.class, ultima);
        int enviosAntes = impresoraSimulada.envios().size();

        HttpResponse<String> respuesta = enviar("/api/facturas/ultima/reimprimir", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"impresa\":true", "de la venta N.º " + ventaUltima);
        assertThat(impresoraSimulada.envios()).hasSize(enviosAntes + 1);
        String ticket = new String(impresoraSimulada.ultimoA(caja.host()).datos(), PAGINA_850);
        assertThat(ticket.split("COPIA - REIMPRESIÓN", -1)).hasSize(3);
        assertThat(ticket.split("FACTURA", -1)).hasSize(2);   // una sola copia, aunque la impresora tenga dos
        assertThat(jdbc.queryForMap("SELECT usuario_id, caja_id FROM reimpresion WHERE factura_id = ?", ultima))
                .containsEntry("usuario_id", cajero.usuarioId()).containsEntry("caja_id", caja.id());
        assertThat(jdbc.queryForMap("""
                SELECT exitoso, caja_id FROM bitacora WHERE accion = 'FACTURA_REIMPRESA' AND entidad_id = ?""", ultima))
                .containsEntry("exitoso", true).containsEntry("caja_id", caja.id());
    }

    @Test
    void unaCajaSinFacturasNoTieneQueReimprimir() throws Exception {
        Cajero cajero = cajeroEn(cajaConImpresora());

        HttpResponse<String> respuesta = enviar("/api/facturas/ultima/reimprimir", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("SIN_FACTURAS");
    }

    // Crit. 2

    @Test
    void elCajeroBuscaYReimprimeSoloLasDeSuTurno() throws Exception {
        Caja caja = cajaConImpresora();
        Cajero cajero = cajeroEn(caja);
        Cajero otro = cajeroEn(cajaConImpresora());
        long suya = vender(cajero);
        long ajena = vender(otro);

        String lista = enviar("/api/facturas/reimprimibles", "GET", null, cajero.sesion()).body();
        assertThat(lista).contains("\"facturaId\":" + suya + ",").doesNotContain("\"facturaId\":" + ajena + ",");
        assertThat(enviar("/api/facturas/" + suya + "/reimprimir", "POST", null, cajero.sesion()).body())
                .contains("\"impresa\":true");

        HttpResponse<String> deOtro = enviar("/api/facturas/" + ajena + "/reimprimir", "POST", null, cajero.sesion());
        assertThat(deOtro.statusCode()).isEqualTo(422);
        assertThat(deOtro.body()).contains("FACTURA_DE_OTRO_TURNO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'FACTURA_REIMPRESA' AND NOT exitoso AND usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isEqualTo(1);
    }

    @Test
    void elSupervisorReimprimeFacturasDeCualquierTurnoEnSuCaja() throws Exception {
        Caja caja = cajaConImpresora();
        long ajena = vender(cajeroEn(cajaConImpresora()));
        String supervisor = supervisorEn(caja);

        assertThat(enviar("/api/facturas/reimprimibles", "GET", null, supervisor).body())
                .contains("\"facturaId\":" + ajena + ",");
        assertThat(enviar("/api/facturas/" + ajena + "/reimprimir", "POST", null, supervisor).body())
                .contains("\"impresa\":true");
        assertThat(new String(impresoraSimulada.ultimoA(caja.host()).datos(), PAGINA_850))
                .contains("COPIA - REIMPRESIÓN");
    }

    @Test
    void noSeReimprimeUnaAnuladaNiSinImpresoraNiConLaImpresoraCaida() throws Exception {
        Caja caja = cajaConImpresora();
        Cajero cajero = cajeroEn(caja);
        long anulada = vender(cajero);
        assertThat(enviar("/api/facturas/" + anulada + "/anular", "POST", "{\"motivo\":\"Error de prueba\"}",
                cookieAdmin).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/facturas/" + anulada + "/reimprimir", "POST", null, cajero.sesion()).body())
                .contains("FACTURA_NO_REIMPRIMIBLE", "anulada");

        long vigente = vender(cajero);
        impresoraSimulada.fallar(true);
        assertThat(enviar("/api/facturas/" + vigente + "/reimprimir", "POST", null, cajero.sesion()).body())
                .contains("IMPRESORA_NO_DISPONIBLE", "no respondió");
        impresoraSimulada.fallar(false);

        Cajero sinImpresora = cajeroEn(new Caja(idDe(enviar("/api/cajas", "POST",
                "{\"nombre\":\"Caja SI%d-%d\"}".formatted(SIGUIENTE.incrementAndGet(), System.nanoTime() % 100_000),
                cookieAdmin)), null));
        vender(sinImpresora);
        assertThat(enviar("/api/facturas/ultima/reimprimir", "POST", null, sinImpresora.sesion()).body())
                .contains("CAJA_SIN_IMPRESORA");
    }

    // Apoyo

    record Caja(long id, String host) {}

    record Cajero(long usuarioId, String sesion) {}

    private Caja cajaConImpresora() throws Exception {
        String host = "reimpresora-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000 + ".local";
        long impresora = idDe(enviar("/api/configuracion/impresoras", "POST",
                "{\"nombre\":\"Térmica %s\",\"host\":\"%s\",\"anchoMm\":80,\"copiasFactura\":2}".formatted(host, host),
                cookieAdmin));
        String nombre = "Caja RI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST",
                "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresora), cookieAdmin));
        return new Caja(caja, host);
    }

    private String equipoDe(Caja caja) throws Exception {
        return cookie(enviar("/api/cajas/" + caja.id() + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
    }

    /** Un cajero con turno abierto en la caja. */
    private Cajero cajeroEn(Caja caja) throws Exception {
        String username = "ri.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de reimpresión', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipoDe(caja)),
                "POS_SESION");
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, sesion);
    }

    /** Un supervisor que inicia sesión en el equipo de la caja, sin turno propio. */
    private String supervisorEn(Caja caja) throws Exception {
        String username = "ri.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor de reimpresión', 2, ?, false)""", username, codificador.encode(CONTRASENA));
        return cookie(enviar("/api/auth/login", "POST", login(username, "contrasena", CONTRASENA), equipoDe(caja)),
                "POS_SESION");
    }

    /** Una venta cobrada y finalizada por el cajero; devuelve el id de su factura certificada. */
    private long vender(Cajero cajero) throws Exception {
        String codigo = "RI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto reimpreso\",\"precio\":6.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo),
                cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion()).body())
                .contains("\"estado\":\"CERTIFICADA\"");
        return jdbc.queryForObject("""
                SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?
                ORDER BY f.id DESC LIMIT 1""", Long.class, cajero.usuarioId());
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
    }

    private static long idDe(HttpResponse<String> respuesta) {
        String cuerpo = respuesta.body();
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
