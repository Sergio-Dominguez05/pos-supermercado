package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.aplicacion.EmisionFacturas;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado.Escenario;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-08.03 Certificar la factura al finalizar la venta y HU-08.04 Evitar facturas duplicadas, contra PostgreSQL real
 * y el certificador simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CertificacionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CertificacionIntegracionTest {

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

    @Autowired
    EmisionFacturas emision;

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

    // Crit. 1, 2 y 4

    @Test
    void alFinalizarElCobroLaFacturaSeCertificaYGuardaSuAutorizacion() throws Exception {
        Cajero cajero = cajeroConVentaCobrada("Arroz certificado", "5.00", 3);   // Q15.00

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"factura\":{\"estado\":\"CERTIFICADA\"", "\"serie\":\"SIM\"",
                "\"motivo\":null");
        Map<String, Object> factura = facturaDe(cajero);
        assertThat(factura).containsEntry("estado", "CERTIFICADA").containsEntry("serie", "SIM")
                .containsEntry("con_autorizacion", true).containsEntry("con_numero", true)
                .containsEntry("con_fecha", true).containsEntry("receptor_id", "CF")
                .containsEntry("receptor_nombre", "Consumidor Final");
        assertThat(respuesta.body()).contains("\"autorizacion\":\"" + factura.get("autorizacion") + "\"",
                "\"numero\":\"" + factura.get("numero") + "\"");
        // Crit. 1: lo enviado es el DTE de la venta, con los montos guardados (HU-08.02).
        assertThat((String) factura.get("xml_enviado")).contains("Tipo=\"FACT\"", "Arroz certificado",
                "<dte:GranTotal>15.00</dte:GranTotal>");
        assertThat((String) factura.get("emisor")).contains("\"nit\": ", "\"afiliacionIva\": \"GEN\"", "\"codigoSat\": ");
        // La tarea de certificar quedó completada y la respuesta del certificador, guardada.
        assertThat(jdbc.queryForMap("""
                SELECT t.completada, t.intentos, i.exitoso, i.operacion
                FROM tarea_pendiente t JOIN fel_intento i ON i.factura_id = t.factura_id WHERE t.factura_id = ?""",
                factura.get("id"))).containsEntry("completada", true).containsEntry("intentos", 1)
                .containsEntry("exitoso", true).containsEntry("operacion", "CERTIFICAR");
        // Crit. 4: la factura es de la venta, y por ella de la caja, el turno y el usuario.
        assertThat(jdbc.queryForMap("""
                SELECT v.usuario_id, t.caja_id = v.caja_id AS de_la_caja, t.estado AS turno
                FROM factura f JOIN venta v ON v.id = f.venta_id JOIN turno t ON t.id = v.turno_id
                WHERE f.id = ?""", factura.get("id"))).containsEntry("usuario_id", cajero.usuarioId())
                .containsEntry("de_la_caja", true).containsEntry("turno", "ABIERTO");
    }

    /** Pendiente heredado de HU-03.02 crit. 5: cambiar el producto después no altera la factura emitida. */
    @Test
    void cambiarElProductoDespuesNoAlteraLaFacturaEmitida() throws Exception {
        Cajero cajero = cajeroConVentaCobrada("Frijol original", "8.50", 2);
        finalizar(cajero);
        String antes = (String) facturaDe(cajero).get("xml_enviado");

        assertThat(enviar("/api/productos/" + cajero.productoId(), "PUT",
                "{\"descripcion\":\"Frijol con otro nombre\",\"precio\":9.75}", cookieAdmin).statusCode())
                .isEqualTo(200);

        assertThat(facturaDe(cajero).get("xml_enviado")).isEqualTo(antes);
        assertThat(antes).contains("Frijol original", "<dte:PrecioUnitario>8.50</dte:PrecioUnitario>")
                .doesNotContain("Frijol con otro nombre", "9.75");
    }

    // Rechazo y contingencia: la venta ya está guardada en ambos casos

    @Test
    void siElCertificadorLaRechazaQuedaRechazadaConElMotivo() throws Exception {
        Cajero cajero = cajeroConVentaCobrada("Producto rechazado", "5.00", 1);
        simulador.usarEscenario(Escenario.RECHAZAR);

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"RECHAZADA\"", "El NIT o el nombre del cliente no es válido",
                "\"autorizacion\":null");
        assertThat(estadoDeLaVenta(cajero)).isEqualTo("FINALIZADA");
        Map<String, Object> factura = facturaDe(cajero);
        assertThat(factura).containsEntry("estado", "RECHAZADA").containsEntry("con_autorizacion", false);
        assertThat(jdbc.queryForMap("SELECT exitoso, codigo_error FROM fel_intento WHERE factura_id = ?",
                factura.get("id"))).containsEntry("exitoso", false).containsEntry("codigo_error", "SIM-001");
    }

    @Test
    void sinRespuestaDelCertificadorPasaAContingenciaSinPerderLaVenta() throws Exception {   // crit. 3
        Cajero cajero = cajeroConVentaCobrada("Producto en contingencia", "5.00", 1);
        simulador.usarEscenario(Escenario.SIN_CONEXION);

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"CONTINGENCIA\"", "se certificará después");
        assertThat(estadoDeLaVenta(cajero)).isEqualTo("FINALIZADA");
        Map<String, Object> factura = facturaDe(cajero);
        assertThat(factura).containsEntry("estado", "CONTINGENCIA").containsEntry("con_autorizacion", false);
        // La tarea sigue pendiente para reintentarla (HU-08.08).
        assertThat(jdbc.queryForMap("""
                SELECT completada, intentos, ultimo_error IS NOT NULL AS con_error, proximo_intento > now() AS despues
                FROM tarea_pendiente WHERE factura_id = ?""", factura.get("id"))).containsEntry("completada", false)
                .containsEntry("intentos", 1).containsEntry("con_error", true).containsEntry("despues", true);
    }

    @Test
    void unaVentaTieneUnaSolaFactura() throws Exception {   // HU-08.04 crit. 3
        Cajero cajero = cajeroConVentaCobrada("Producto único", "5.00", 1);
        finalizar(cajero);

        assertThat(finalizar(cajero).body()).contains("SIN_VENTA_EN_CURSO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isEqualTo(1);
    }

    // HU-08.04 Evitar facturas duplicadas

    @Test
    void elIdentificadorUnicoQueSeEnviaEsElDeLaVenta() throws Exception {   // crit. 1
        Cajero cajero = cajeroConVentaCobrada("Producto identificado", "5.00", 1);

        finalizar(cajero);

        assertThat(simulador.enviosDe(uuidDeLaVenta(cajero))).isEqualTo(1);
    }

    @Test
    void siSePierdeLaRespuestaElReintentoConsultaYNoVuelveACertificar() throws Exception {   // crit. 2
        Cajero cajero = cajeroConVentaCobrada("Producto con respuesta perdida", "5.00", 1);
        simulador.usarEscenario(Escenario.RESPUESTA_PERDIDA);
        assertThat(finalizar(cajero).body()).contains("\"estado\":\"CONTINGENCIA\"");
        UUID venta = uuidDeLaVenta(cajero);
        String certificadaEnElCertificador = simulador.consultarPorIdentificador(venta).orElseThrow().autorizacion()
                .toString();
        simulador.usarEscenario(Escenario.ACEPTAR);

        FacturaDeVenta reintento = emision.reintentar(ventaId(cajero), sesionDe(cajero));

        assertThat(reintento.estado()).isEqualTo("CERTIFICADA");
        assertThat(reintento.autorizacion()).hasToString(certificadaEnElCertificador);
        assertThat(simulador.enviosDe(venta)).isEqualTo(1);   // se consultó, no se reenvió
        assertThat(facturaDe(cajero)).containsEntry("estado", "CERTIFICADA")
                .containsEntry("autorizacion", certificadaEnElCertificador);
        assertThat(jdbc.queryForObject("SELECT completada FROM tarea_pendiente WHERE factura_id = ?", Boolean.class,
                facturaDe(cajero).get("id"))).isTrue();
    }

    @Test
    void siElCertificadorNoLaTieneElReintentoLaEnvia() throws Exception {   // crit. 2
        Cajero cajero = cajeroConVentaCobrada("Producto sin conexión", "5.00", 1);
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        finalizar(cajero);
        simulador.usarEscenario(Escenario.ACEPTAR);

        assertThat(emision.reintentar(ventaId(cajero), sesionDe(cajero)).estado()).isEqualTo("CERTIFICADA");
        assertThat(simulador.enviosDe(uuidDeLaVenta(cajero))).isEqualTo(1);
    }

    @Test
    void unaVentaConFacturaCertificadaNoSeVuelveAFacturar() throws Exception {   // crit. 3
        Cajero cajero = cajeroConVentaCobrada("Producto certificado una vez", "5.00", 1);
        finalizar(cajero);
        Map<String, Object> factura = facturaDe(cajero);

        FacturaDeVenta reintento = emision.reintentar(ventaId(cajero), sesionDe(cajero));

        assertThat(reintento.estado()).isEqualTo("CERTIFICADA");
        assertThat(reintento.autorizacion()).hasToString((String) factura.get("autorizacion"));
        assertThat(simulador.enviosDe(uuidDeLaVenta(cajero))).isEqualTo(1);
        // La base de datos tampoco lo permite: ni otra factura para la venta ni cambiar la certificada.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO factura (venta_id, receptor_tipo, receptor_id, receptor_nombre, emisor_snapshot, xml_enviado)
                VALUES (?, 'CF', 'CF', 'Consumidor Final', '{}', '<dte/>')""", ventaId(cajero)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE factura SET estado = 'PENDIENTE' WHERE id = ?", factura.get("id")))
                .hasMessageContaining("ya está certificada");
        assertThatThrownBy(() -> jdbc.update("UPDATE factura SET numero = '1' WHERE id = ?", factura.get("id")))
                .hasMessageContaining("ya está certificada");
        assertThat(facturaDe(cajero)).isEqualTo(factura);
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion, long productoId) {}

    /** La sesión del cajero, como la ve el servidor: la bitácora registra a su nombre un rechazo (HU-08.06). */
    private static SesionActiva sesionDe(Cajero cajero) {
        return new SesionActiva(0, cajero.usuarioId(), "cajero", "Cajero", Rol.CAJERO, "Cajero", false, Set.of());
    }

    private long ventaId(Cajero cajero) {
        return jdbc.queryForObject("SELECT id FROM venta WHERE usuario_id = ? ORDER BY id DESC LIMIT 1", Long.class,
                cajero.usuarioId());
    }

    private UUID uuidDeLaVenta(Cajero cajero) {
        return jdbc.queryForObject("SELECT uuid FROM venta WHERE usuario_id = ? ORDER BY id DESC LIMIT 1", UUID.class,
                cajero.usuarioId());
    }

    private Map<String, Object> facturaDe(Cajero cajero) {
        return jdbc.queryForMap("""
                SELECT f.id, f.estado, f.serie, f.numero, f.uuid_autorizacion::text AS autorizacion,
                       f.uuid_autorizacion IS NOT NULL AS con_autorizacion, f.numero IS NOT NULL AS con_numero,
                       f.fecha_certificacion IS NOT NULL AS con_fecha, f.receptor_id, f.receptor_nombre,
                       f.xml_enviado, f.emisor_snapshot::text AS emisor
                FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""", cajero.usuarioId());
    }

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    private String estadoDeLaVenta(Cajero cajero) {
        return jdbc.queryForObject("SELECT estado FROM venta WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
                String.class, cajero.usuarioId());
    }

    /** Un cajero con turno abierto y una venta en curso de un producto nuevo, ya pagada con tarjeta. */
    private Cajero cajeroConVentaCobrada(String descripcion, String precio, int cantidad) throws Exception {
        String nombre = "Caja F" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "fe.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de facturación', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);

        String codigo = "FE" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> creado = enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":%s}".formatted(codigo, descripcion, precio),
                cookieAdmin);
        assertThat(creado.statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST",
                "{\"codigo\":\"%s\",\"cantidad\":%d}".formatted(codigo, cantidad), sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST",
                "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}", sesion).statusCode()).isEqualTo(200);
        return new Cajero(usuarioId, sesion, idDe(creado));
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
