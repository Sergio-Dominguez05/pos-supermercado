package gt.supermercado.pos.facturacion;

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
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-08.13 Consultar el historial de facturas y HU-08.14 Consultar el detalle de una factura, contra PostgreSQL real.
 * Cada prueba usa su propia caja, con facturas en fechas pasadas, para no depender de las demás pruebas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + HistorialFacturasIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class HistorialFacturasIntegracionTest {

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
    void elHistorialMuestraLasFacturasDeLaMasRecienteALaMasAntigua() throws Exception {   // HU-08.13 crit. 1
        Caja caja = caja();
        long primera = caja.factura("2025-05-01T15:00:00Z", "CERTIFICADA", "112.00", "1234567", "Comercial Ñandú");
        long segunda = caja.factura("2025-05-02T15:00:00Z", "CONTINGENCIA", "22.40", "CF", "Consumidor Final");
        long tercera = caja.factura("2025-05-03T15:00:00Z", "ANULADA", "50.00", "CF", "Consumidor Final");

        JsonNode historial = ok(get("/api/facturas/historial?cajaId=" + caja.id));

        assertThat(historial.get("total").asLong()).isEqualTo(3);
        List<JsonNode> filas = filas(historial);
        assertThat(filas).extracting(f -> f.get("facturaId").asLong()).containsExactly(tercera, segunda, primera);
        JsonNode certificada = filas.get(2);
        assertThat(certificada.get("serie").asString()).isEqualTo("A1");
        assertThat(certificada.get("numero").asString()).isEqualTo(numeroDe(primera));
        assertThat(certificada.get("autorizacion").asString()).hasSize(36);
        assertThat(certificada.get("receptorId").asString()).isEqualTo("1234567");
        assertThat(certificada.get("receptorNombre").asString()).isEqualTo("Comercial Ñandú");
        assertThat(certificada.get("total").decimalValue()).isEqualByComparingTo("112.00");
        assertThat(certificada.get("estado").asString()).isEqualTo("CERTIFICADA");
        assertThat(certificada.get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(certificada.get("cajero").asString()).endsWith("(" + caja.cajero + ")");
        JsonNode contingencia = filas.get(1);
        assertThat(contingencia.get("estado").asString()).isEqualTo("CONTINGENCIA");
        assertThat(contingencia.get("numeroAcceso").asLong()).isPositive();
        assertThat(contingencia.get("autorizacion").isNull()).isTrue();
    }

    @Test
    void filtraPorFechaEstadoCajaCajeroYCliente() throws Exception {   // HU-08.13 crit. 2
        Caja caja = caja();
        caja.factura("2025-05-01T15:00:00Z", "CERTIFICADA", "112.00", "1234567", "Comercial Ñandú");
        caja.factura("2025-05-02T15:00:00Z", "ANULADA", "22.40", "CF", "Consumidor Final");
        Caja otro = caja.conOtroCajero();
        otro.factura("2025-05-03T15:00:00Z", "CERTIFICADA", "50.00", "CF", "Consumidor Final");
        String base = "/api/facturas/historial?cajaId=" + caja.id;

        assertThat(total(base + "&desde=2025-05-02&hasta=2025-05-02")).isEqualTo(1);
        assertThat(total(base + "&desde=2025-05-02")).isEqualTo(2);
        assertThat(total(base + "&estado=CERTIFICADA")).isEqualTo(2);
        assertThat(total(base + "&estado=ANULADA")).isEqualTo(1);
        assertThat(total(base + "&cajeroId=" + otro.cajeroId)).isEqualTo(1);
        assertThat(total(base + "&cliente=nandu")).isEqualTo(1);   // sin tildes ni mayúsculas
        assertThat(total(base + "&cliente=12345")).isEqualTo(1);   // por NIT
        assertThat(total("/api/facturas/historial?cajaId=999999999")).isZero();
    }

    @Test
    void buscaPorNumeroDeFacturaAutorizacionNumeroDeAccesoOVenta() throws Exception {   // HU-08.13 crit. 3
        Caja caja = caja();
        long certificada = caja.factura("2025-05-01T15:00:00Z", "CERTIFICADA", "112.00", "CF", "Consumidor Final");
        long contingencia = caja.factura("2025-05-02T15:00:00Z", "CONTINGENCIA", "22.40", "CF", "Consumidor Final");
        String autorizacion = jdbc.queryForObject("SELECT uuid_autorizacion::text FROM factura WHERE id = ?",
                String.class, certificada);
        long acceso = jdbc.queryForObject("SELECT numero_acceso_contingencia FROM factura WHERE id = ?", Long.class,
                contingencia);
        long venta = jdbc.queryForObject("SELECT v.numero FROM venta v JOIN factura f ON f.venta_id = v.id WHERE f.id = ?",
                Long.class, certificada);
        String base = "/api/facturas/historial?cajaId=" + caja.id + "&buscar=";

        assertThat(ids(base + numeroDe(certificada))).containsExactly(certificada);
        assertThat(ids(base + "A1-" + numeroDe(certificada))).containsExactly(certificada);
        assertThat(ids(base + autorizacion.substring(0, 8).toUpperCase())).containsExactly(certificada);
        assertThat(ids(base + acceso)).containsExactly(contingencia);
        assertThat(ids(base + venta)).contains(certificada);
    }

    @Test
    void paginaDeCincuentaEnCincuentaYRechazaLasFechasInvertidas() throws Exception {
        Caja caja = caja();
        for (int i = 0; i < 52; i++) {
            caja.factura("2025-06-01T15:%02d:00Z".formatted(i), "CERTIFICADA", "1.12", "CF", "Consumidor Final");
        }

        JsonNode primera = ok(get("/api/facturas/historial?cajaId=" + caja.id));
        JsonNode segunda = ok(get("/api/facturas/historial?cajaId=" + caja.id + "&pagina=1"));

        assertThat(primera.get("total").asLong()).isEqualTo(52);
        assertThat(primera.get("tamanoPagina").asInt()).isEqualTo(50);
        assertThat(filas(primera)).hasSize(50);
        assertThat(filas(segunda)).hasSize(2);
        HttpResponse<String> invertidas = get("/api/facturas/historial?desde=2025-06-02&hasta=2025-06-01");
        assertThat(invertidas.statusCode()).isEqualTo(422);
        assertThat(invertidas.body()).contains("FILTRO_FECHAS_INVERTIDAS");
    }

    @Test
    void elDetalleTraeEmisorReceptorLineasPagosYCertificacion() throws Exception {   // HU-08.14 crit. 1
        Caja caja = caja();
        long factura = caja.factura("2025-05-01T15:00:00Z", "CERTIFICADA", "33.60", "1234567", "Comercial Ñandú");
        long venta = ventaDe(factura);
        caja.linea(venta, "Arroz 1 lb", 2, "11.20", false, true);
        caja.linea(venta, "Frijol 1 lb", 1, "11.20", false, false);
        caja.linea(venta, "Línea quitada", 1, "5.00", true, true);                 // eliminada: no aparece
        caja.pago(venta, "DEBITO", "33.60", "AUT-1", false);
        caja.pago(venta, "CREDITO", "9.00", "AUT-X", true);                          // eliminado: no aparece

        JsonNode detalle = ok(get("/api/facturas/" + factura));

        assertThat(detalle.get("estado").asString()).isEqualTo("CERTIFICADA");
        assertThat(detalle.get("caja").asString()).isEqualTo(caja.nombre);
        assertThat(detalle.get("turnoAbierto").asBoolean()).isFalse();   // el turno de la prueba ya está cerrado
        JsonNode emisor = detalle.get("emisor");
        assertThat(emisor.get("nombreComercial").asString()).isEqualTo("Súper Prueba");
        assertThat(emisor.get("razonSocial").asString()).isEqualTo("Supermercados de Prueba, S.A.");
        assertThat(emisor.get("nit").asString()).isEqualTo("576937K");
        assertThat(emisor.get("establecimiento").asString()).isEqualTo("Matriz");
        assertThat(emisor.get("codigoEstablecimiento").asString()).isEqualTo("1");
        assertThat(detalle.get("receptor").get("tipo").asString()).isEqualTo("NIT");
        assertThat(detalle.get("receptor").get("nombre").asString()).isEqualTo("Comercial Ñandú");
        List<String> lineas = new ArrayList<>();
        detalle.get("lineas").forEach(l -> lineas.add(l.get("descripcion").asString() + " x" + l.get("cantidad").asInt()
                + " = " + l.get("subtotal").decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString()));
        assertThat(lineas).containsExactly("Arroz 1 lb x2 = 22.40", "Frijol 1 lb x1 = 11.20");   // HU-03.09 crit. 3
        assertThat(detalle.get("subtotal").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(detalle.get("iva").decimalValue()).isEqualByComparingTo("3.60");
        assertThat(detalle.get("total").decimalValue()).isEqualByComparingTo("33.60");
        assertThat(detalle.get("pagos")).hasSize(1);
        assertThat(detalle.get("pagos").get(0).get("autorizacion").asString()).isEqualTo("AUT-1");
        assertThat(detalle.get("pagos").get(0).get("ultimos4").asString()).isEqualTo("4242");
        JsonNode certificacion = detalle.get("certificacion");
        assertThat(certificacion.get("serie").asString()).isEqualTo("A1");
        assertThat(certificacion.get("numero").asString()).isEqualTo(numeroDe(factura));
        assertThat(certificacion.get("autorizacion").asString()).hasSize(36);
        assertThat(certificacion.get("certificadaEn").asString()).isNotBlank();
    }

    @Test
    void elDetalleMuestraElHistorialDeLaFacturaEnOrden() throws Exception {   // HU-08.14 crit. 2
        Caja caja = caja();
        long factura = caja.factura("2025-05-01T15:00:00Z", "ANULADA", "33.60", "CF", "Consumidor Final");
        caja.intento(factura, "CERTIFICAR", false, "SIN_RESPUESTA", "{\"error\":\"tiempo agotado\"}",
                "2025-05-01T15:00:05Z");
        caja.intento(factura, "CERTIFICAR", false, "2001", "{\"mensaje\":\"NIT del receptor no válido\"}",
                "2025-05-01T15:10:00Z");
        caja.intento(factura, "CONSULTAR", true, null, "{}", "2025-05-01T15:19:00Z");
        caja.intento(factura, "CERTIFICAR", true, null, "{}", "2025-05-01T15:20:00Z");
        caja.reimpresion(factura, "2025-05-01T16:00:00Z", supervisorId);
        caja.intento(factura, "ANULAR", false, "E1", "{\"mensaje\":\"Certificador ocupado\"}",
                "2025-05-01T16:59:00Z");
        caja.anulacion(factura, "2025-05-01T17:00:00Z", "Error de digitación", supervisorId);
        caja.intento(factura, "ANULAR", true, null, "{}", "2025-05-01T17:00:00Z");   // no se repite

        JsonNode detalle = ok(get("/api/facturas/" + factura));

        List<String> eventos = new ArrayList<>();
        detalle.get("historial").forEach(e -> eventos.add(e.get("tipo").asString()));
        assertThat(eventos).containsExactly("EMITIDA", "SIN_RESPUESTA", "RECHAZADA", "CONSULTA", "CERTIFICADA",
                "REIMPRESA", "ANULACION_RECHAZADA", "ANULADA");
        JsonNode historial = detalle.get("historial");
        assertThat(historial.get(0).get("descripcion").asString()).startsWith("Emitida con la venta N.º ");
        assertThat(historial.get(2).get("descripcion").asString()).startsWith("Rechazada por el certificador: ");
        assertThat(historial.get(5).get("descripcion").asString()).isEqualTo("Reimpresa en " + caja.nombre);
        assertThat(historial.get(5).get("usuario").asString()).endsWith("(" + usernameDe(supervisorId) + ")");
        assertThat(historial.get(6).get("descripcion").asString())
                .isEqualTo("El certificador no aceptó la anulación: Certificador ocupado");
        assertThat(historial.get(7).get("descripcion").asString()).isEqualTo("Anulada: Error de digitación");
    }

    @Test
    void soloElSupervisorYElAdministradorConsultanElHistorial() throws Exception {   // permiso de anular facturas
        Caja caja = caja();
        long factura = caja.factura("2025-05-01T15:00:00Z", "CERTIFICADA", "11.20", "CF", "Consumidor Final");
        String cajero = login("/api/auth/login-pin", caja.cajero, "pin", PIN);

        assertThat(enviar("/api/facturas/historial", cajero).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/facturas/historial/opciones", cajero).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/facturas/" + factura, cajero).statusCode()).isEqualTo(403);
        assertThat(get("/api/facturas/999999999").statusCode()).isEqualTo(404);
        String admin = login("/api/auth/login", "admin", "contrasena", CONTRASENA_ADMIN);
        assertThat(enviar("/api/facturas/" + factura, admin).statusCode()).isEqualTo(200);

        JsonNode opciones = ok(get("/api/facturas/historial/opciones"));
        List<String> cajas = new ArrayList<>();
        opciones.get("cajas").forEach(c -> cajas.add(c.get("etiqueta").asString()));
        List<String> cajeros = new ArrayList<>();
        opciones.get("cajeros").forEach(c -> cajeros.add(c.get("etiqueta").asString()));
        assertThat(cajas).contains(caja.nombre);
        assertThat(cajeros).anyMatch(c -> c.endsWith("(" + caja.cajero + ")"));
    }

    // Apoyo

    /** Una caja propia de la prueba, con un turno de un cajero. */
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

        /** Una venta finalizada con su factura en el estado indicado; devuelve el id de la factura. */
        long factura(String emitidaEn, String estado, String total, String receptorId, String receptorNombre) {
            BigDecimal monto = new BigDecimal(total);
            BigDecimal subtotal = monto.divide(new BigDecimal("1.12"), 2, RoundingMode.HALF_UP);
            long venta = jdbc.queryForObject("""
                    INSERT INTO venta (turno_id, caja_id, usuario_id, tasa_iva, estado, subtotal, iva, total,
                                       creada_en, finalizada_en)
                    VALUES (?, ?, ?, 0.12, 'FINALIZADA', ?, ?, ?, ?::timestamptz, ?::timestamptz) RETURNING id""",
                    Long.class, turnoId, id, cajeroId, subtotal, monto.subtract(subtotal), monto, emitidaEn,
                    emitidaEn);
            boolean autorizada = estado.equals("CERTIFICADA") || estado.equals("ANULADA");
            long factura = jdbc.queryForObject("""
                    INSERT INTO factura (venta_id, estado, receptor_tipo, receptor_id, receptor_nombre, emisor_snapshot,
                                         uuid_autorizacion, fecha_certificacion, numero_acceso_contingencia, emitida_en,
                                         xml_enviado, serie, numero)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::timestamptz, ?, ?::timestamptz, '<dte/>', ?, ?) RETURNING id""",
                    Long.class, venta, estado, receptorId.equals("CF") ? "CF" : "NIT", receptorId, receptorNombre,
                    """
                    {"nit":"576937K","razonSocial":"Supermercados de Prueba, S.A.","nombreComercial":"Súper Prueba",
                     "direccionFiscal":"Zona 1, Guatemala",
                     "establecimiento":{"codigoSat":"1","nombre":"Matriz","direccion":"Zona 1"}}""",
                    autorizada ? UUID.randomUUID() : null, autorizada ? emitidaEn : null,
                    estado.equals("CONTINGENCIA")
                            ? jdbc.queryForObject("SELECT nextval('numero_acceso_contingencia_seq')", Long.class) : null,
                    emitidaEn, autorizada ? "A1" : null, autorizada ? String.valueOf(venta) : null);
            return factura;
        }

        void linea(long venta, String descripcion, int cantidad, String precio, boolean eliminada, boolean activo) {
            long producto = jdbc.queryForObject("""
                    INSERT INTO producto (codigo_barras, descripcion, precio, activo) VALUES (?, ?, ?, ?)
                    RETURNING id""", Long.class, "HF-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000,
                    descripcion, new BigDecimal(precio), activo);
            BigDecimal subtotal = new BigDecimal(precio).multiply(BigDecimal.valueOf(cantidad));
            BigDecimal base = subtotal.divide(new BigDecimal("1.12"), 6, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario,
                                             subtotal_con_iva, base_imponible, iva, eliminada_en, eliminada_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? THEN now() END, CASE WHEN ? THEN ?::bigint END)""",
                    venta, producto, descripcion, cantidad, new BigDecimal(precio), subtotal, base,
                    subtotal.subtract(base), eliminada, eliminada, cajeroId);
        }

        void pago(long venta, String tipo, String monto, String autorizacion, boolean eliminado) {
            jdbc.update("""
                    INSERT INTO pago (venta_id, turno_id, usuario_id, tipo_tarjeta, autorizacion, ultimos4, monto,
                                      eliminado_en, eliminado_por)
                    VALUES (?, ?, ?, ?, ?, '4242', ?, CASE WHEN ? THEN now() END, CASE WHEN ? THEN ?::bigint END)""",
                    venta, turnoId, cajeroId, tipo, autorizacion, new BigDecimal(monto), eliminado, eliminado,
                    cajeroId);
        }

        void intento(long factura, String operacion, boolean exitoso, String codigo, String respuesta, String en) {
            jdbc.update("""
                    INSERT INTO fel_intento (factura_id, operacion, exitoso, codigo_error, respuesta, intentado_en)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::timestamptz)""", factura, operacion, exitoso, codigo, respuesta,
                    en);
        }

        void reimpresion(long factura, String en, long usuarioId) {
            jdbc.update("INSERT INTO reimpresion (factura_id, usuario_id, caja_id, impresa_en) VALUES (?, ?, ?, ?::timestamptz)",
                    factura, usuarioId, id, en);
        }

        void anulacion(long factura, String en, String motivo, long usuarioId) {
            jdbc.update("""
                    INSERT INTO anulacion (factura_id, motivo, usuario_id, turno_id, anulada_en, respuesta_fel)
                    VALUES (?, ?, ?, ?, ?::timestamptz, '{}'::jsonb)""", factura, motivo, usuarioId, turnoId, en);
        }
    }

    private Caja caja() {
        String nombre = "Caja H" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = jdbc.queryForObject("INSERT INTO caja (sucursal_id, nombre) VALUES (1, ?) RETURNING id", Long.class,
                nombre);
        return new Caja(id, nombre, usuario(3, PIN));
    }

    /** El número de la factura certificada: en estas pruebas, el id de su venta. */
    private String numeroDe(long factura) {
        return jdbc.queryForObject("SELECT numero FROM factura WHERE id = ?", String.class, factura);
    }

    private long ventaDe(long factura) {
        return jdbc.queryForObject("SELECT venta_id FROM factura WHERE id = ?", Long.class, factura);
    }

    private long total(String ruta) throws Exception {
        return ok(get(ruta)).get("total").asLong();
    }

    private List<Long> ids(String ruta) throws Exception {
        return filas(ok(get(ruta))).stream().map(f -> f.get("facturaId").asLong()).toList();
    }

    private List<JsonNode> filas(JsonNode historial) {
        List<JsonNode> filas = new ArrayList<>();
        historial.get("facturas").forEach(filas::add);
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
        String username = "hf.u" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 1_000_000;
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
