package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.clientes.NitsDePrueba;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-08.06 Manejar rechazos del certificador y HU-04.09 crit. 2, contra PostgreSQL real y el certificador simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + RechazosIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class RechazosIntegracionTest {

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

    // Crit. 1 y 4

    @Test
    void elRechazoSeExplicaEnLenguajeComprensibleYSeGuardaCompleto() throws Exception {
        Cajero cajero = cajeroConVentaCobrada();
        simulador.usarEscenario(Escenario.RECHAZAR);

        HttpResponse<String> finalizada = finalizar(cajero);

        assertThat(finalizada.statusCode()).isEqualTo(200);
        assertThat(finalizada.body()).contains("\"estado\":\"RECHAZADA\"",
                "El NIT o el nombre del cliente no es válido para la SAT", "\"corregible\":true");
        Map<String, Object> intento = jdbc.queryForMap("""
                SELECT i.codigo_error, i.respuesta->>'respuesta' AS respuesta FROM fel_intento i
                JOIN factura f ON f.id = i.factura_id WHERE f.venta_id = ?""", cajero.ventaId());
        assertThat(intento).containsEntry("codigo_error", "SIM-001");
        assertThat((String) intento.get("respuesta")).contains("\"codigo\":\"SIM-001\"", "\"resultado\":false");
        assertThat(jdbc.queryForMap("""
                SELECT exitoso, usuario_id, cambios->>'tipo' AS tipo FROM bitacora
                WHERE accion = 'FACTURA_RECHAZADA_CERTIFICADOR' AND entidad = 'factura'
                  AND entidad_id = (SELECT id FROM factura WHERE venta_id = ?)""", cajero.ventaId()))
                .containsEntry("exitoso", true).containsEntry("usuario_id", cajero.usuarioId())
                .containsEntry("tipo", "RECEPTOR");
    }

    // Crit. 2

    @Test
    void elCajeroCorrigeElClienteYReenviaSinVolverACobrar() throws Exception {
        Cajero cajero = cajeroConVentaCobrada();
        simulador.usarEscenario(Escenario.RECHAZAR);
        finalizar(cajero);
        simulador.usarEscenario(Escenario.ACEPTAR);
        String nit = NitsDePrueba.nuevo();
        long cliente = idDe(enviar("/api/clientes", "POST", "{\"nit\":\"%s\",\"nombre\":\"Cliente corregido\"}"
                .formatted(nit), cajero.sesion()));
        int pagosAntes = pagosDe(cajero);

        HttpResponse<String> reenvio = reenviarDesdeCaja(cajero, cliente);

        assertThat(reenvio.statusCode()).isEqualTo(200);
        assertThat(reenvio.body()).contains("\"estado\":\"CERTIFICADA\"");
        Map<String, Object> factura = jdbc.queryForMap("""
                SELECT f.estado, f.receptor_id, f.receptor_nombre, f.xml_enviado, v.estado AS venta, v.cliente_id
                FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.id = ?""", cajero.ventaId());
        assertThat(factura).containsEntry("estado", "CERTIFICADA").containsEntry("receptor_id", nit)
                .containsEntry("receptor_nombre", "Cliente corregido").containsEntry("venta", "FINALIZADA")
                .containsEntry("cliente_id", cliente);
        assertThat((String) factura.get("xml_enviado")).contains("IDReceptor=\"" + nit + "\"");
        assertThat(pagosDe(cajero)).isEqualTo(pagosAntes);   // no se volvió a cobrar
        assertThat(jdbc.queryForObject("SELECT count(*) FROM factura WHERE venta_id = ?", Integer.class,
                cajero.ventaId())).isEqualTo(1);
    }

    @Test
    void tambienSePuedeReenviarAConsumidorFinal() throws Exception {
        Cajero cajero = cajeroConVentaCobrada();
        simulador.usarEscenario(Escenario.RECHAZAR);
        finalizar(cajero);
        simulador.usarEscenario(Escenario.ACEPTAR);

        assertThat(reenviarDesdeCaja(cajero, null).body()).contains("\"estado\":\"CERTIFICADA\"");
        assertThat(jdbc.queryForObject("SELECT receptor_id FROM factura WHERE venta_id = ?", String.class,
                cajero.ventaId())).isEqualTo("CF");
    }

    @Test
    void soloSeReenviaUnaFacturaRechazada() throws Exception {
        Cajero cajero = cajeroConVentaCobrada();
        finalizar(cajero);   // certificada

        HttpResponse<String> reenvio = reenviarDesdeCaja(cajero, null);

        assertThat(reenvio.statusCode()).isEqualTo(422);
        assertThat(reenvio.body()).contains("FACTURA_NO_RECHAZADA");
    }

    // Crit. 3 y HU-04.09 crit. 2

    @Test
    void unRechazoPorMontosQuedaPendienteParaElSupervisorConSuDetalle() throws Exception {
        Cajero cajero = cajeroConVentaCobrada();
        simulador.usarEscenario(Escenario.RECHAZAR_MONTOS);

        assertThat(finalizar(cajero).body()).contains("\"estado\":\"RECHAZADA\"", "Los montos de la factura no "
                + "coinciden", "avise a su supervisor", "\"corregible\":false");

        // El cajero no lo puede corregir en caja, y el intento queda en la bitácora.
        HttpResponse<String> desdeCaja = reenviarDesdeCaja(cajero, null);
        assertThat(desdeCaja.statusCode()).isEqualTo(422);
        assertThat(desdeCaja.body()).contains("RECHAZO_NO_CORREGIBLE_EN_CAJA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'FACTURA_RECHAZADA_CERTIFICADOR' AND NOT exitoso
                  AND usuario_id = ?""", Integer.class, cajero.usuarioId())).isEqualTo(1);
        // HU-04.09 crit. 2: el rechazo por montos se registra con los montos que envió el POS.
        assertThat(jdbc.queryForMap("""
                SELECT cambios->>'tipo' AS tipo, cambios->>'totalEnviado' AS total, cambios->>'ivaEnviado' AS iva
                FROM bitacora WHERE accion = 'FACTURA_RECHAZADA_CERTIFICADOR' AND exitoso
                  AND entidad_id = (SELECT id FROM factura WHERE venta_id = ?)""", cajero.ventaId()))
                .containsEntry("tipo", "MONTOS").containsEntry("total", "15.00").containsEntry("iva", "1.61");

        // El supervisor la ve en la lista de pendientes de facturar.
        HttpResponse<String> lista = enviar("/api/facturas/rechazadas", "GET", null, cookieAdmin);
        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).contains("\"ventaId\":" + cajero.ventaId(), "\"tipo\":\"MONTOS\"",
                "\"corregibleEnCaja\":false", "\"codigo\":\"SIM-002\"", "\\\"resultado\\\":false");
        assertThat(enviar("/api/facturas/rechazadas", "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);

        // Corregida la causa, el supervisor la reenvía y sale de la lista.
        simulador.usarEscenario(Escenario.ACEPTAR);
        HttpResponse<String> reenvio = enviar("/api/facturas/rechazadas/" + cajero.ventaId() + "/reenviar", "POST",
                null, cookieAdmin);
        assertThat(reenvio.body()).contains("\"estado\":\"CERTIFICADA\"");
        assertThat(enviar("/api/facturas/rechazadas", "GET", null, cookieAdmin).body())
                .doesNotContain("\"ventaId\":" + cajero.ventaId() + ",");
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion, long ventaId) {}

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    private HttpResponse<String> reenviarDesdeCaja(Cajero cajero, Long clienteId) throws Exception {
        return enviar("/api/ventas/" + cajero.ventaId() + "/factura/reenviar", "POST",
                "{\"clienteId\":%s}".formatted(clienteId), cajero.sesion());
    }

    private int pagosDe(Cajero cajero) {
        return jdbc.queryForObject("SELECT count(*) FROM pago WHERE venta_id = ?", Integer.class, cajero.ventaId());
    }

    /** Un cajero con turno abierto y una venta de Q15.00 pagada con tarjeta, lista para finalizar. */
    private Cajero cajeroConVentaCobrada() throws Exception {
        String nombre = "Caja R" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "re.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de rechazos', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "RE" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Arroz rechazado\",\"precio\":5.00}".formatted(codigo),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\",\"cantidad\":3}".formatted(codigo),
                sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                sesion).statusCode()).isEqualTo(200);
        long ventaId = jdbc.queryForObject("SELECT id FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
        return new Cajero(usuarioId, sesion, ventaId);
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
    }

    private static long idDe(HttpResponse<String> respuesta) {
        String cuerpo = respuesta.body();
        int inicio = cuerpo.indexOf("\"id\":") + 5;
        int fin = inicio;
        while (fin < cuerpo.length() && Character.isDigit(cuerpo.charAt(fin))) {
            fin++;
        }
        return Long.parseLong(cuerpo.substring(inicio, fin));
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
