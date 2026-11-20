package gt.supermercado.pos.caja;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-09.08 Cerrar la caja, HU-09.09 Justificar diferencias y HU-09.10 Imprimir el comprobante de cierre, contra
 * PostgreSQL real, el certificador y la impresora simulados.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CierreIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CierreIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";
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

    @Test
    void elResumenSumaPorTarjetaYDescuentaLasAnuladas() throws Exception {   // 09.08 crit. 3 y decisión de Sergio
        Cajero cajero = cajeroConTurno();
        vender(cajero, "10.00", "DEBITO");
        vender(cajero, "5.00", "CREDITO");
        long anulada = vender(cajero, "3.00", "DEBITO");
        assertThat(enviar("/api/facturas/" + anulada + "/anular", "POST", "{\"motivo\":\"Error de cobro\"}",
                cookieAdmin).statusCode()).isEqualTo(200);

        HttpResponse<String> resumen = enviar("/api/turnos/actual/cierre", "GET", null, cajero.sesion());

        assertThat(resumen.statusCode()).isEqualTo(200);
        assertThat(resumen.body()).contains("\"ventas\":3", "\"debito\":10.00", "\"credito\":5.00", "\"anuladas\":1",
                "\"totalAnulado\":3.00", "\"totalRegistrado\":15.00");
    }

    @Test
    void sinDiferenciaSeCierraSeGuardaYSeImprimeElComprobante() throws Exception {   // 09.08 crit. 4 a 6, 09.10
        Cajero cajero = cajeroConTurno();
        vender(cajero, "12.50", "DEBITO");

        HttpResponse<String> cierre = cerrar(cajero, "12.50", null);

        assertThat(cierre.statusCode()).isEqualTo(200);
        assertThat(cierre.body()).contains("\"diferencia\":0.00", "\"totalLote\":12.50", "\"totalRegistrado\":12.50",
                "\"impresa\":true");
        Map<String, Object> turno = jdbc.queryForMap("""
                SELECT estado, cerrado_en IS NOT NULL AS con_hora, cerrado_por, total_registrado::text AS registrado,
                       total_lote::text AS lote, diferencia::text AS diferencia
                FROM turno WHERE id = ?""", cajero.turnoId());
        assertThat(turno).containsEntry("estado", "CERRADO").containsEntry("con_hora", true)
                .containsEntry("cerrado_por", cajero.usuarioId()).containsEntry("registrado", "12.50")
                .containsEntry("lote", "12.50").containsEntry("diferencia", "0.00");
        String comprobante = new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850);
        assertThat(comprobante).contains("CIERRE DE CAJA", "Tarjeta de débito", "Q12.50", "DIFERENCIA",
                "Firma del cajero", "Firma del supervisor");
        assertThat(comprobante.split("CIERRE DE CAJA", -1)).hasSize(3);   // las dos copias de cierre de la impresora
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'TURNO_CERRADO' AND exitoso AND entidad_id = ?""",
                Integer.class, cajero.turnoId())).isEqualTo(1);

        // Crit. 4: cerrado, no se modifica y ya no se vende en él.
        assertThatThrownBy(() -> jdbc.update("UPDATE turno SET total_lote = 0 WHERE id = ?", cajero.turnoId()))
                .hasMessageContaining("ya está cerrado");
        assertThat(enviar("/api/ventas", "POST", null, cajero.sesion()).body()).contains("CAJA_CERRADA");
    }

    @Test
    void unaDiferenciaExigeJustificacion() throws Exception {   // 09.08 crit. 3 y 09.09 crit. 1
        Cajero cajero = cajeroConTurno();
        vender(cajero, "20.00", "CREDITO");

        HttpResponse<String> sinJustificar = cerrar(cajero, "18.00", " ");
        assertThat(sinJustificar.statusCode()).isEqualTo(422);
        assertThat(sinJustificar.body()).contains("JUSTIFICACION_REQUERIDA", "Q-2.00");
        assertThat(cerrar(cajero, null, null).body()).contains("TOTAL_LOTE_INVALIDO");

        HttpResponse<String> justificado = cerrar(cajero, "18.00", "Un pago se registró dos veces en la terminal");

        assertThat(justificado.statusCode()).isEqualTo(200);
        assertThat(justificado.body()).contains("\"diferencia\":-2.00",
                "\"justificacion\":\"Un pago se registró dos veces en la terminal\"");
        assertThat(new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850))
                .contains("Justificación: Un pago se registró dos veces en", "la terminal");   // en dos renglones
    }

    @Test
    void conVentasPorResolverNoSeCierra() throws Exception {   // 09.08 crit. 1
        Cajero cajero = cajeroConTurno();
        agregarProducto(cajero, "4.00");
        assertThat(enviar("/api/ventas/actual/suspender", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(cerrar(cajero, "0", null).body()).contains("VENTAS_SUSPENDIDAS");

        long suspendida = jdbc.queryForObject(
                "SELECT id FROM venta WHERE turno_id = ? AND estado = 'SUSPENDIDA'", Long.class, cajero.turnoId());
        assertThat(enviar("/api/ventas/suspendidas/" + suspendida + "/retomar", "POST", null, cajero.sesion())
                .statusCode()).isEqualTo(200);
        assertThat(cerrar(cajero, "0", null).body()).contains("VENTA_EN_CURSO");

        assertThat(enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        enviar("/api/ventas", "POST", null, cajero.sesion());   // la pantalla deja lista una venta vacía
        assertThat(cerrar(cajero, "0", null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venta WHERE turno_id = ? AND estado = 'EN_CURSO'",
                Integer.class, cajero.turnoId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'TURNO_CERRADO' AND NOT exitoso AND usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isEqualTo(2);
    }

    @Test
    void losCierresAnterioresSeConsultanYSeReimprimen() throws Exception {   // 09.10 crit. 3
        Cajero cajero = cajeroConTurno();
        vender(cajero, "7.00", "DEBITO");
        cerrar(cajero, "7.00", null);

        HttpResponse<String> cierres = enviar("/api/turnos/cierres", "GET", null, cajero.sesion());
        assertThat(cierres.statusCode()).isEqualTo(200);
        assertThat(cierres.body()).contains("\"turnoId\":" + cajero.turnoId(), "\"totalLote\":7.00");
        Cajero otro = cajeroConTurno();
        assertThat(enviar("/api/turnos/cierres", "GET", null, otro.sesion()).body())
                .doesNotContain("\"turnoId\":" + cajero.turnoId() + ",");

        HttpResponse<String> reimpresion = enviar("/api/turnos/" + cajero.turnoId() + "/cierre/reimprimir", "POST",
                null, cajero.sesion());
        assertThat(reimpresion.body()).contains("\"impresa\":true");
        String copia = new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850);
        assertThat(copia).contains("COPIA - REIMPRESIÓN", "CIERRE DE CAJA");
        assertThat(enviar("/api/turnos/" + cajero.turnoId() + "/cierre/reimprimir", "POST", null, otro.sesion())
                .body()).contains("CIERRE_DE_OTRO_USUARIO");
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion, long turnoId, String host) {}

    private HttpResponse<String> cerrar(Cajero cajero, String totalLote, String justificacion) throws Exception {
        String cuerpo = "{\"totalLote\":%s,\"justificacion\":%s}".formatted(totalLote,
                justificacion == null ? "null" : "\"" + justificacion + "\"");
        return enviar("/api/turnos/actual/cerrar", "POST", cuerpo, cajero.sesion());
    }

    /** Un cajero con turno abierto en una caja nueva cuya impresora saca dos copias de cada cierre. */
    private Cajero cajeroConTurno() throws Exception {
        String host = "cierre-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000 + ".local";
        long impresora = idDe(enviar("/api/configuracion/impresoras", "POST",
                "{\"nombre\":\"Térmica %s\",\"host\":\"%s\",\"anchoMm\":80,\"copiasCierre\":2}".formatted(host, host),
                cookieAdmin));
        String nombre = "Caja CI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST",
                "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresora), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "ci.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero que cierra', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        HttpResponse<String> turno = enviar("/api/turnos", "POST", null, sesion);
        assertThat(turno.statusCode()).isEqualTo(201);
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, sesion, idDe(turno), host);
    }

    private void agregarProducto(Cajero cajero, String precio) throws Exception {
        String codigo = "CI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto del cierre\",\"precio\":%s}".formatted(codigo,
                        precio), cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo),
                cajero.sesion()).statusCode()).isEqualTo(200);
    }

    /** Una venta cobrada con la tarjeta indicada y finalizada; devuelve el id de su factura. */
    private long vender(Cajero cajero, String precio, String tarjeta) throws Exception {
        agregarProducto(cajero, precio);
        assertThat(enviar("/api/ventas/actual/pagos", "POST",
                "{\"tipoTarjeta\":\"%s\",\"autorizacion\":\"123456\"}".formatted(tarjeta), cajero.sesion())
                .statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        return jdbc.queryForObject("""
                SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.turno_id = ?
                ORDER BY f.id DESC LIMIT 1""", Long.class, cajero.turnoId());
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
