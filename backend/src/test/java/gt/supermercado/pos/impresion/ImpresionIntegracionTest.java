package gt.supermercado.pos.impresion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado.Envio;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-12.08 Configurar las impresoras y HU-08.05 Imprimir la factura, contra PostgreSQL real y la impresora simulada. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ImpresionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ImpresionIntegracionTest {

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

    @AfterEach
    void impresoraQueResponde() {
        impresoraSimulada.fallar(false);
    }

    // HU-12.08

    @Test
    void elAdministradorRegistraLaImpresoraConSuAnchoYSusCopias() throws Exception {   // crit. 1 y 3
        String host = host();
        HttpResponse<String> creada = enviar("/api/configuracion/impresoras", "POST", """
                {"nombre":"Térmica %s","host":"%s","anchoMm":58,"copiasFactura":2,"copiasCierre":3}"""
                .formatted(host, host), cookieAdmin);

        assertThat(creada.statusCode()).isEqualTo(201);
        assertThat(creada.body()).contains("\"host\":\"" + host + "\"", "\"puerto\":9100", "\"anchoMm\":58",
                "\"copiasFactura\":2", "\"copiasCierre\":3");
        long id = idDe(creada);
        assertThat(enviar("/api/configuracion/impresoras/" + id, "PUT", """
                {"nombre":"Térmica %s","host":"%s","puerto":9101,"anchoMm":80,"copiasFactura":1,"copiasCierre":1}"""
                .formatted(host, host), cookieAdmin).body()).contains("\"puerto\":9101", "\"anchoMm\":80");
        assertThat(enviar("/api/configuracion/impresoras", "GET", null, cookieAdmin).body()).contains(host);
        // El cambio queda en la bitácora con el valor anterior y el nuevo (HU-12.15 crit. 4).
        assertThat(jdbc.queryForObject("""
                SELECT cambios->'anchoMm'->>'despues' FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND entidad = 'impresora' AND entidad_id = ? AND exitoso
                ORDER BY id DESC LIMIT 1""", String.class, id)).isEqualTo("80");
    }

    @Test
    void losDatosDeLaImpresoraSeValidan() throws Exception {   // crit. 1 y 3
        String base = "{\"nombre\":\"Validada %s\",\"host\":\"%s\",\"anchoMm\":%d,\"copiasFactura\":%d}";
        String host = host();

        assertThat(crear(base.formatted(host, "http://192.168.1.5", 80, 1)).body()).contains("HOST_IMPRESORA_INVALIDO");
        assertThat(crear(base.formatted(host, host, 70, 1)).body()).contains("ANCHO_PAPEL_INVALIDO");
        assertThat(crear(base.formatted(host, host, 80, 6)).body()).contains("COPIAS_INVALIDAS");
        assertThat(crear(base.formatted(host, host, 80, 1)).statusCode()).isEqualTo(201);
        assertThat(crear(base.formatted(host, host, 80, 1)).body()).contains("IMPRESORA_DUPLICADA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND NOT exitoso AND descripcion LIKE 'Cambio de impresora%'""",
                Integer.class)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void cadaCajaEligeSuImpresora() throws Exception {   // crit. 1 y HU-12.05 crit. 2
        long impresora = impresora(host(), 80, 1);
        String nombre = "Caja I" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;

        HttpResponse<String> caja = enviar("/api/cajas", "POST",
                "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresora), cookieAdmin);

        assertThat(caja.statusCode()).isEqualTo(201);
        assertThat(caja.body()).contains("\"impresoraId\":" + impresora, "\"impresoraNombre\":\"Térmica");
        long id = idDe(caja);
        assertThat(enviar("/api/cajas/" + id, "PUT", "{\"nombre\":\"%s\",\"impresoraId\":999999}".formatted(nombre),
                cookieAdmin).body()).contains("IMPRESORA_NO_ENCONTRADA");
        assertThat(enviar("/api/cajas/" + id, "PUT", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin).body())
                .contains("\"impresoraId\":null");
    }

    @Test
    void laPaginaDePruebaSaleEnLaImpresora() throws Exception {   // crit. 2
        String host = host();
        long id = impresora(host, 58, 3);

        HttpResponse<String> prueba = enviar("/api/configuracion/impresoras/" + id + "/prueba", "POST", null,
                cookieAdmin);

        assertThat(prueba.statusCode()).isEqualTo(200);
        assertThat(prueba.body()).contains("\"exitosa\":true", "Página de prueba enviada");
        assertThat(texto(impresoraSimulada.ultimoA(host))).contains("PÁGINA DE PRUEBA", "58 mm (32 columnas)");
        impresoraSimulada.fallar(true);
        assertThat(enviar("/api/configuracion/impresoras/" + id + "/prueba", "POST", null, cookieAdmin).body())
                .contains("\"exitosa\":false", "no respondió", "Revise que esté encendida");
    }

    @Test
    void soloElAdministradorConfiguraLasImpresoras() throws Exception {
        Cajero cajero = cajeroConVentaCobrada(null);

        assertThat(enviar("/api/configuracion/impresoras", "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/configuracion/impresoras/1/prueba", "POST", null, cajero.sesion()).statusCode())
                .isEqualTo(403);
    }

    // HU-08.05

    @Test
    void alFinalizarLaFacturaSeImprimeConLasCopiasDeLaImpresoraDeLaCaja() throws Exception {   // crit. 1, 2 y 3
        String host = host();
        Cajero cajero = cajeroConVentaCobrada(impresora(host, 80, 2));

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"impresion\":{\"impresa\":true,\"mensaje\":null}");
        Envio envio = impresoraSimulada.ultimoA(host);
        String ticket = texto(envio);
        Map<String, Object> factura = jdbc.queryForMap("""
                SELECT f.serie, f.numero, upper(f.uuid_autorizacion::text) AS autorizacion
                FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""", cajero.usuarioId());
        assertThat(ticket).contains("FACTURA", (String) factura.get("serie"), (String) factura.get("numero"),
                (String) factura.get("autorizacion"), "NIT", "Consumidor Final", "Arroz impreso", "3 x Q5.00",
                "Tarjeta de débito ****4321", "Aut. 123456", "Sujeto a pagos trimestrales ISR", "Certificador");
        assertThat(ticket.indexOf("Subtotal")).isLessThan(ticket.indexOf("IVA ")).isPositive();
        assertThat(ticket.indexOf("IVA ")).isLessThan(ticket.indexOf("TOTAL"));
        assertThat(ticket.split("FACTURA", -1)).hasSize(3);   // dos copias (HU-12.08 crit. 3)
    }

    @Test
    void siLaImpresoraFallaLaFacturaSigueValidaYSePuedeReimprimir() throws Exception {   // crit. 6
        Cajero cajero = cajeroConVentaCobrada(impresora(host(), 80, 1));
        impresoraSimulada.fallar(true);

        HttpResponse<String> respuesta = finalizar(cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"CERTIFICADA\"", "\"impresa\":false", "no respondió",
                "se puede reimprimir");
        assertThat(jdbc.queryForMap("""
                SELECT v.estado AS venta, f.estado AS factura FROM venta v JOIN factura f ON f.venta_id = v.id
                WHERE v.usuario_id = ?""", cajero.usuarioId())).containsEntry("venta", "FINALIZADA")
                .containsEntry("factura", "CERTIFICADA");
    }

    @Test
    void unaCajaSinImpresoraLoAvisa() throws Exception {   // crit. 6
        HttpResponse<String> respuesta = finalizar(cajeroConVentaCobrada(null));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"impresa\":false", "no tiene impresora configurada",
                "se puede reimprimir");
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion) {}

    private static String host() {
        return "impresora-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000 + ".local";
    }

    private static String texto(Envio envio) {
        assertThat(envio).as("algo se envió a la impresora").isNotNull();
        return new String(envio.datos(), PAGINA_850);
    }

    private HttpResponse<String> crear(String cuerpo) throws Exception {
        return enviar("/api/configuracion/impresoras", "POST", cuerpo, cookieAdmin);
    }

    private long impresora(String host, int anchoMm, int copiasFactura) throws Exception {
        HttpResponse<String> creada = crear("{\"nombre\":\"Térmica %s\",\"host\":\"%s\",\"anchoMm\":%d,\"copiasFactura\":%d}"
                .formatted(host, host, anchoMm, copiasFactura));
        assertThat(creada.statusCode()).isEqualTo(201);
        return idDe(creada);
    }

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    /** Un cajero en una caja nueva (con la impresora indicada o sin impresora) y una venta pagada con tarjeta. */
    private Cajero cajeroConVentaCobrada(Long impresoraId) throws Exception {
        String nombre = "Caja I" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", impresoraId == null
                ? "{\"nombre\":\"%s\"}".formatted(nombre)
                : "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresoraId), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "im.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de impresión', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "IM" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Arroz impreso\",\"precio\":5.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\",\"cantidad\":3}".formatted(codigo),
                sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST",
                "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\",\"ultimos4\":\"4321\"}", sesion).statusCode())
                .isEqualTo(200);
        return new Cajero(usuarioId, sesion);
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
