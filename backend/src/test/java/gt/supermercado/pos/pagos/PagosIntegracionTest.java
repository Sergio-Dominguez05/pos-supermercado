package gt.supermercado.pos.pagos;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.pagos.dominio.EmisionDeFactura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/** HU-07.03 Cobrar con tarjeta, contra PostgreSQL real con las migraciones. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + PagosIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class PagosIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    /** La emisión real (provisional, sin factura hasta EP-08); una prueba la hace fallar (HU-07.08 crit. 4). */
    @MockitoSpyBean
    EmisionDeFactura emision;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
    }

    // Crit. 1 y 2: el total a cobrar y el monto propuesto

    @Test
    void elCobroMuestraElTotalYProponeElPendienteComoMonto() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00

        HttpResponse<String> estado = enviar("/api/ventas/actual/pagos", "GET", null, cajero.sesion());

        assertThat(estado.statusCode()).isEqualTo(200);
        assertThat(estado.body()).contains("\"total\":15.00", "\"pagado\":0.00", "\"pendiente\":15.00", "\"pagos\":[]");
    }

    @Test
    void sinMontoSeRegistraElTotalPendienteConElTurnoYElUsuario() throws Exception {   // crit. 2, 3 y HU-09.02
        Cajero cajero = cajeroConVenta("11.20", 1);

        HttpResponse<String> respuesta = pagar(cajero, "{\"tipoTarjeta\":\"CREDITO\",\"autorizacion\":\" A1B2C3 \","
                + "\"ultimos4\":\"4321\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"pagado\":11.20", "\"pendiente\":0.00", "\"tipoTarjeta\":\"CREDITO\"",
                "\"autorizacion\":\"A1B2C3\"", "\"ultimos4\":\"4321\"", "\"monto\":11.20");
        Map<String, Object> pago = jdbc.queryForMap("""
                SELECT p.turno_id = t.id AS del_turno, p.usuario_id, p.tipo_tarjeta, p.monto::text AS monto
                FROM pago p JOIN venta v ON v.id = p.venta_id JOIN turno t ON t.id = v.turno_id
                WHERE v.usuario_id = ?""", cajero.usuarioId());
        assertThat(pago).containsEntry("del_turno", true).containsEntry("usuario_id", cajero.usuarioId())
                .containsEntry("tipo_tarjeta", "CREDITO").containsEntry("monto", "11.20");
    }

    @Test
    void losUltimosCuatroDigitosSonOpcionales() throws Exception {   // crit. 4
        Cajero cajero = cajeroConVenta("5.00", 1);

        HttpResponse<String> respuesta = pagar(cajero, "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"778899\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"ultimos4\":null", "\"tipoTarjeta\":\"DEBITO\"");
    }

    // Crit. 3 y 4: tipo y autorización obligatorios; nunca el número completo de la tarjeta

    @Test
    void sinTipoOSinAutorizacionNoSeRegistraElPago() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 1);

        assertThat(pagar(cajero, "{\"autorizacion\":\"123456\"}").body()).contains("TIPO_TARJETA_REQUERIDO");
        assertThat(pagar(cajero, "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"  \"}").body())
                .contains("AUTORIZACION_REQUERIDA");
        assertThat(pagar(cajero, "{\"tipoTarjeta\":\"PREPAGO\",\"autorizacion\":\"123456\"}").statusCode())
                .isEqualTo(400);
        assertThat(pagosDe(cajero)).isZero();
    }

    @Test
    void nuncaSeAceptaElNumeroCompletoDeLaTarjeta() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 1);

        assertThat(pagar(cajero, "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\","
                + "\"ultimos4\":\"4111111111111111\"}").body()).contains("ULTIMOS4_INVALIDOS");
        assertThat(pagar(cajero, "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\",\"ultimos4\":\"12a4\"}")
                .body()).contains("ULTIMOS4_INVALIDOS");
        assertThat(pagar(cajero, "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"4111 1111 1111 1111\"}").body())
                .contains("AUTORIZACION_INVALIDA");
        assertThat(pagosDe(cajero)).isZero();
    }

    // Crit. 5 y 7: el monto no supera el pendiente; quetzales con centavos

    @Test
    void elMontoNoPuedeSerMayorAlPendienteNiCeroNiConMasDeDosDecimales() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00

        assertThat(pagar(cajero, pago("15.01")).body()).contains("MONTO_MAYOR_AL_PENDIENTE", "Q15.01", "Q15.00");
        for (String monto : List.of("0", "-5", "10.005")) {
            assertThat(pagar(cajero, pago(monto)).body()).as("monto " + monto).contains("MONTO_INVALIDO");
        }
        assertThat(pagosDe(cajero)).isZero();
        assertThat(pagar(cajero, pago("15")).body()).contains("\"pendiente\":0.00");
    }

    @Test
    void enV1SeCobraConUnaSolaTarjetaPorVenta() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 3);
        assertThat(pagar(cajero, pago("10.00")).body()).contains("\"pendiente\":5.00");

        HttpResponse<String> segunda = pagar(cajero, pago("5.00"));

        assertThat(segunda.statusCode()).isEqualTo(422);
        assertThat(segunda.body()).contains("PAGO_YA_REGISTRADO");
        assertThat(pagosDe(cajero)).isEqualTo(1);
    }

    // Sin productos o sin turno no se cobra (HU-06.15 crit. 2, HU-09.02 crit. 2)

    @Test
    void sinProductosOSinTurnoNoSeRegistraElPago() throws Exception {
        Cajero conTurno = cajeroConTurnoAbierto();
        enviar("/api/ventas", "POST", null, conTurno.sesion());
        assertThat(pagar(conTurno, pago("5.00")).body()).contains("VENTA_VACIA");

        Cajero sinTurno = cajeroEnCaja();
        assertThat(pagar(sinTurno, pago("5.00")).body()).contains("CAJA_CERRADA");
        assertThat(enviar("/api/ventas/actual/pagos", "GET", null, sinTurno.sesion()).body()).contains("CAJA_CERRADA");
    }

    // Decisión de HU-07.03: con el pago registrado la venta ya no se modifica hasta eliminarlo (HU-07.06)

    @Test
    void conElPagoRegistradoLaVentaYaNoSeModifica() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 2);
        long linea = jdbc.queryForObject("""
                SELECT l.id FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO'""", Long.class, cajero.usuarioId());
        pagar(cajero, pago("10.00"));

        List<HttpResponse<String>> intentos = List.of(
                enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(cajero.codigo()),
                        cajero.sesion()),
                enviar("/api/ventas/actual/lineas/" + linea + "/cantidad", "PUT", "{\"cantidad\":5}", cajero.sesion()),
                enviar("/api/ventas/actual/lineas/" + linea, "DELETE", null, cajero.sesion()),
                enviar("/api/ventas/actual/suspender", "POST", null, cajero.sesion()),
                enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion()));

        assertThat(intentos).allSatisfy(intento -> {
            assertThat(intento.statusCode()).isEqualTo(422);
            assertThat(intento.body()).contains("VENTA_CON_PAGO");
        });
        assertThat(enviar("/api/ventas/actual", "GET", null, cajero.sesion()).body())
                .contains("\"estado\":\"EN_CURSO\"", "\"cantidad\":2", "\"total\":10.00");
        // Se puede seguir consultando el cobro.
        assertThat(enviar("/api/ventas/actual/cobro", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
    }

    // HU-07.06 Eliminar un pago antes de finalizar

    @Test
    void alEliminarElPagoSeRecalculaElPendienteYLaVentaVuelveAModificarse() throws Exception {   // crit. 1 y 2
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00
        long pago = idDePago(pagar(cajero, pago("10.00")));

        HttpResponse<String> respuesta = eliminarPago(pago, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"total\":15.00", "\"pagado\":0.00", "\"pendiente\":15.00", "\"pagos\":[]");
        // La venta ya no tiene pago: se puede modificar y volver a cobrar.
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(cajero.codigo()),
                cajero.sesion()).body()).contains("\"total\":20.00");
        assertThat(pagar(cajero, pago("20.00")).body()).contains("\"pendiente\":0.00");
    }

    @Test
    void elPagoEliminadoNoSeBorraYQuedaEnLaBitacora() throws Exception {   // crit. 4
        Cajero cajero = cajeroConVenta("11.20", 1);
        long pago = idDePago(pagar(cajero, "{\"tipoTarjeta\":\"CREDITO\",\"autorizacion\":\"A1B2C3\",\"ultimos4\":\"4321\"}"));

        eliminarPago(pago, cajero);

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT monto::text AS monto, eliminado_en IS NOT NULL AS eliminado, eliminado_por FROM pago WHERE id = ?",
                pago);
        assertThat(fila).containsEntry("monto", "11.20").containsEntry("eliminado", true)
                .containsEntry("eliminado_por", cajero.usuarioId());
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT usuario_id, exitoso, descripcion, cambios::text AS cambios FROM bitacora
                WHERE accion = 'PAGO_TARJETA_ELIMINADO' AND entidad = 'pago' AND entidad_id = ? AND exitoso""", pago);
        assertThat(registro).containsEntry("usuario_id", cajero.usuarioId());
        assertThat((String) registro.get("descripcion")).contains("crédito ••••4321", "Q11.20");
        assertThat((String) registro.get("cambios")).contains("\"ultimos4\": \"4321\"", "\"autorizacion\": \"A1B2C3\"",
                "\"monto\": 11.20", "\"tipoTarjeta\": \"CREDITO\"");
    }

    @Test
    void soloSeEliminanPagosVigentesDeLaVentaEnCursoPropia() throws Exception {   // crit. 1
        Cajero ana = cajeroConVenta("5.00", 1);
        Cajero beto = cajeroConVenta("5.00", 1);
        long deAna = idDePago(pagar(ana, pago("5.00")));

        HttpResponse<String> ajeno = eliminarPago(deAna, beto);
        assertThat(ajeno.statusCode()).isEqualTo(404);
        assertThat(ajeno.body()).contains("PAGO_NO_ENCONTRADO");
        assertThat(pagosDe(ana)).isEqualTo(1);

        eliminarPago(deAna, ana);
        assertThat(eliminarPago(deAna, ana).statusCode()).isEqualTo(404);   // ya eliminado
        assertThat(eliminarPago(999_999_999L, ana).statusCode()).isEqualTo(404);
    }

    @Test
    void losPagosDeUnaVentaFinalizadaNoSeModifican() throws Exception {   // crit. 3
        Cajero cajero = cajeroConVenta("5.00", 1);
        long pago = idDePago(pagar(cajero, pago("5.00")));
        // Finalizar el cobro llega con HU-07.08 (#63); aquí se deja la venta como la dejará esa historia.
        jdbc.update("UPDATE venta SET estado = 'FINALIZADA', finalizada_en = now() WHERE usuario_id = ? AND estado = 'EN_CURSO'",
                cajero.usuarioId());

        HttpResponse<String> respuesta = eliminarPago(pago, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("SIN_VENTA_EN_CURSO");
        // La base lo impide aunque se intente directamente (trigger de V20).
        assertThatThrownBy(() -> jdbc.update("UPDATE pago SET eliminado_en = now(), eliminado_por = ? WHERE id = ?",
                cajero.usuarioId(), pago)).hasMessageContaining("ya no está en curso");
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NULL FROM pago WHERE id = ?", Boolean.class, pago))
                .isTrue();
    }

    @Test
    void unPagoEliminadoNoSeVuelveAModificar() throws Exception {
        Cajero cajero = cajeroConVenta("5.00", 1);
        long pago = idDePago(pagar(cajero, pago("5.00")));
        eliminarPago(pago, cajero);

        assertThatThrownBy(() -> jdbc.update("UPDATE pago SET eliminado_en = NULL, eliminado_por = NULL WHERE id = ?",
                pago)).hasMessageContaining("fue eliminado");
    }

    // HU-07.07 Validar que el pago cubra el total

    @Test
    void elCobroSoloQuedaCompletoCuandoLosPagosCubrenElTotal() throws Exception {   // crit. 1 y 2
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00
        assertThat(enviar("/api/ventas/actual/pagos", "GET", null, cajero.sesion()).body())
                .contains("\"completo\":false");

        long parcial = idDePago(pagar(cajero, pago("10.00")));
        assertThat(enviar("/api/ventas/actual/pagos", "GET", null, cajero.sesion()).body())
                .contains("\"pendiente\":5.00", "\"completo\":false");

        assertThat(eliminarPago(parcial, cajero).body()).contains("\"completo\":false");
        assertThat(pagar(cajero, pago("15.00")).body()).contains("\"pendiente\":0.00", "\"completo\":true");
    }

    // HU-07.08 Finalizar el cobro

    @Test
    void alFinalizarLaVentaQuedaRegistradaConSusTotalesYSusPagos() throws Exception {   // crit. 1, 2 y 3
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00
        long pago = idDePago(pagar(cajero, pago("15.00")));

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"articulos\":3", "\"total\":15.00", "\"pagado\":15.00",
                "\"facturaEmitida\":false", "\"id\":" + pago);
        Map<String, Object> venta = jdbc.queryForMap("""
                SELECT estado, finalizada_en IS NOT NULL AS con_hora, subtotal::text AS subtotal, iva::text AS iva,
                       total::text AS total
                FROM venta WHERE usuario_id = ?""", cajero.usuarioId());
        assertThat(venta).containsEntry("estado", "FINALIZADA").containsEntry("con_hora", true)
                .containsEntry("subtotal", "13.39").containsEntry("iva", "1.61").containsEntry("total", "15.00");
        // Crit. 2 y 3: el pago queda con la venta, el turno abierto (que lo suma al resumen) y el usuario.
        Map<String, Object> asociado = jdbc.queryForMap("""
                SELECT p.venta_id = v.id AS de_la_venta, p.turno_id = t.id AS del_turno, t.estado AS turno,
                       t.caja_id = v.caja_id AS de_la_caja, p.usuario_id, p.eliminado_en IS NULL AS vigente
                FROM pago p JOIN venta v ON v.id = p.venta_id JOIN turno t ON t.id = p.turno_id WHERE p.id = ?""", pago);
        assertThat(asociado).containsEntry("de_la_venta", true).containsEntry("del_turno", true)
                .containsEntry("turno", "ABIERTO").containsEntry("de_la_caja", true)
                .containsEntry("usuario_id", cajero.usuarioId()).containsEntry("vigente", true);
    }

    @Test
    void alTerminarLaCajaQuedaListaParaUnaNuevaVenta() throws Exception {   // crit. 5
        Cajero cajero = cajeroConVenta("5.00", 1);
        pagar(cajero, pago("5.00"));
        String finalizada = finalizar(cajero).body();

        assertThat(enviar("/api/ventas/actual/pagos", "GET", null, cajero.sesion()).body())
                .contains("SIN_VENTA_EN_CURSO");
        HttpResponse<String> nueva = enviar("/api/ventas", "POST", null, cajero.sesion());
        assertThat(nueva.statusCode()).isEqualTo(200);
        assertThat(nueva.body()).contains("\"estado\":\"EN_CURSO\"", "\"lineas\":[]");
        assertThat(numero(nueva.body())).isGreaterThan(numero(finalizada));
        // La venta finalizada ya no se toca: ni sus líneas ni sus pagos.
        assertThat(finalizar(cajero).body()).contains("PAGO_INCOMPLETO");
    }

    @Test
    void conElPagoIncompletoNoSeFinaliza() throws Exception {   // crit. 1 y HU-07.07
        Cajero cajero = cajeroConVenta("5.00", 3);   // Q15.00
        assertThat(finalizar(cajero).body()).contains("PAGO_INCOMPLETO");

        pagar(cajero, pago("10.00"));
        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("PAGO_INCOMPLETO", "Falta cobrar Q5.00");
        assertThat(estadoDeLaVenta(cajero)).isEqualTo("EN_CURSO");
    }

    @Test
    void sinTurnoOSinVentaNoSeFinaliza() throws Exception {
        assertThat(finalizar(cajeroEnCaja()).body()).contains("CAJA_CERRADA");
        Cajero conTurno = cajeroConTurnoAbierto();
        assertThat(finalizar(conTurno).body()).contains("SIN_VENTA_EN_CURSO");
    }

    @Test
    void siFallaAlGuardarLaVentaNoQuedaAMediasYSePuedeReintentar() throws Exception {   // crit. 4
        Cajero cajero = cajeroConVenta("5.00", 2);
        long pago = idDePago(pagar(cajero, pago("10.00")));
        doThrow(new IllegalStateException("Falla simulada al emitir la factura")).when(emision).emitir(any());

        assertThat(finalizar(cajero).statusCode()).isEqualTo(500);

        Map<String, Object> venta = jdbc.queryForMap(
                "SELECT estado, finalizada_en IS NULL AS sin_hora, total IS NULL AS sin_total FROM venta WHERE usuario_id = ?",
                cajero.usuarioId());
        assertThat(venta).containsEntry("estado", "EN_CURSO").containsEntry("sin_hora", true)
                .containsEntry("sin_total", true);
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NULL FROM pago WHERE id = ?", Boolean.class, pago))
                .isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'VENTA_FINALIZADA' AND exitoso AND usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isZero();

        doCallRealMethod().when(emision).emitir(any());
        HttpResponse<String> reintento = finalizar(cajero);
        assertThat(reintento.statusCode()).isEqualTo(200);
        assertThat(estadoDeLaVenta(cajero)).isEqualTo("FINALIZADA");
    }

    // Apoyo

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    private String estadoDeLaVenta(Cajero cajero) {
        return jdbc.queryForObject("SELECT estado FROM venta WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
                String.class, cajero.usuarioId());
    }

    private static long numero(String cuerpo) {
        int inicio = cuerpo.indexOf("\"numero\":") + 9;
        return Long.parseLong(cuerpo.substring(inicio, cuerpo.indexOf(',', inicio)));
    }

    private HttpResponse<String> eliminarPago(long pago, Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/pagos/" + pago, "DELETE", null, cajero.sesion());
    }

    /** El id del último pago de la respuesta del cobro. */
    private static long idDePago(HttpResponse<String> cobro) {
        assertThat(cobro.statusCode()).isEqualTo(200);
        String cuerpo = cobro.body();
        int inicio = cuerpo.lastIndexOf("{\"id\":") + 6;
        return Long.parseLong(cuerpo.substring(inicio, cuerpo.indexOf(',', inicio)));
    }

    private static String pago(String monto) {
        return "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\",\"monto\":%s}".formatted(monto);
    }

    private HttpResponse<String> pagar(Cajero cajero, String cuerpo) throws Exception {
        return enviar("/api/ventas/actual/pagos", "POST", cuerpo, cajero.sesion());
    }

    private int pagosDe(Cajero cajero) {
        return jdbc.queryForObject("SELECT count(*) FROM pago WHERE usuario_id = ?", Integer.class, cajero.usuarioId());
    }

    record Cajero(long usuarioId, String sesion, String codigo) {}

    /** Un cajero con turno abierto y una venta en curso con {@code cantidad} unidades de un producto nuevo. */
    private Cajero cajeroConVenta(String precio, int cantidad) throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        String codigo = "PG" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> creado = enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto de cobro\",\"precio\":%s}".formatted(codigo, precio),
                cookieAdmin);
        assertThat(creado.statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST",
                "{\"codigo\":\"%s\",\"cantidad\":%d}".formatted(codigo, cantidad), cajero.sesion()).statusCode())
                .isEqualTo(200);
        return new Cajero(cajero.usuarioId(), cajero.sesion(), codigo);
    }

    private Cajero cajeroConTurnoAbierto() throws Exception {
        Cajero cajero = cajeroEnCaja();
        assertThat(enviar("/api/turnos", "POST", null, cajero.sesion()).statusCode()).isEqualTo(201);
        return cajero;
    }

    /** Un cajero que inició sesión en una caja nueva, sin abrir el turno. */
    private Cajero cajeroEnCaja() throws Exception {
        String nombre = "Caja P" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "pg.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de cobro', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, sesion, null);
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
