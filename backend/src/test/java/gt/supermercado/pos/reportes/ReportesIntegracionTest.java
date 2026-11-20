package gt.supermercado.pos.reportes;

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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-10.01, HU-10.02 y HU-10.15 contra PostgreSQL real: filtros comunes, ventas por período y acceso a los reportes.
 * Cada prueba usa su propia caja con ventas en fechas pasadas, para no depender de las ventas de otras pruebas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ReportesIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ReportesIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String CONTRASENA = "Clave2026segura";
    static final String PIN = "4821";
    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    JsonMapper json;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String supervisor;
    private long supervisorId;

    @BeforeEach
    void sesion() throws Exception {
        String username = usuario(2, CONTRASENA);
        supervisorId = idDeUsuario(username);
        supervisor = login("/api/auth/login", username, "contrasena", CONTRASENA);
    }

    @Test
    void sumaLasVentasFinalizadasDelPeriodoPorDiaSinLasAnuladas() throws Exception {   // HU-10.02 crit. 1 y 3
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-03-03T15:00:00Z", "100.00", null);
        caja.venta("FINALIZADA", "2025-03-03T20:00:00Z", "50.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-03-03T21:00:00Z", "33.33", "RECHAZADA");    // cobrada: sí es venta
        caja.venta("FINALIZADA", "2025-03-03T22:00:00Z", "999.00", "ANULADA");     // crit. 3: no se suma
        caja.venta("CANCELADA", "2025-03-03T22:30:00Z", "500.00", null);
        // 23:30 del 4 de marzo en Guatemala (UTC-6) es el 5 de marzo en UTC: cuenta para el 4.
        caja.venta("FINALIZADA", "2025-03-05T05:30:00Z", "11.20", null);

        JsonNode reporte = ok(get("/api/reportes/ventas?desde=2025-03-03&hasta=2025-03-05&cajaId=" + caja.id));

        assertThat(reporte.get("agrupacion").asString()).isEqualTo("DIA");
        List<JsonNode> filas = filas(reporte);
        assertThat(filas).extracting(f -> f.get("periodo").asString())
                .containsExactly("03/03/2025", "04/03/2025", "05/03/2025");
        assertFila(filas.get(0), 3, "183.33", "61.11");
        assertThat(filas.get(0).get("subtotal").decimalValue()).isEqualByComparingTo("163.69");
        assertThat(filas.get(0).get("iva").decimalValue()).isEqualByComparingTo("19.64");
        assertFila(filas.get(1), 1, "11.20", "11.20");
        assertFila(filas.get(2), 0, "0.00", "0.00");   // un día sin ventas también aparece
        assertFila(reporte.get("totales"), 4, "194.53", "48.63");
    }

    @Test
    void agrupaPorSemanaYPorMesRecortandoAlRangoPedido() throws Exception {   // HU-10.02 crit. 2
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-01-30T18:00:00Z", "10.00", null);   // jueves
        caja.venta("FINALIZADA", "2025-02-03T18:00:00Z", "20.00", null);   // lunes
        caja.venta("FINALIZADA", "2025-02-14T18:00:00Z", "30.00", null);

        JsonNode semanas = ok(get("/api/reportes/ventas?desde=2025-01-29&hasta=2025-02-14&agrupacion=SEMANA&cajaId="
                + caja.id));
        assertThat(filas(semanas)).extracting(f -> f.get("periodo").asString()).containsExactly(
                "Semana del 29/01/2025 al 02/02/2025", "Semana del 03/02/2025 al 09/02/2025",
                "Semana del 10/02/2025 al 14/02/2025");
        assertThat(filas(semanas)).extracting(f -> f.get("cantidadVentas").asLong()).containsExactly(1L, 1L, 1L);

        JsonNode meses = ok(get("/api/reportes/ventas?desde=2025-01-29&hasta=2025-02-14&agrupacion=MES&cajaId="
                + caja.id));
        assertThat(filas(meses)).extracting(f -> f.get("periodo").asString()).containsExactly("Enero 2025",
                "Febrero 2025");
        assertFila(filas(meses).get(1), 2, "50.00", "25.00");
        assertThat(filas(meses).get(0).get("desde").asString()).isEqualTo("2025-01-29");
    }

    @Test
    void filtraPorCajeroYMuestraLosFiltrosEnElEncabezado() throws Exception {   // HU-10.01 crit. 2 y 4
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-04-10T18:00:00Z", "40.00", null);
        Caja otroCajero = caja.conOtroCajero();
        otroCajero.venta("FINALIZADA", "2025-04-10T19:00:00Z", "60.00", null);

        JsonNode todos = ok(get("/api/reportes/ventas?desde=2025-04-10&hasta=2025-04-10&cajaId=" + caja.id));
        JsonNode uno = ok(get("/api/reportes/ventas?desde=2025-04-10&hasta=2025-04-10&cajaId=%d&cajeroId=%d"
                .formatted(caja.id, otroCajero.cajeroId)));

        assertFila(todos.get("totales"), 2, "100.00", "50.00");
        assertFila(uno.get("totales"), 1, "60.00", "60.00");
        JsonNode encabezado = uno.get("encabezado");
        assertThat(encabezado.get("titulo").asString()).isEqualTo("Ventas por período");
        List<String> filtros = new ArrayList<>();
        encabezado.get("filtros").forEach(f -> filtros.add(f.get("etiqueta").asString() + ": " + f.get("valor").asString()));
        assertThat(filtros).containsExactly("Período: 10/04/2025", "Caja: " + caja.nombre,
                "Cajero: Usuario " + otroCajero.cajero + " (" + otroCajero.cajero + ")", "Agrupación: Día");
        assertThat(encabezado.get("generadoPor").asString()).endsWith("(" + usernameDe(supervisorId) + ")");
        assertThat(encabezado.get("generadoEn").asString()).isNotBlank();
    }

    @Test
    void lasOpcionesTraenLosRangosRapidosConLaFechaDelServidor() throws Exception {   // HU-10.01 crit. 2 y 3
        Caja caja = caja();
        JsonNode opciones = ok(get("/api/reportes/filtros"));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Guatemala"));

        assertThat(opciones.get("hoy").asString()).isEqualTo(hoy.toString());
        List<String> rangos = new ArrayList<>();
        opciones.get("rangos").forEach(r -> rangos.add(r.get("codigo").asString()));
        assertThat(rangos).containsExactly("HOY", "AYER", "ESTA_SEMANA", "ESTE_MES", "MES_ANTERIOR");
        assertThat(opciones.get("rangos").get(0).get("desde").asString()).isEqualTo(hoy.toString());
        List<String> cajas = new ArrayList<>();
        opciones.get("cajas").forEach(c -> cajas.add(c.get("etiqueta").asString()));
        assertThat(cajas).contains(caja.nombre);
        List<String> cajeros = new ArrayList<>();
        opciones.get("cajeros").forEach(c -> cajeros.add(c.get("etiqueta").asString()));
        assertThat(cajeros).anyMatch(c -> c.endsWith("(" + caja.cajero + ")")).noneMatch(c -> c.endsWith("(sistema)"));
        List<String> tarjetas = new ArrayList<>();
        opciones.get("tiposTarjeta").forEach(t -> tarjetas.add(t.get("valor").asString()));
        assertThat(tarjetas).containsExactly("DEBITO", "CREDITO");
    }

    @Test
    void rechazaLosFiltrosInvalidosYDejaElIntentoEnLaBitacora() throws Exception {   // HU-10.01 crit. 1, HU-10.15
        assertError(get("/api/reportes/ventas?hasta=2025-04-10"), "FILTRO_FECHAS_OBLIGATORIAS");
        assertError(get("/api/reportes/ventas?desde=2025-04-10&hasta=2025-04-01"), "FILTRO_FECHAS_INVERTIDAS");
        assertError(get("/api/reportes/ventas?desde=2015-01-01&hasta=2025-04-01"), "FILTRO_RANGO_EXCESIVO");
        assertError(get("/api/reportes/ventas?desde=2025-04-01&hasta=2025-04-10&cajaId=999999999"),
                "FILTRO_CAJA_INEXISTENTE");
        assertError(get("/api/reportes/ventas?desde=2025-04-01&hasta=2025-04-10&cajeroId=999999999"),
                "FILTRO_CAJERO_INEXISTENTE");
        assertThat(get("/api/reportes/ventas?desde=2025-04-01&hasta=2025-04-10&agrupacion=ANIO").statusCode())
                .isEqualTo(400);

        assertThat(jdbc.queryForList("""
                SELECT descripcion FROM bitacora WHERE accion = 'REPORTE_GENERADO' AND NOT exitoso AND usuario_id = ?
                ORDER BY secuencia""", String.class, supervisorId)).hasSize(5)
                .first().isEqualTo("Reporte \"Ventas por período\" rechazado: Indique la fecha inicial y la final del reporte.");
    }

    @Test
    void cadaReporteGeneradoQuedaEnLaBitacoraConSusFiltros() throws Exception {   // HU-10.15 crit. 3
        Caja caja = caja();
        ok(get("/api/reportes/ventas?desde=2025-05-01&hasta=2025-05-31&agrupacion=SEMANA&cajaId=" + caja.id));

        var registro = jdbc.queryForMap("""
                SELECT rol, modulo, descripcion, cambios->>'Caja' AS caja, cambios->>'reporte' AS reporte FROM bitacora
                WHERE accion = 'REPORTE_GENERADO' AND exitoso AND usuario_id = ? ORDER BY secuencia DESC LIMIT 1""",
                supervisorId);
        assertThat(registro.get("rol")).isEqualTo("SUPERVISOR");
        assertThat(registro.get("modulo")).isEqualTo("reportes");
        assertThat(registro.get("reporte")).isEqualTo("Ventas por período");
        assertThat(registro.get("caja")).isEqualTo(caja.nombre);
        assertThat(registro.get("descripcion")).isEqualTo("Reporte \"Ventas por período\" generado (Período: Del "
                + "01/05/2025 al 31/05/2025; Caja: " + caja.nombre + "; Cajero: Todos; Agrupación: Semana)");
    }

    @Test
    void elCajeroNoTieneAccesoALosReportesYElIntentoQuedaRegistrado() throws Exception {   // HU-10.15 crit. 1 y 2
        String cajero = usuario(3, PIN);
        long cajeroId = idDeUsuario(cajero);
        String sesion = login("/api/auth/login-pin", cajero, "pin", PIN);

        assertThat(enviar("/api/reportes/ventas?desde=2025-04-01&hasta=2025-04-10", sesion).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/reportes/filtros", sesion).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForList("""
                SELECT cambios->>'permisoRequerido' FROM bitacora WHERE accion = 'ACCESO_DENEGADO' AND usuario_id = ?""",
                String.class, cajeroId)).containsExactly("REPORTE_CONSULTAR", "REPORTE_CONSULTAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'REPORTE_GENERADO' AND usuario_id = ?",
                Integer.class, cajeroId)).isZero();
    }

    @Test
    void elAdministradorTambienConsultaLosReportes() throws Exception {   // HU-10.15 crit. 1
        String admin = login("/api/auth/login", "admin", "contrasena", ReportesIntegracionTest.CONTRASENA_ADMIN);
        assertThat(enviar("/api/reportes/ventas?desde=2025-04-01&hasta=2025-04-10", admin).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/reportes/filtros", admin).statusCode()).isEqualTo(200);
    }

    // Apoyo

    /** Una caja propia de la prueba, con un turno abierto de un cajero. */
    private final class Caja {
        final long id;
        final String nombre;
        final String cajero;
        final long cajeroId;
        final long turnoId;

        Caja(long id, String nombre, String cajero) {
            this.id = id;
            this.nombre = nombre;
            this.cajero = cajero;
            this.cajeroId = idDeUsuario(cajero);
            this.turnoId = jdbc.queryForObject("""
                    INSERT INTO turno (caja_id, usuario_id, estado, abierto_en, cerrado_en, cerrado_por,
                                       total_registrado, total_lote, diferencia)
                    VALUES (?, ?, 'CERRADO', '2025-01-01T12:00:00Z', '2025-01-01T13:00:00Z', ?, 0, 0, 0)
                    RETURNING id""", Long.class, id, cajeroId, cajeroId);
        }

        Caja conOtroCajero() {
            return new Caja(id, nombre, usuario(3, PIN));
        }

        /** Una venta con su total (IVA del 12 % incluido) y, si se indica, su factura en ese estado. */
        void venta(String estado, String finalizadaEn, String total, String estadoFactura) {
            BigDecimal monto = new BigDecimal(total);
            BigDecimal subtotal = monto.divide(new BigDecimal("1.12"), 2, java.math.RoundingMode.HALF_UP);
            long venta = jdbc.queryForObject("""
                    INSERT INTO venta (turno_id, caja_id, usuario_id, tasa_iva, estado, subtotal, iva, total,
                                       creada_en, finalizada_en)
                    VALUES (?, ?, ?, 0.12, ?, ?, ?, ?, ?::timestamptz, CASE WHEN ? = 'FINALIZADA' THEN ?::timestamptz END)
                    RETURNING id""", Long.class, turnoId, id, cajeroId, estado, subtotal, monto.subtract(subtotal),
                    monto, finalizadaEn, estado, finalizadaEn);
            if (estadoFactura != null) {
                boolean autorizada = !estadoFactura.equals("RECHAZADA");
                jdbc.update("""
                        INSERT INTO factura (venta_id, estado, receptor_tipo, receptor_id, receptor_nombre,
                                             emisor_snapshot, serie, numero, uuid_autorizacion, fecha_certificacion,
                                             xml_enviado)
                        VALUES (?, ?, 'CF', 'CF', 'Consumidor Final', '{}'::jsonb, ?, ?, ?, ?::timestamptz, '<dte/>')""",
                        venta, estadoFactura, autorizada ? "SERIE" : null, autorizada ? String.valueOf(venta) : null,
                        autorizada ? java.util.UUID.randomUUID() : null, autorizada ? finalizadaEn : null);
            }
        }
    }

    private Caja caja() {
        String nombre = "Caja R" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = jdbc.queryForObject("INSERT INTO caja (sucursal_id, nombre) VALUES (1, ?) RETURNING id", Long.class,
                nombre);
        return new Caja(id, nombre, usuario(3, PIN));
    }

    private static void assertFila(JsonNode fila, long cantidad, String total, String promedio) {
        assertThat(fila.get("cantidadVentas").asLong()).isEqualTo(cantidad);
        assertThat(fila.get("total").decimalValue()).isEqualByComparingTo(total);
        assertThat(fila.get("ticketPromedio").decimalValue()).isEqualByComparingTo(promedio);
    }

    private void assertError(HttpResponse<String> respuesta, String codigo) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(422);
        assertThat(respuesta.body()).contains(codigo);
    }

    private List<JsonNode> filas(JsonNode reporte) {
        List<JsonNode> filas = new ArrayList<>();
        reporte.get("filas").forEach(filas::add);
        return filas;
    }

    private JsonNode ok(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return enviar(ruta, supervisor);
    }

    private String usuario(int rolId, String credencial) {
        String username = "rep.u" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false)""", username, "Usuario " + username, rolId, codificador.encode(credencial));
        return username;
    }

    private long idDeUsuario(String username) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String usernameDe(long id) {
        return jdbc.queryForObject("SELECT username FROM usuario WHERE id = ?", String.class, id);
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor))).build();
        String setCookie = http.send(peticion, HttpResponse.BodyHandlers.ofString()).headers().firstValue("Set-Cookie")
                .orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviar(String ruta, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
