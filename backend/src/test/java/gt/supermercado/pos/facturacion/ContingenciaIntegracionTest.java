package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias;
import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias.Ronda;
import gt.supermercado.pos.facturacion.aplicacion.EmisionFacturas;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado.Escenario;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado;
import gt.supermercado.pos.seguridad.aplicacion.SesionDelSistema;
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

/**
 * HU-08.07 Emitir comprobantes en contingencia, HU-08.08 Certificarlos y HU-08.09 Avisar los pendientes, contra
 * PostgreSQL real, el certificador y la impresora simulados.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ContingenciaIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ContingenciaIntegracionTest {

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
    CertificadorFelSimulado simulador;

    @Autowired
    CanalImpresionSimulado impresoraSimulada;

    @Autowired
    CertificacionContingencias contingencias;

    @Autowired
    SesionDelSistema sistema;

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

    // HU-08.07

    @Test
    void sinConexionSeEmiteElComprobanteConSuNumeroDeAccesoYSeImprime() throws Exception {   // crit. 1, 2 y 4
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);

        HttpResponse<String> finalizada = vender(cajero);

        assertThat(finalizada.statusCode()).isEqualTo(200);
        assertThat(finalizada.body()).contains("\"estado\":\"CONTINGENCIA\"", "número de acceso",
                "\"impresa\":true");
        Map<String, Object> factura = facturaDe(cajero);
        long numero = (Long) factura.get("numero_acceso_contingencia");
        assertThat(numero).isBetween(100_000_001L, 999_999_999L);
        assertThat(finalizada.body()).contains(String.valueOf(numero));
        assertThat((String) factura.get("xml_enviado")).contains("NumeroAcceso=\"" + numero + "\"");
        String ticket = new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850);
        assertThat(ticket).contains("COMPROBANTE EN CONTINGENCIA", "PENDIENTE DE CERTIFICAR ANTE LA SAT",
                String.valueOf(numero)).doesNotContain("Autorización");

        // Un nuevo intento sin respuesta conserva el mismo número y espera más (HU-08.08).
        long ventaId = jdbc.queryForObject("SELECT venta_id FROM factura WHERE id = ?", Long.class, factura.get("id"));
        emision.reintentar(ventaId, sistema.sesion());
        Map<String, Object> despues = facturaDe(cajero);
        assertThat(despues.get("numero_acceso_contingencia")).isEqualTo(numero);
        assertThat(jdbc.queryForMap("""
                SELECT intentos, proximo_intento > now() + interval '90 seconds' AS espera_mas
                FROM tarea_pendiente WHERE factura_id = ? AND NOT completada""", factura.get("id")))
                .containsEntry("intentos", 2).containsEntry("espera_mas", true);
    }

    @Test
    void elCajeroVeQueSeEstaOperandoEnContingencia() throws Exception {   // crit. 3
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        vender(cajero);

        assertThat(enviar("/api/facturas/contingencia/estado", "GET", null, cajero.sesion()).body())
                .contains("\"enContingencia\":true", "\"pendientes\":");
    }

    // HU-08.08

    @Test
    void alVolverLaConexionSeCertificanEnOrdenYQuedanVinculadasALaVenta() throws Exception {   // crit. 1, 2 y 4
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        vender(cajero);
        vender(cajero);
        simulador.usarEscenario(Escenario.ACEPTAR);

        HttpResponse<String> manual = enviar("/api/facturas/contingencia/certificar", "POST", null, cookieAdmin);

        assertThat(manual.statusCode()).isEqualTo(200);
        assertThat(manual.body()).contains("\"ocupado\":false", "Se certificaron");
        assertThat(jdbc.queryForList("""
                SELECT f.estado FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE v.usuario_id = ? AND f.uuid_autorizacion IS NOT NULL AND f.numero_acceso_contingencia IS NOT NULL
                ORDER BY f.id""", String.class, cajero.usuarioId())).containsExactly("CERTIFICADA", "CERTIFICADA");
        assertThat(enviar("/api/facturas/contingencia/certificar", "POST", null, cajero.sesion()).statusCode())
                .isEqualTo(403);
    }

    @Test
    void siElCertificadorRechazaUnaSeSigueConLasDemas() throws Exception {   // crit. 3
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        vender(cajero);
        vender(cajero);
        simulador.usarEscenario(Escenario.RECHAZAR);

        Ronda ronda = contingencias.certificarAhora(sistema.sesion());

        assertThat(ronda.rechazadas()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT f.estado FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""",
                String.class, cajero.usuarioId())).containsOnly("RECHAZADA");
        // Las del proceso automático quedan en la bitácora a nombre del usuario técnico "sistema".
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora b JOIN usuario u ON u.id = b.usuario_id
                WHERE b.accion = 'FACTURA_RECHAZADA_CERTIFICADOR' AND u.username = 'sistema' AND b.rol = 'SISTEMA'
                  AND b.entidad_id IN (SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id
                                       WHERE v.usuario_id = ?)""", Integer.class, cajero.usuarioId())).isEqualTo(2);
    }

    @Test
    void sinRespuestaLaRondaSeDetieneYElProcesoAutomaticoRespetaLaEspera() throws Exception {   // crit. 1 y 3
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        vender(cajero);
        vender(cajero);

        Ronda sinRespuesta = contingencias.certificarAhora(sistema.sesion());
        assertThat(sinRespuesta.procesadas()).isEqualTo(1);
        assertThat(sinRespuesta.mensaje()).contains("dejó de responder");

        // Ninguna de las dos ya cumplió su espera: el proceso automático no las envía.
        simulador.usarEscenario(Escenario.ACEPTAR);
        jdbc.update("""
                UPDATE tarea_pendiente SET proximo_intento = now() + interval '5 minutes'
                WHERE NOT completada AND tipo = 'CERTIFICAR'""");
        assertThat(contingencias.certificarPendientes(sistema.sesion()).procesadas()).isZero();
        // Cuando una cumple su espera, el proceso automático la certifica.
        long primera = (Long) jdbc.queryForList("""
                SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ? ORDER BY f.id""",
                Long.class, cajero.usuarioId()).getFirst();
        jdbc.update("UPDATE tarea_pendiente SET proximo_intento = now() - interval '1 second' WHERE factura_id = ?",
                primera);
        assertThat(contingencias.certificarPendientes(sistema.sesion()).certificadas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id = ?", String.class, primera))
                .isEqualTo("CERTIFICADA");
    }

    // HU-08.09

    @Test
    void elSupervisorVeLasPendientesLaMasAntiguaYLaAlertaCercaDelPlazo() throws Exception {   // crit. 1, 2 y 3
        Cajero cajero = cajeroEnCajaConImpresora();
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        vender(cajero);
        long factura = (Long) facturaDe(cajero).get("id");
        // Emitida hace 13 días: ya pasó del día 12 de los 15.
        jdbc.update("UPDATE factura SET emitida_en = now() - interval '13 days' WHERE id = ?", factura);

        HttpResponse<String> resumen = enviar("/api/facturas/contingencia", "GET", null, cookieAdmin);

        assertThat(resumen.statusCode()).isEqualTo(200);
        assertThat(resumen.body()).contains("\"alerta\":true", "\"diasMaximos\":15", "\"diaDeAlerta\":12",
                "\"dias\":13", "\"ultimoError\":\"Sin conexión con el certificador (simulado).\"",
                "\"numeroAcceso\":" + facturaDe(cajero).get("numero_acceso_contingencia"));
        assertThat(enviar("/api/facturas/contingencia", "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);

        // Se deja certificada para no dejar una alerta pendiente a las demás pruebas.
        simulador.usarEscenario(Escenario.ACEPTAR);
        contingencias.certificarAhora(sistema.sesion());
    }

    @Test
    void elUsuarioTecnicoNoIniciaSesionNiSeAdministra() throws Exception {   // decisión de Sergio, HU-08.08
        assertThat(enviar("/api/auth/login", "POST", login("sistema", "contrasena", "cualquiera"), null).statusCode())
                .isNotEqualTo(200);
        assertThat(enviar("/api/usuarios", "GET", null, cookieAdmin).body()).doesNotContain("\"username\":\"sistema\"");
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'sistema'", Long.class);
        assertThat(enviar("/api/usuarios/" + id, "GET", null, cookieAdmin).statusCode()).isEqualTo(404);
        assertThat(enviar("/api/usuarios", "POST",
                "{\"nombreCompleto\":\"Otro\",\"username\":\"otro.sis\",\"rol\":\"SISTEMA\",\"credencialTemporal\":\"Clave2026\"}",
                cookieAdmin).statusCode()).isEqualTo(400);   // el rol Sistema no se asigna
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion, String host) {}

    private Map<String, Object> facturaDe(Cajero cajero) {
        return jdbc.queryForMap("""
                SELECT f.id, f.numero_acceso_contingencia, f.xml_enviado FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE v.usuario_id = ? ORDER BY f.id LIMIT 1""", cajero.usuarioId());
    }

    /** Un cajero con turno abierto en una caja nueva con impresora. */
    private Cajero cajeroEnCajaConImpresora() throws Exception {
        String host = "contingencia-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000 + ".local";
        long impresora = idDe(enviar("/api/configuracion/impresoras", "POST",
                "{\"nombre\":\"Térmica %s\",\"host\":\"%s\",\"anchoMm\":80}".formatted(host, host), cookieAdmin));
        String nombre = "Caja CO" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST",
                "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresora), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "co.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de contingencia', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, sesion, host);
    }

    /** Una venta cobrada; devuelve la respuesta de finalizarla. */
    private HttpResponse<String> vender(Cajero cajero) throws Exception {
        String codigo = "CO" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto sin conexión\",\"precio\":7.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo),
                cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
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
