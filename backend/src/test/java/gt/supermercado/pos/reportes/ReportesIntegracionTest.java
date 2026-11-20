package gt.supermercado.pos.reportes;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
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
    void resumePorCajeroLasVentasLasCanceladasLasLineasEliminadasYLasAnuladas() throws Exception {   // HU-10.03 crit. 1
        Caja caja = caja();
        long venta = caja.venta("FINALIZADA", "2025-06-02T15:00:00Z", "100.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-06-02T16:00:00Z", "50.00", null);
        caja.venta("FINALIZADA", "2025-06-03T16:00:00Z", "30.00", "ANULADA");
        caja.venta("CANCELADA", "2025-06-03T17:00:00Z", "20.00", null);       // por la fecha en que se inició
        caja.venta("CANCELADA", "2025-07-03T17:00:00Z", "20.00", null);       // fuera del período
        caja.lineaEliminada(venta, "2025-06-02T14:59:00Z");
        caja.lineaEliminada(venta, "2025-06-02T14:59:30Z");
        Caja otro = caja.conOtroCajero();
        otro.venta("CANCELADA", "2025-06-04T17:00:00Z", "20.00", null);   // solo tiene una cancelada: también aparece

        JsonNode reporte = ok(get("/api/reportes/cajeros?desde=2025-06-01&hasta=2025-06-30&cajaId=" + caja.id));

        List<JsonNode> filas = filas(reporte);
        assertThat(filas).hasSize(2);
        JsonNode primero = filas.stream().filter(f -> f.get("cajeroId").asLong() == caja.cajeroId).findFirst()
                .orElseThrow();
        assertThat(primero.get("cajero").asString()).isEqualTo("Usuario " + caja.cajero + " (" + caja.cajero + ")");
        assertThat(primero.get("cantidadVentas").asLong()).isEqualTo(2);
        assertThat(primero.get("totalVendido").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(primero.get("ventasCanceladas").asLong()).isEqualTo(1);
        assertThat(primero.get("lineasEliminadas").asLong()).isEqualTo(2);
        assertThat(primero.get("facturasAnuladas").asLong()).isEqualTo(1);
        assertThat(primero.get("montoAnulado").decimalValue()).isEqualByComparingTo("30.00");
        JsonNode totales = reporte.get("totales");
        assertThat(totales.get("cajero").asString()).isEqualTo("Total");
        assertThat(totales.get("cantidadVentas").asLong()).isEqualTo(2);
        assertThat(totales.get("ventasCanceladas").asLong()).isEqualTo(2);
        assertThat(reporte.get("encabezado").get("titulo").asString()).isEqualTo("Ventas por cajero");

        JsonNode soloOtro = ok(get("/api/reportes/cajeros?desde=2025-06-01&hasta=2025-06-30&cajaId=%d&cajeroId=%d"
                .formatted(caja.id, otro.cajeroId)));
        assertThat(filas(soloOtro)).extracting(f -> f.get("cajeroId").asLong()).containsExactly(otro.cajeroId);
    }

    @Test
    void agrupaLasVentasPorHoraDeGuatemalaYPromediaUnPeriodo() throws Exception {   // HU-10.11 crit. 1 y 2
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-05-05T14:10:00Z", "10.00", null);    // 08:10 en Guatemala
        caja.venta("FINALIZADA", "2025-05-05T14:50:00Z", "20.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-05-05T14:55:00Z", "99.00", "ANULADA"); // no se cuenta
        caja.venta("CANCELADA", "2025-05-05T15:00:00Z", "50.00", null);       // no es venta
        caja.venta("FINALIZADA", "2025-05-06T05:30:00Z", "30.00", null);     // 23:30 del 5 de mayo
        caja.venta("FINALIZADA", "2025-05-06T14:00:00Z", "40.00", null);     // 08:00 del 6 de mayo

        JsonNode dia = ok(get("/api/reportes/horas?desde=2025-05-05&hasta=2025-05-05&cajaId=" + caja.id));

        assertThat(dia.get("encabezado").get("titulo").asString()).isEqualTo("Ventas por hora");
        assertThat(dia.get("promedio").asBoolean()).isFalse();
        List<JsonNode> horas = filas(dia);
        assertThat(horas).hasSize(24);
        assertThat(horas.get(8).get("cantidadVentas").asLong()).isEqualTo(2);
        assertThat(horas.get(8).get("totalVendido").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(horas.get(23).get("cantidadVentas").asLong()).isEqualTo(1);
        assertThat(horas.get(9).get("cantidadVentas").asLong()).isZero();
        assertThat(dia.get("horaPico").asInt()).isEqualTo(8);
        assertThat(dia.get("totales").get("cantidadVentas").asLong()).isEqualTo(3);

        JsonNode periodo = ok(get("/api/reportes/horas?desde=2025-05-05&hasta=2025-05-06&cajaId=" + caja.id));
        assertThat(periodo.get("promedio").asBoolean()).isTrue();
        assertThat(periodo.get("dias").asLong()).isEqualTo(2);
        JsonNode ocho = filas(periodo).get(8);
        assertThat(ocho.get("cantidadVentas").asLong()).isEqualTo(3);
        assertThat(ocho.get("promedioVentas").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(ocho.get("promedioTotal").decimalValue()).isEqualByComparingTo("35.00");
    }

    @Test
    void resumePorCajaLasVentasElTotalYLosTurnos() throws Exception {   // HU-10.04 crit. 1
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-08-04T15:00:00Z", "100.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-08-05T15:00:00Z", "50.00", null);
        caja.venta("FINALIZADA", "2025-08-05T16:00:00Z", "999.00", "ANULADA");   // no se cuenta
        caja.venta("CANCELADA", "2025-08-05T17:00:00Z", "20.00", null);          // no es venta
        caja.venta("FINALIZADA", "2025-09-01T15:00:00Z", "70.00", null);         // fuera del período
        caja.cierre("2025-08-04T23:00:00Z", "100.00", "100.00", null);           // turno abierto el 4
        Caja otro = caja.conOtroCajero();
        otro.cierre("2025-08-05T23:00:00Z", "50.00", "50.00", null);
        otro.cierre("2025-09-01T23:00:00Z", "70.00", "70.00", null);              // fuera del período

        JsonNode reporte = ok(get("/api/reportes/cajas?desde=2025-08-01&hasta=2025-08-31&cajaId=" + caja.id));

        assertThat(reporte.get("encabezado").get("titulo").asString()).isEqualTo("Ventas por caja");
        List<JsonNode> filas = filas(reporte);
        assertThat(filas).hasSize(1);
        JsonNode fila = filas.getFirst();
        assertThat(fila.get("cajaId").asLong()).isEqualTo(caja.id);
        assertThat(fila.get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(fila.get("cantidadVentas").asLong()).isEqualTo(2);
        assertThat(fila.get("totalVendido").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(fila.get("turnos").asLong()).isEqualTo(2);
        assertThat(fila.get("ticketPromedio").decimalValue()).isEqualByComparingTo("75.00");
        assertThat(fila.get("participacion").decimalValue()).isEqualByComparingTo("100.0");

        JsonNode soloOtro = ok(get("/api/reportes/cajas?desde=2025-08-01&hasta=2025-08-31&cajaId=%d&cajeroId=%d"
                .formatted(caja.id, otro.cajeroId)));
        JsonNode filaOtro = filas(soloOtro).getFirst();
        assertThat(filaOtro.get("cantidadVentas").asLong()).isZero();
        assertThat(filaOtro.get("turnos").asLong()).isEqualTo(1);
        assertThat(filaOtro.get("participacion").decimalValue()).isEqualByComparingTo("0.0");
    }

    @Test
    void comparaLasCajasEnElMismoPeriodoDeLaQueMasVendioALaQueMenos() throws Exception {   // HU-10.04 crit. 2
        Caja poca = caja();
        poca.venta("FINALIZADA", "2024-02-10T15:00:00Z", "25.00", null);
        Caja mucha = caja();
        mucha.venta("FINALIZADA", "2024-02-10T16:00:00Z", "50.00", null);
        mucha.venta("FINALIZADA", "2024-02-11T16:00:00Z", "25.00", null);
        Caja sinVentas = caja();
        jdbc.update("UPDATE caja SET activo = false WHERE id = ?", sinVentas.id);   // inactiva y sin actividad: no sale

        JsonNode reporte = ok(get("/api/reportes/cajas?desde=2024-02-10&hasta=2024-02-11"));

        List<JsonNode> filas = filas(reporte);
        List<Long> ids = filas.stream().map(f -> f.get("cajaId").asLong()).toList();
        assertThat(ids).contains(mucha.id, poca.id).doesNotContain(sinVentas.id);
        assertThat(ids.indexOf(mucha.id)).isLessThan(ids.indexOf(poca.id));
        JsonNode totales = reporte.get("totales");
        assertThat(totales.get("caja").asString()).isEqualTo("Total");
        assertThat(totales.get("cantidadVentas").asLong()).isEqualTo(3);
        assertThat(totales.get("totalVendido").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(totales.get("participacion").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(filas.get(ids.indexOf(mucha.id)).get("participacion").decimalValue()).isEqualByComparingTo("75.0");
        assertThat(filas.get(ids.indexOf(poca.id)).get("participacion").decimalValue()).isEqualByComparingTo("25.0");
    }

    @Test
    void ordenaLosProductosMasVendidosPorCantidadOMontoSinLasAnuladas() throws Exception {   // HU-10.05 crit. 1, 2 y 4
        Caja caja = caja();
        long arroz = producto("Arroz del reporte", "11.20");
        long frijol = producto("Frijol del reporte", "5.00");
        long leche = producto("Leche del reporte", "50.00");
        long certificada = caja.venta("FINALIZADA", "2025-11-03T15:00:00Z", "43.60", "CERTIFICADA");
        caja.linea(certificada, arroz, 3, "11.20", null);
        caja.linea(certificada, frijol, 2, "5.00", null);
        long sinFactura = caja.venta("FINALIZADA", "2025-11-04T15:00:00Z", "91.20", null);
        caja.linea(sinFactura, arroz, 1, "11.20", null);
        caja.linea(sinFactura, frijol, 6, "5.00", null);
        caja.linea(sinFactura, leche, 1, "50.00", null);
        caja.linea(sinFactura, leche, 5, "50.00", "2025-11-04T14:59:00Z");                 // línea eliminada
        caja.linea(caja.venta("FINALIZADA", "2025-11-04T16:00:00Z", "500.00", "ANULADA"), leche, 10, "50.00", null);
        caja.linea(caja.venta("CANCELADA", "2025-11-04T17:00:00Z", "112.00", null), arroz, 10, "11.20", null);
        caja.linea(caja.venta("FINALIZADA", "2025-12-01T15:00:00Z", "500.00", null), frijol, 100, "5.00", null);

        String filtros = "desde=2025-11-01&hasta=2025-11-30&cajaId=" + caja.id;
        JsonNode porCantidad = ok(get("/api/reportes/productos?" + filtros));
        JsonNode porMonto = ok(get("/api/reportes/productos?ordenarPor=MONTO&" + filtros));

        assertThat(porCantidad.get("ordenarPor").asString()).isEqualTo("CANTIDAD");
        assertThat(porCantidad.get("limite").asInt()).isEqualTo(20);
        assertThat(porCantidad.get("productosVendidos").asLong()).isEqualTo(3);
        List<JsonNode> filas = filas(porCantidad);
        assertThat(filas).extracting(f -> f.get("productoId").asLong()).containsExactly(frijol, arroz, leche);
        assertThat(filas).extracting(f -> f.get("posicion").asInt()).containsExactly(1, 2, 3);
        assertThat(filas).extracting(f -> f.get("cantidad").asLong()).containsExactly(8L, 4L, 1L);
        assertThat(filas.get(1).get("descripcion").asString()).isEqualTo("Arroz del reporte");
        assertThat(filas.get(1).get("codigo").asString()).isNotBlank();
        assertThat(filas.get(1).get("monto").decimalValue()).isEqualByComparingTo("44.80");
        assertThat(porCantidad.get("totales").get("cantidad").asLong()).isEqualTo(13);
        assertThat(porCantidad.get("totales").get("monto").decimalValue()).isEqualByComparingTo("134.80");

        assertThat(filas(porMonto)).extracting(f -> f.get("productoId").asLong()).containsExactly(leche, arroz, frijol);
        List<String> aplicados = new ArrayList<>();
        porMonto.get("encabezado").get("filtros").forEach(f -> aplicados.add(f.get("etiqueta").asString() + ": "
                + f.get("valor").asString()));
        assertThat(aplicados).contains("Ordenado por: Monto vendido", "Mostrar: Los 20 primeros");
    }

    @Test
    void limitaLosProductosMasVendidosYRechazaUnLimiteInvalido() throws Exception {   // HU-10.05 crit. 3
        Caja caja = caja();
        long venta = caja.venta("FINALIZADA", "2025-11-10T15:00:00Z", "16.20", null);
        long primero = producto("Primero del reporte", "1.00");
        long segundo = producto("Segundo del reporte", "1.00");
        caja.linea(venta, primero, 9, "1.00", null);
        caja.linea(venta, segundo, 5, "1.00", null);
        caja.linea(venta, producto("Tercero del reporte", "1.00"), 2, "1.00", null);

        JsonNode reporte = ok(get("/api/reportes/productos?desde=2025-11-10&hasta=2025-11-10&limite=2&cajaId=" + caja.id));

        assertThat(filas(reporte)).extracting(f -> f.get("productoId").asLong()).containsExactly(primero, segundo);
        assertThat(reporte.get("productosVendidos").asLong()).isEqualTo(3);   // se vendieron 3; se muestran 2
        assertThat(reporte.get("totales").get("cantidad").asLong()).isEqualTo(14);
        assertError(get("/api/reportes/productos?desde=2025-11-10&hasta=2025-11-10&limite=0"), "FILTRO_LIMITE_INVALIDO");
        assertError(get("/api/reportes/exportar/PRODUCTOS?formato=PDF&desde=2025-11-10&hasta=2025-11-10&limite=501"),
                "FILTRO_LIMITE_INVALIDO");
        assertThat(get("/api/reportes/productos?desde=2025-11-10&hasta=2025-11-10&ordenarPor=PRECIO").statusCode())
                .isEqualTo(400);
    }

    @Test
    void elDetalleMuestraLasVentasDelCajeroEnOrden() throws Exception {   // HU-10.03 crit. 2
        Caja caja = caja();
        long certificada = caja.venta("FINALIZADA", "2025-06-02T15:00:00Z", "100.00", "CERTIFICADA");
        caja.venta("CANCELADA", "2025-06-02T12:00:00Z", "20.00", null);
        caja.venta("FINALIZADA", "2025-06-03T16:00:00Z", "30.00", "ANULADA");
        caja.lineaEliminada(certificada, "2025-06-02T14:59:00Z");
        caja.conOtroCajero().venta("FINALIZADA", "2025-06-02T15:30:00Z", "70.00", null);   // de otro cajero

        JsonNode detalle = ok(get("/api/reportes/cajeros/%d/ventas?desde=2025-06-01&hasta=2025-06-30&cajaId=%d"
                .formatted(caja.cajeroId, caja.id)));

        List<JsonNode> ventas = new ArrayList<>();
        detalle.get("ventas").forEach(ventas::add);
        assertThat(ventas).extracting(v -> v.get("estado").asString())
                .containsExactly("CANCELADA", "FINALIZADA", "FINALIZADA");
        assertThat(ventas.get(1).get("factura").asString()).isEqualTo("SERIE-" + certificada);
        assertThat(ventas.get(1).get("lineasEliminadas").asLong()).isEqualTo(1);
        assertThat(ventas.get(1).get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(ventas.get(2).get("estadoFactura").asString()).isEqualTo("ANULADA");
        List<String> filtros = new ArrayList<>();
        detalle.get("encabezado").get("filtros").forEach(f -> filtros.add(f.get("valor").asString()));
        assertThat(filtros).contains("Usuario " + caja.cajero + " (" + caja.cajero + ")");

        assertError(get("/api/reportes/cajeros/999999999/ventas?desde=2025-06-01&hasta=2025-06-30"),
                "FILTRO_CAJERO_INEXISTENTE");
        assertThat(jdbc.queryForList("""
                SELECT descripcion FROM bitacora WHERE accion = 'REPORTE_GENERADO' AND usuario_id = ?
                ORDER BY secuencia""", String.class, supervisorId)).hasSize(2)
                .first().asString().startsWith("Reporte \"Detalle de ventas del cajero\" generado");
    }

    @Test
    void sumaLosIngresosPorTipoDeTarjetaConLosAnuladosAparte() throws Exception {   // HU-10.06 crit. 1 y 2
        Caja caja = caja();
        long debito = caja.venta("FINALIZADA", "2025-08-04T15:00:00Z", "100.00", "CERTIFICADA");
        caja.pago(debito, "DEBITO", "100.00", "D-1", false);
        caja.pago(debito, "CREDITO", "55.00", "X-0", true);                      // eliminado: no cuenta
        long credito = caja.venta("FINALIZADA", "2025-08-05T15:00:00Z", "40.00", null);
        caja.pago(credito, "CREDITO", "40.00", "C-1", false);
        long anulada = caja.venta("FINALIZADA", "2025-08-05T16:00:00Z", "25.00", "ANULADA");
        caja.pago(anulada, "DEBITO", "25.00", "D-2", false);
        long cancelada = caja.venta("CANCELADA", "2025-08-05T17:00:00Z", "9.00", null);
        caja.pago(cancelada, "DEBITO", "9.00", "D-3", true);

        JsonNode reporte = ok(get("/api/reportes/tarjetas?desde=2025-08-01&hasta=2025-08-31&cajaId=" + caja.id));

        List<JsonNode> filas = filas(reporte);
        assertThat(filas).extracting(f -> f.get("tipo").asString()).containsExactly("DEBITO", "CREDITO");
        assertThat(filas.get(0).get("etiqueta").asString()).isEqualTo("Débito");
        assertThat(filas.get(0).get("transacciones").asLong()).isEqualTo(1);
        assertThat(filas.get(0).get("monto").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(filas.get(1).get("monto").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(reporte.get("total").get("transacciones").asLong()).isEqualTo(2);
        assertThat(reporte.get("total").get("monto").decimalValue()).isEqualByComparingTo("140.00");
        assertThat(reporte.get("anulados").get(0).get("monto").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(reporte.get("totalAnulado").get("transacciones").asLong()).isEqualTo(1);

        JsonNode soloCredito = ok(get("/api/reportes/tarjetas?desde=2025-08-01&hasta=2025-08-31&tipoTarjeta=CREDITO"
                + "&cajaId=" + caja.id));
        assertThat(filas(soloCredito)).extracting(f -> f.get("tipo").asString()).containsExactly("CREDITO");
        List<String> filtros = new ArrayList<>();
        soloCredito.get("encabezado").get("filtros").forEach(f -> filtros.add(f.get("etiqueta").asString() + ": "
                + f.get("valor").asString()));
        assertThat(filtros).contains("Tipo de tarjeta: Crédito");
    }

    @Test
    void elDetalleDeUnaTarjetaListaSusTransaccionesConLaAutorizacion() throws Exception {   // HU-10.06 crit. 3
        Caja caja = caja();
        long primera = caja.venta("FINALIZADA", "2025-08-04T15:00:00Z", "100.00", "CERTIFICADA");
        caja.pago(primera, "DEBITO", "100.00", "AUT-001", false);
        long anulada = caja.venta("FINALIZADA", "2025-08-05T16:00:00Z", "25.00", "ANULADA");
        caja.pago(anulada, "DEBITO", "25.00", "AUT-002", false);
        long credito = caja.venta("FINALIZADA", "2025-08-05T17:00:00Z", "40.00", null);
        caja.pago(credito, "CREDITO", "40.00", "AUT-003", false);

        JsonNode detalle = ok(get("/api/reportes/tarjetas/DEBITO/pagos?desde=2025-08-01&hasta=2025-08-31&cajaId="
                + caja.id));

        List<JsonNode> pagos = new ArrayList<>();
        detalle.get("pagos").forEach(pagos::add);
        assertThat(pagos).extracting(p -> p.get("autorizacion").asString()).containsExactly("AUT-001", "AUT-002");
        assertThat(pagos).extracting(p -> p.get("anulada").asBoolean()).containsExactly(false, true);
        assertThat(pagos.get(0).get("ultimos4").asString()).isEqualTo("4242");
        assertThat(pagos.get(0).get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(get("/api/reportes/tarjetas/EFECTIVO/pagos?desde=2025-08-01&hasta=2025-08-31").statusCode())
                .isEqualTo(400);
    }

    @Test
    void elIvaSoloSumaLasFacturasCertificadasYAvisaLasQueFaltan() throws Exception {   // HU-10.07 crit. 1, 2 y 4
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-09-01T15:00:00Z", "112.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-09-01T16:00:00Z", "56.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-09-02T15:00:00Z", "22.40", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-09-02T16:00:00Z", "999.00", "ANULADA");        // crit. 2
        caja.venta("FINALIZADA", "2025-09-02T17:00:00Z", "30.00", "CONTINGENCIA");    // aún no cuenta
        caja.venta("FINALIZADA", "2025-09-02T18:00:00Z", "11.20", "RECHAZADA");       // aún no cuenta

        JsonNode reporte = ok(get("/api/reportes/iva?desde=2025-09-01&hasta=2025-09-30&cajaId=" + caja.id));

        List<JsonNode> dias = new ArrayList<>();
        reporte.get("dias").forEach(dias::add);
        assertThat(dias).extracting(d -> d.get("fecha").asString()).containsExactly("2025-09-01", "2025-09-02");
        assertThat(dias.get(0).get("facturas").asLong()).isEqualTo(2);
        assertThat(dias.get(0).get("baseImponible").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(dias.get(0).get("iva").decimalValue()).isEqualByComparingTo("18.00");
        JsonNode total = reporte.get("total");
        assertThat(total.get("facturas").asLong()).isEqualTo(3);
        assertThat(total.get("baseImponible").decimalValue()).isEqualByComparingTo("170.00");
        assertThat(total.get("iva").decimalValue()).isEqualByComparingTo("20.40");
        assertThat(total.get("total").decimalValue()).isEqualByComparingTo("190.40");
        assertThat(reporte.get("sinCertificar").get("facturas").asLong()).isEqualTo(2);
        assertThat(reporte.get("sinCertificar").get("total").decimalValue()).isEqualByComparingTo("41.20");
    }

    @Test
    void elDetalleDelIvaListaCadaFacturaCertificada() throws Exception {   // HU-10.07 crit. 3
        Caja caja = caja();
        long primera = caja.venta("FINALIZADA", "2025-09-01T15:00:00Z", "112.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-09-02T16:00:00Z", "999.00", "ANULADA");
        long segunda = caja.venta("FINALIZADA", "2025-09-02T15:00:00Z", "22.40", "CERTIFICADA");

        JsonNode detalle = ok(get("/api/reportes/iva/facturas?desde=2025-09-01&hasta=2025-09-30&cajaId=" + caja.id));

        List<JsonNode> facturas = new ArrayList<>();
        detalle.get("facturas").forEach(facturas::add);
        assertThat(facturas).extracting(f -> f.get("numero").asString())
                .containsExactly(String.valueOf(primera), String.valueOf(segunda));
        assertThat(facturas.get(0).get("serie").asString()).isEqualTo("SERIE");
        assertThat(facturas.get(0).get("autorizacion").asString()).hasSize(36);
        assertThat(facturas.get(0).get("receptorNombre").asString()).isEqualTo("Consumidor Final");
        assertThat(facturas.get(0).get("baseImponible").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(facturas.get(0).get("iva").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(detalle.get("encabezado").get("titulo").asString()).isEqualTo("Detalle de IVA por factura");
    }

    @Test
    void listaLosCierresConSusDiferenciasYSumaFaltantesYSobrantes() throws Exception {   // HU-10.08 crit. 1 y 2
        Caja caja = caja();
        caja.cierre("2025-10-01T23:00:00Z", "500.00", "500.00", null);
        caja.cierre("2025-10-02T23:00:00Z", "300.00", "290.50", "Voucher extraviado");      // faltante
        caja.cierre("2025-10-03T23:00:00Z", "200.00", "205.00", "Cobro duplicado en la terminal");   // sobrante
        caja.cierre("2025-11-01T23:00:00Z", "100.00", "90.00", "Fuera del período");

        JsonNode reporte = ok(get("/api/reportes/cierres?desde=2025-10-01&hasta=2025-10-31&cajaId=" + caja.id));

        List<JsonNode> cierres = new ArrayList<>();
        reporte.get("cierres").forEach(cierres::add);
        assertThat(cierres).hasSize(3);
        JsonNode faltante = cierres.get(1);
        assertThat(faltante.get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(faltante.get("cajero").asString()).isEqualTo("Usuario " + caja.cajero + " (" + caja.cajero + ")");
        assertThat(faltante.get("totalRegistrado").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(faltante.get("totalLote").decimalValue()).isEqualByComparingTo("290.50");
        assertThat(faltante.get("diferencia").decimalValue()).isEqualByComparingTo("-9.50");
        assertThat(faltante.get("justificacion").asString()).isEqualTo("Voucher extraviado");
        assertThat(reporte.get("cierresConDiferencia").asLong()).isEqualTo(2);
        assertThat(reporte.get("totalFaltantes").decimalValue()).isEqualByComparingTo("9.50");
        assertThat(reporte.get("totalSobrantes").decimalValue()).isEqualByComparingTo("5.00");
    }

    @Test
    void puedeMostrarSoloLosCierresConDiferencias() throws Exception {   // HU-10.08 crit. 3
        Caja caja = caja();
        caja.cierre("2025-10-01T23:00:00Z", "500.00", "500.00", null);
        caja.cierre("2025-10-02T23:00:00Z", "300.00", "290.50", "Voucher extraviado");

        JsonNode reporte = ok(get("/api/reportes/cierres?desde=2025-10-01&hasta=2025-10-31&soloConDiferencias=true"
                + "&cajaId=" + caja.id));

        List<JsonNode> cierres = new ArrayList<>();
        reporte.get("cierres").forEach(cierres::add);
        assertThat(cierres).extracting(c -> c.get("justificacion").asString()).containsExactly("Voucher extraviado");
        List<String> filtros = new ArrayList<>();
        reporte.get("encabezado").get("filtros").forEach(f -> filtros.add(f.get("etiqueta").asString() + ": "
                + f.get("valor").asString()));
        assertThat(filtros).contains("Cierres: Solo con diferencias");
    }

    @Test
    void listaLasFacturasAnuladasEnElPeriodoConQuienLasEmitioYQuienLasAnulo() throws Exception {   // HU-10.09
        Caja caja = caja();
        long anuladaPorSupervisor = caja.venta("FINALIZADA", "2025-10-05T15:00:00Z", "45.00", "ANULADA");
        caja.anular(anuladaPorSupervisor, "2025-10-05T16:00:00Z", "Error de digitación", supervisorId);
        String otro = usuario(2, CONTRASENA);
        long otroId = idDeUsuario(otro);
        long anuladaPorOtro = caja.venta("FINALIZADA", "2025-10-06T15:00:00Z", "30.00", "ANULADA");
        caja.anular(anuladaPorOtro, "2025-10-07T15:00:00Z", "Cliente devolvió todo", otroId);
        long fuera = caja.venta("FINALIZADA", "2025-09-30T15:00:00Z", "10.00", "ANULADA");   // emitida antes,
        caja.anular(fuera, "2025-10-01T15:00:00Z", "Anulada el 1 de octubre", supervisorId);    // anulada en el período
        caja.venta("FINALIZADA", "2025-10-05T17:00:00Z", "99.00", "CERTIFICADA");

        JsonNode reporte = ok(get("/api/reportes/anuladas?desde=2025-10-01&hasta=2025-10-31&cajaId=" + caja.id));

        List<JsonNode> facturas = new ArrayList<>();
        reporte.get("facturas").forEach(facturas::add);
        assertThat(facturas).extracting(f -> f.get("motivo").asString()).containsExactly("Anulada el 1 de octubre",
                "Error de digitación", "Cliente devolvió todo");
        JsonNode primera = facturas.get(1);
        assertThat(primera.get("serie").asString()).isEqualTo("SERIE");
        assertThat(primera.get("numero").asString()).isEqualTo(String.valueOf(anuladaPorSupervisor));
        assertThat(primera.get("monto").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(primera.get("cajero").asString()).isEqualTo("Usuario " + caja.cajero + " (" + caja.cajero + ")");
        assertThat(primera.get("anuladaPor").asString()).endsWith("(" + usernameDe(supervisorId) + ")");
        assertThat(primera.get("emitidaEn").asString()).isNotBlank();
        assertThat(primera.get("anuladaEn").asString()).isNotBlank();
        assertThat(reporte.get("cantidad").asLong()).isEqualTo(3);
        assertThat(reporte.get("monto").decimalValue()).isEqualByComparingTo("85.00");

        JsonNode delOtro = ok(get("/api/reportes/anuladas?desde=2025-10-01&hasta=2025-10-31&anuladaPorId=%d&cajaId=%d"
                .formatted(otroId, caja.id)));
        assertThat(delOtro.get("cantidad").asLong()).isEqualTo(1);
        List<String> filtros = new ArrayList<>();
        delOtro.get("encabezado").get("filtros").forEach(f -> filtros.add(f.get("etiqueta").asString() + ": "
                + f.get("valor").asString()));
        assertThat(filtros).contains("Anuladas: Del 01/10/2025 al 31/10/2025",
                "Anulada por: Usuario " + otro + " (" + otro + ")");
        assertError(get("/api/reportes/anuladas?desde=2025-10-01&hasta=2025-10-31&anuladaPorId=999999999"),
                "FILTRO_ANULADOR_INEXISTENTE");

        JsonNode opciones = ok(get("/api/reportes/filtros"));
        List<String> anuladores = new ArrayList<>();
        opciones.get("anuladores").forEach(a -> anuladores.add(a.get("etiqueta").asString()));
        assertThat(anuladores).contains("Usuario " + otro + " (" + otro + ")").noneMatch(a -> a.endsWith(
                "(" + caja.cajero + ")"));
    }

    @Test
    void exportaAPdfConLaEmpresaLosFiltrosYLosMismosTotales() throws Exception {   // HU-10.13 crit. 1 a 3
        FacturacionDePrueba.configurarEmisorYEstablecimiento(jdbc);
        Caja caja = caja();
        caja.venta("FINALIZADA", "2025-03-03T15:00:00Z", "100.00", null);
        caja.venta("FINALIZADA", "2025-03-04T15:00:00Z", "11.20", null);

        HttpResponse<byte[]> respuesta = bytes("/api/reportes/exportar/VENTAS_PERIODO?formato=PDF&desde=2025-03-03"
                + "&hasta=2025-03-04&cajaId=" + caja.id);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(respuesta.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment", "ventas-por-periodo_2025-03-03_2025-03-04.pdf");
        assertThat(new String(respuesta.body(), 0, 5)).isEqualTo("%PDF-");
        String texto = new PdfTextExtractor(new PdfReader(respuesta.body())).getTextFromPage(1);
        var emisor = jdbc.queryForMap("SELECT nombre_comercial, razon_social, nit FROM emisor WHERE id = 1");
        assertThat(texto).contains((String) emisor.get("nombre_comercial"), (String) emisor.get("razon_social"),
                "NIT " + emisor.get("nit"));   // crit. 2
        assertThat(texto).contains("Ventas por período", "Período: Del 03/03/2025 al 04/03/2025",
                "Caja: " + caja.nombre, "Agrupación: Día", "Generado el");
        assertThat(texto).contains("03/03/2025", "04/03/2025", "Q100.00", "Q11.20", "Q111.20", "Q55.60");   // crit. 3
        assertThat(texto.indexOf("03/03/2025 1")).isLessThan(texto.indexOf("04/03/2025 1"));

        var registro = jdbc.queryForMap("""
                SELECT descripcion, cambios->>'formato' AS formato FROM bitacora
                WHERE accion = 'REPORTE_EXPORTADO' AND exitoso AND usuario_id = ? ORDER BY secuencia DESC LIMIT 1""",
                supervisorId);
        assertThat(registro.get("formato")).isEqualTo("PDF");
        assertThat((String) registro.get("descripcion")).startsWith("Reporte \"Ventas por período\" exportado a PDF");
    }

    @Test
    void exportaAExcelConNumerosYFechasDeVerdad() throws Exception {   // HU-10.14 crit. 1 a 3
        Caja caja = caja();
        long venta = caja.venta("FINALIZADA", "2025-09-01T15:00:00Z", "112.00", "CERTIFICADA");
        caja.venta("FINALIZADA", "2025-09-02T15:00:00Z", "22.40", "CERTIFICADA");

        HttpResponse<byte[]> respuesta = bytes("/api/reportes/exportar/DETALLE_IVA?formato=XLSX&desde=2025-09-01"
                + "&hasta=2025-09-30&cajaId=" + caja.id);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("iva-por-factura_2025-09-01_2025-09-30.xlsx");
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.body()))) {
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formato = new DataFormatter();
            List<String> textos = new ArrayList<>();
            hoja.forEach(fila -> fila.forEach(celda -> textos.add(formato.formatCellValue(celda))));
            assertThat(textos).contains("Detalle de IVA por factura", "Período", "Del 01/09/2025 al 30/09/2025",
                    "Caja", caja.nombre);   // crit. 3
            Row titulos = buscarFila(hoja, "Emitida");
            Row primera = hoja.getRow(titulos.getRowNum() + 1);
            Cell emitida = primera.getCell(0);
            assertThat(emitida.getCellType()).isEqualTo(CellType.NUMERIC);   // crit. 2: fecha de verdad
            assertThat(DateUtil.isCellDateFormatted(emitida)).isTrue();
            assertThat(emitida.getLocalDateTimeCellValue()).isEqualTo("2025-09-01T09:00");   // hora de Guatemala
            assertThat(primera.getCell(1).getStringCellValue()).isEqualTo("SERIE-" + venta);
            Cell total = primera.getCell(8);
            assertThat(total.getCellType()).isEqualTo(CellType.NUMERIC);    // crit. 2: monto como número
            assertThat(total.getNumericCellValue()).isEqualTo(112.00);
            assertThat(primera.getCell(6).getNumericCellValue()).isEqualTo(100.00);
            assertThat(hoja.getRow(titulos.getRowNum() + 2).getCell(8).getNumericCellValue()).isEqualTo(22.40);
        }
    }

    @Test
    void exportaCadaReporteYDetalleEnLosDosFormatos() throws Exception {   // HU-10.13 crit. 1, HU-10.14 crit. 1
        Caja caja = caja();
        long venta = caja.venta("FINALIZADA", "2025-10-05T15:00:00Z", "45.00", "ANULADA");
        caja.pago(venta, "DEBITO", "45.00", "AUT-9", false);
        caja.anular(venta, "2025-10-05T16:00:00Z", "Error de digitación", supervisorId);
        caja.cierre("2025-10-05T23:00:00Z", "45.00", "40.00", "Faltó un voucher");
        String filtros = "&desde=2025-10-01&hasta=2025-10-31&cajaId=" + caja.id;
        List<String> rutas = List.of("VENTAS_PERIODO?agrupacion=SEMANA", "VENTAS_CAJERO?x=1", "VENTAS_CAJA?x=1", "VENTAS_HORA?x=1",
                "PRODUCTOS?ordenarPor=MONTO&limite=50",
                "DETALLE_CAJERO?cajeroId=" + caja.cajeroId, "TARJETAS?tipoTarjeta=DEBITO",
                "DETALLE_TARJETA?tipoTarjeta=DEBITO", "IVA?x=1", "DETALLE_IVA?x=1", "CIERRES?soloConDiferencias=true",
                "ANULADAS?anuladaPorId=" + supervisorId);

        for (String ruta : rutas) {
            for (String formato : List.of("PDF", "XLSX")) {
                HttpResponse<byte[]> respuesta = bytes("/api/reportes/exportar/" + ruta + "&formato=" + formato + filtros);
                assertThat(respuesta.statusCode()).as(ruta + " " + formato).isEqualTo(200);
                assertThat(respuesta.body().length).as(ruta + " " + formato).isGreaterThan(500);
            }
        }
        String cierres = new PdfTextExtractor(new PdfReader(bytes("/api/reportes/exportar/CIERRES?formato=PDF"
                + filtros).body())).getTextFromPage(1);
        assertThat(cierres).contains("Total de faltantes: Q5.00", "-Q5.00", "Faltó un voucher");
        String anuladas = new PdfTextExtractor(new PdfReader(bytes("/api/reportes/exportar/ANULADAS?formato=PDF"
                + filtros).body())).getTextFromPage(1);
        assertThat(anuladas).contains("Error de digitación", "1 factura anulada", "Q45.00");
    }

    @Test
    void laExportacionRechazaLosFiltrosInvalidosYQuedaEnLaBitacora() throws Exception {   // HU-10.15 crit. 3
        assertError(get("/api/reportes/exportar/VENTAS_PERIODO?formato=PDF&desde=2025-04-10&hasta=2025-04-01"),
                "FILTRO_FECHAS_INVERTIDAS");
        assertError(get("/api/reportes/exportar/DETALLE_CAJERO?formato=XLSX&desde=2025-04-01&hasta=2025-04-10"),
                "FILTRO_DETALLE_INCOMPLETO");
        assertThat(get("/api/reportes/exportar/VENTAS_PERIODO?formato=CSV&desde=2025-04-01&hasta=2025-04-10")
                .statusCode()).isEqualTo(400);
        assertThat(get("/api/reportes/exportar/INVENTARIO?formato=PDF&desde=2025-04-01&hasta=2025-04-10")
                .statusCode()).isEqualTo(400);

        assertThat(jdbc.queryForList("""
                SELECT descripcion FROM bitacora WHERE accion = 'REPORTE_EXPORTADO' AND NOT exitoso AND usuario_id = ?
                ORDER BY secuencia""", String.class, supervisorId)).containsExactly(
                "Exportación a PDF del reporte \"Ventas por período\" rechazada: La fecha final no puede ser anterior a "
                        + "la fecha inicial.",
                "Exportación a XLSX del reporte \"Detalle de ventas del cajero\" rechazada: Indique el cajero del "
                        + "detalle.");
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
        assertThat(enviar("/api/reportes/cajeros?desde=2025-04-01&hasta=2025-04-10", sesion).statusCode())
                .isEqualTo(403);
        assertThat(enviar("/api/reportes/cajeros/%d/ventas?desde=2025-04-01&hasta=2025-04-10".formatted(cajeroId),
                sesion).statusCode()).isEqualTo(403);
        for (String ruta : List.of("/api/reportes/tarjetas", "/api/reportes/tarjetas/DEBITO/pagos", "/api/reportes/iva",
                "/api/reportes/iva/facturas", "/api/reportes/cierres", "/api/reportes/anuladas",
                "/api/reportes/exportar/VENTAS_PERIODO")) {
            assertThat(enviar(ruta + "?formato=PDF&desde=2025-04-01&hasta=2025-04-10", sesion).statusCode()).as(ruta)
                    .isEqualTo(403);
        }
        assertThat(jdbc.queryForList("""
                SELECT cambios->>'permisoRequerido' FROM bitacora WHERE accion = 'ACCESO_DENEGADO' AND usuario_id = ?""",
                String.class, cajeroId)).hasSize(11).containsOnly("REPORTE_CONSULTAR");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion IN ('REPORTE_GENERADO', 'REPORTE_EXPORTADO')
                AND usuario_id = ?""", Integer.class, cajeroId)).isZero();
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
        long venta(String estado, String finalizadaEn, String total, String estadoFactura) {
            BigDecimal monto = new BigDecimal(total);
            BigDecimal subtotal = monto.divide(new BigDecimal("1.12"), 2, java.math.RoundingMode.HALF_UP);
            long venta = jdbc.queryForObject("""
                    INSERT INTO venta (turno_id, caja_id, usuario_id, tasa_iva, estado, subtotal, iva, total,
                                       creada_en, finalizada_en)
                    VALUES (?, ?, ?, 0.12, ?, ?, ?, ?, ?::timestamptz, CASE WHEN ? = 'FINALIZADA' THEN ?::timestamptz END)
                    RETURNING id""", Long.class, turnoId, id, cajeroId, estado, subtotal, monto.subtract(subtotal),
                    monto, finalizadaEn, estado, finalizadaEn);
            if (estadoFactura != null) {
                boolean autorizada = estadoFactura.equals("CERTIFICADA") || estadoFactura.equals("ANULADA");
                jdbc.update("""
                        INSERT INTO factura (venta_id, estado, receptor_tipo, receptor_id, receptor_nombre,
                                             emisor_snapshot, serie, numero, uuid_autorizacion, fecha_certificacion,
                                             xml_enviado, emitida_en)
                        VALUES (?, ?, 'CF', 'CF', 'Consumidor Final', '{}'::jsonb, ?, ?, ?, ?::timestamptz, '<dte/>',
                                ?::timestamptz)""",
                        venta, estadoFactura, autorizada ? "SERIE" : null, autorizada ? String.valueOf(venta) : null,
                        autorizada ? java.util.UUID.randomUUID() : null, autorizada ? finalizadaEn : null,
                        finalizadaEn);
            }
            return venta;
        }

        /** Un pago con tarjeta de la venta; {@code eliminado} si se quitó antes de finalizar (HU-07.06). */
        void pago(long venta, String tipo, String monto, String autorizacion, boolean eliminado) {
            jdbc.update("""
                    INSERT INTO pago (venta_id, turno_id, usuario_id, tipo_tarjeta, autorizacion, ultimos4, monto,
                                      eliminado_en, eliminado_por)
                    VALUES (?, ?, ?, ?, ?, '4242', ?, CASE WHEN ? THEN now() END, CASE WHEN ? THEN ?::bigint END)""",
                    venta, turnoId, cajeroId, tipo, autorizacion, new BigDecimal(monto), eliminado, eliminado,
                    cajeroId);
        }

        /** Un turno cerrado de este cajero en esta caja, con sus totales y su justificación. */
        long cierre(String cerradoEn, String registrado, String lote, String justificacion) {
            BigDecimal diferencia = new BigDecimal(lote).subtract(new BigDecimal(registrado));
            return jdbc.queryForObject("""
                    INSERT INTO turno (caja_id, usuario_id, estado, abierto_en, cerrado_en, cerrado_por,
                                       total_registrado, total_lote, diferencia, justificacion)
                    VALUES (?, ?, 'CERRADO', ?::timestamptz - interval '8 hours', ?::timestamptz, ?, ?, ?, ?, ?)
                    RETURNING id""", Long.class, id, cajeroId, cerradoEn, cerradoEn, cajeroId,
                    new BigDecimal(registrado), new BigDecimal(lote), diferencia, justificacion);
        }

        /** Anula la factura de la venta: la anulación queda a nombre de {@code usuarioId}. */
        void anular(long venta, String anuladaEn, String motivo, long usuarioId) {
            jdbc.update("""
                    INSERT INTO anulacion (factura_id, motivo, usuario_id, turno_id, anulada_en, respuesta_fel)
                    SELECT f.id, ?, ?, ?, ?::timestamptz, '{}'::jsonb FROM factura f WHERE f.venta_id = ?""",
                    motivo, usuarioId, turnoId, anuladaEn, venta);
        }

        /** Una línea de la venta; si {@code eliminadaEn} no es nulo, se eliminó en ese momento (HU-06.07). */
        void linea(long venta, long producto, int cantidad, String precio, String eliminadaEn) {
            BigDecimal subtotal = new BigDecimal(precio).multiply(BigDecimal.valueOf(cantidad));
            BigDecimal base = subtotal.divide(new BigDecimal("1.12"), 6, java.math.RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario,
                                             subtotal_con_iva, base_imponible, iva, eliminada_en, eliminada_por)
                    VALUES (?, ?, 'Producto del reporte', ?, ?, ?, ?, ?, ?::timestamptz, ?)""",
                    venta, producto, cantidad, new BigDecimal(precio), subtotal, base, subtotal.subtract(base),
                    eliminadaEn, eliminadaEn == null ? null : cajeroId);
        }

        /** Una línea eliminada de la venta en el momento indicado. */
        void lineaEliminada(long venta, String eliminadaEn) {
            long producto = jdbc.queryForObject("""
                    INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, 'Producto del reporte', 11.20)
                    RETURNING id""", Long.class, "REP-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000);
            jdbc.update("""
                    INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario,
                                             subtotal_con_iva, base_imponible, iva, eliminada_en, eliminada_por)
                    VALUES (?, ?, 'Producto del reporte', 1, 11.20, 11.20, 10.00, 1.20, ?::timestamptz, ?)""",
                    venta, producto, eliminadaEn, cajeroId);
        }
    }

    private Caja caja() {
        String nombre = "Caja R" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = jdbc.queryForObject("INSERT INTO caja (sucursal_id, nombre) VALUES (1, ?) RETURNING id", Long.class,
                nombre);
        return new Caja(id, nombre, usuario(3, PIN));
    }

    /** Un producto propio de la prueba, con un código de barras que no se repite. */
    private long producto(String descripcion, String precio) {
        return jdbc.queryForObject("""
                INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, ?, ?) RETURNING id""", Long.class,
                "PMV-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000, descripcion,
                new BigDecimal(precio));
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

    private HttpResponse<byte[]> bytes(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Cookie", supervisor).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Row buscarFila(Sheet hoja, String primeraCelda) {
        for (Row fila : hoja) {
            Cell celda = fila.getCell(0);
            if (celda != null && celda.getCellType() == CellType.STRING
                    && celda.getStringCellValue().equals(primeraCelda)) {
                return fila;
            }
        }
        throw new AssertionError("No se encontró la fila " + primeraCelda);
    }

    private HttpResponse<String> enviar(String ruta, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
