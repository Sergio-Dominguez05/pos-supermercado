package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado.Escenario;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-08.10 Anular una factura y HU-09.02 crit. 2 (la anulación queda en el turno de la venta), contra PostgreSQL real y
 * el certificador simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + AnulacionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class AnulacionIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    CertificadorFelSimulado simulador;

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
    void certificadorQueAcepta() {
        simulador.usarEscenario(Escenario.ACEPTAR);
    }

    @Test
    void seBuscaPorNumeroSerieAutorizacionOFecha() throws Exception {   // crit. 1
        Vendida vendida = ventaCertificada();
        Map<String, Object> factura = jdbc.queryForMap(
                "SELECT numero, uuid_autorizacion::text AS autorizacion FROM factura WHERE id = ?", vendida.facturaId());
        String hoy = LocalDate.now(ZoneId.of("America/Guatemala")).toString();

        for (String texto : new String[] {(String) factura.get("numero"), (String) factura.get("autorizacion"),
                ((String) factura.get("autorizacion")).substring(0, 8)}) {
            assertThat(buscar("buscar=" + texto).body()).as(texto).contains("\"facturaId\":" + vendida.facturaId());
        }
        assertThat(buscar("buscar=SIM&fecha=" + hoy).body()).contains("\"facturaId\":" + vendida.facturaId(),
                "\"turnoAbierto\":true", "\"estado\":\"CERTIFICADA\"");
        assertThat(buscar("buscar=" + factura.get("numero") + "&fecha=2020-01-01").body())
                .doesNotContain("\"facturaId\":" + vendida.facturaId() + ",");
    }

    @Test
    void conMotivoLaFacturaQuedaAnuladaEnElTurnoDeLaVenta() throws Exception {   // crit. 2, 3, 4 y HU-09.02
        Vendida vendida = ventaCertificada();
        assertThat(anular(vendida, "").body()).contains("MOTIVO_ANULACION_REQUERIDO");
        assertThat(anular(vendida, "Mal").body()).contains("MOTIVO_ANULACION_REQUERIDO");

        HttpResponse<String> anulada = anular(vendida, "Error de digitación en el producto");

        assertThat(anulada.statusCode()).isEqualTo(200);
        assertThat(anulada.body()).contains("\"estado\":\"ANULADA\"",
                "\"motivoAnulacion\":\"Error de digitación en el producto\"");
        Map<String, Object> anulacion = jdbc.queryForMap("""
                SELECT a.usuario_id, a.turno_id = v.turno_id AS del_turno_de_la_venta, a.respuesta_fel->>'aceptada' AS ok
                FROM anulacion a JOIN factura f ON f.id = a.factura_id JOIN venta v ON v.id = f.venta_id
                WHERE a.factura_id = ?""", vendida.facturaId());
        long adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        assertThat(anulacion).containsEntry("usuario_id", adminId).containsEntry("del_turno_de_la_venta", true)
                .containsEntry("ok", "true");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fel_intento WHERE factura_id = ? AND operacion = 'ANULAR' AND exitoso""",
                Integer.class, vendida.facturaId())).isEqualTo(1);
        // Una factura anulada no se vuelve a anular.
        assertThat(anular(vendida, "Otra vez").body()).contains("FACTURA_NO_ANULABLE", "ya está anulada");
    }

    @Test
    void siElCertificadorLaRechazaONoRespondeLaFacturaSigueVigente() throws Exception {   // crit. 3
        Vendida vendida = ventaCertificada();

        simulador.usarEscenario(Escenario.RECHAZAR);
        assertThat(anular(vendida, "Cliente devolvió todo").body()).contains("ANULACION_RECHAZADA",
                "La SAT no permite anular");
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        assertThat(anular(vendida, "Cliente devolvió todo").body()).contains("CERTIFICADOR_NO_DISPONIBLE");

        assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id = ?", String.class, vendida.facturaId()))
                .isEqualTo("CERTIFICADA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fel_intento WHERE factura_id = ? AND operacion = 'ANULAR' AND NOT exitoso""",
                Integer.class, vendida.facturaId())).isEqualTo(2);
    }

    @Test
    void conElTurnoDeLaVentaCerradoNoSeAnula() throws Exception {   // crit. 6 y decisión D2
        Vendida vendida = ventaCertificada();
        jdbc.update("UPDATE turno SET estado = 'CERRADO', cerrado_en = now() WHERE id = ?", vendida.turnoId());

        HttpResponse<String> respuesta = anular(vendida, "Error de digitación");

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("TURNO_DE_LA_VENTA_CERRADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id = ?", String.class, vendida.facturaId()))
                .isEqualTo("CERTIFICADA");
    }

    @Test
    void soloElSupervisorYElAdministradorAnulan() throws Exception {   // crit. 5
        Vendida vendida = ventaCertificada();

        assertThat(enviar("/api/facturas/" + vendida.facturaId() + "/anular", "POST", "{\"motivo\":\"Error de caja\"}",
                vendida.sesionCajero()).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/facturas?buscar=SIM", "GET", null, vendida.sesionCajero()).statusCode()).isEqualTo(403);
    }

    // Apoyo

    record Vendida(long facturaId, long turnoId, String sesionCajero) {}

    private HttpResponse<String> buscar(String consulta) throws Exception {
        return enviar("/api/facturas?" + consulta, "GET", null, cookieAdmin);
    }

    private HttpResponse<String> anular(Vendida vendida, String motivo) throws Exception {
        return enviar("/api/facturas/" + vendida.facturaId() + "/anular", "POST",
                "{\"motivo\":\"%s\"}".formatted(motivo), cookieAdmin);
    }

    /** Una venta de un cajero nuevo, cobrada y con su factura certificada; su turno sigue abierto. */
    private Vendida ventaCertificada() throws Exception {
        String nombre = "Caja A" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "an.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de anulación', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "AN" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto a anular\",\"precio\":8.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), sesion)
                .statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/finalizar", "POST", null, sesion).body())
                .contains("\"estado\":\"CERTIFICADA\"");
        Map<String, Object> venta = jdbc.queryForMap("""
                SELECT f.id AS factura, v.turno_id FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE v.usuario_id = ?""", usuarioId);
        return new Vendida((Long) venta.get("factura"), (Long) venta.get("turno_id"), sesion);
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
