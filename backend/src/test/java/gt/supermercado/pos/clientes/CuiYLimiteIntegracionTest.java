package gt.supermercado.pos.clientes;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-05.06 Facturar con CUI, HU-05.07 Validar el límite para Consumidor Final y HU-12.07 Configurar ese límite, contra
 * PostgreSQL real y el certificador simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CuiYLimiteIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CuiYLimiteIntegracionTest {

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
    void restaurar() throws Exception {
        simulador.usarEscenario(Escenario.ACEPTAR);
        parametros("2500.00");
    }

    // HU-05.06

    @Test
    void unCuiSeConsultaEnElCertificadorYSeGuardaParaLaProxima() throws Exception {   // 05.06 crit. 1 a 3
        Cajero cajero = cajeroConVenta("12.50");
        String cui = cuiNuevo();

        HttpResponse<String> primera = enviar("/api/clientes/cui/" + cui.substring(0, 4) + "%20" + cui.substring(4),
                "GET", null, cajero.sesion());

        assertThat(primera.statusCode()).isEqualTo(200);
        assertThat(primera.body()).contains("\"origen\":\"CONSULTADO\"", "\"nombre\":\"PERSONA SIMULADA " + cui,
                "\"tipoIdentificacion\":\"CUI\"", "\"nit\":\"" + cui + "\"");
        assertThat(enviar("/api/clientes/cui/" + cui, "GET", null, cajero.sesion()).body())
                .contains("\"origen\":\"REGISTRADO\"");
        // Crit. 2: 13 dígitos numéricos.
        for (String invalido : new String[] {"123456789012", "12345678901234", "12345678901AB"}) {
            HttpResponse<String> rechazado = enviar("/api/clientes/cui/" + invalido, "GET", null, cajero.sesion());
            assertThat(rechazado.statusCode()).as(invalido).isEqualTo(422);
            assertThat(rechazado.body()).as(invalido).contains("CUI_INVALIDO");
        }
    }

    @Test
    void siLaConsultaDelCuiFallaSeEscribeElNombreYLaFacturaSaleConElCui() throws Exception {   // 05.06 crit. 3 y 4
        Cajero cajero = cajeroConVenta("12.50");
        String cui = cuiNuevo();
        simulador.usarEscenario(Escenario.RECHAZAR);
        assertThat(enviar("/api/clientes/cui/" + cui, "GET", null, cajero.sesion()).body())
                .contains("\"origen\":\"NO_EXISTE\"", "El certificador no encontró el CUI " + cui);
        simulador.usarEscenario(Escenario.ACEPTAR);

        long cliente = idDe(enviar("/api/clientes", "POST",
                "{\"tipoIdentificacion\":\"CUI\",\"nit\":\"%s\",\"nombre\":\"María López\"}".formatted(cui),
                cajero.sesion()));
        assertThat(enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":%d}".formatted(cliente), cajero.sesion())
                .body()).contains("\"tipoIdentificacion\":\"CUI\"");
        HttpResponse<String> finalizada = finalizar(cajero);

        assertThat(finalizada.body()).contains("\"estado\":\"CERTIFICADA\"");
        Map<String, Object> factura = jdbc.queryForMap("""
                SELECT f.receptor_tipo, f.receptor_id, f.receptor_nombre, f.xml_enviado FROM factura f
                JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""", cajero.usuarioId());
        assertThat(factura).containsEntry("receptor_tipo", "CUI").containsEntry("receptor_id", cui)
                .containsEntry("receptor_nombre", "María López");
        assertThat((String) factura.get("xml_enviado")).contains("IDReceptor=\"" + cui + "\"", "TipoEspecial=\"CUI\"");
        assertThat(jdbc.queryForObject("""
                SELECT descripcion FROM bitacora WHERE accion = 'CLIENTE_REGISTRADO' AND entidad_id = ?""",
                String.class, cliente)).startsWith("Cliente CUI " + cui + " registrado");
    }

    // HU-05.07

    @Test
    void desdeElLimiteNoSeCobraAConsumidorFinalSinNitNiCui() throws Exception {   // 05.07 crit. 1 a 3
        Cajero cajero = cajeroConVenta("2500.00");

        HttpResponse<String> cobro = enviar("/api/ventas/actual/cobro", "POST", null, cajero.sesion());

        assertThat(cobro.statusCode()).isEqualTo(422);
        assertThat(cobro.body()).contains("LIMITE_CONSUMIDOR_FINAL", "Q2,500.00 o más",
                "la SAT exige el NIT o el CUI del comprador");
        // Con un cliente se cobra.
        long cliente = idDe(enviar("/api/clientes", "POST",
                "{\"nit\":\"%s\",\"nombre\":\"Ferretería El Clavo\"}".formatted(NitsDePrueba.nuevo()), cajero.sesion()));
        enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":%d}".formatted(cliente), cajero.sesion());
        assertThat(enviar("/api/ventas/actual/cobro", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);

        // Crit. 3: por debajo del límite vuelve a poder ser CF.
        Cajero otro = cajeroConVenta("2499.99");
        assertThat(enviar("/api/ventas/actual/cobro", "POST", null, otro.sesion()).statusCode()).isEqualTo(200);
    }

    @Test
    void tambienSeRevisaAlFinalizarSiSeCambiaAConsumidorFinalEnElCobro() throws Exception {   // decisión de Sergio
        Cajero cajero = cajeroConVenta("3000.00");
        long cliente = idDe(enviar("/api/clientes", "POST",
                "{\"nit\":\"%s\",\"nombre\":\"Comercial Ñandú\"}".formatted(NitsDePrueba.nuevo()), cajero.sesion()));
        enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":%d}".formatted(cliente), cajero.sesion());
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        // Con el pago registrado, el cajero vuelve a Consumidor Final.
        enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":null}", cajero.sesion());

        HttpResponse<String> finalizada = enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());

        assertThat(finalizada.statusCode()).isEqualTo(422);
        assertThat(finalizada.body()).contains("LIMITE_CONSUMIDOR_FINAL");
        assertThat(jdbc.queryForObject("SELECT estado FROM venta WHERE usuario_id = ?", String.class,
                cajero.usuarioId())).isEqualTo("EN_CURSO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isZero();
    }

    // HU-12.07

    @Test
    void elAdministradorCambiaElLimiteYSeAplicaDeInmediato() throws Exception {   // 12.07 crit. 1 a 3
        assertThat(enviar("/api/configuracion/parametros", "GET", null, cookieAdmin).body())
                .contains("\"limiteConsumidorFinal\":2500.00");
        Cajero cajero = cajeroConVenta("150.00");
        assertThat(enviar("/api/ventas/actual/cobro", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);

        HttpResponse<String> cambio = parametros("100.00");

        assertThat(cambio.statusCode()).isEqualTo(200);
        assertThat(cambio.body()).contains("\"limiteConsumidorFinal\":100.00");
        HttpResponse<String> cobro = enviar("/api/ventas/actual/cobro", "POST", null, cajero.sesion());
        assertThat(cobro.statusCode()).isEqualTo(422);
        assertThat(cobro.body()).contains("Q100.00 o más");
        assertThat(jdbc.queryForObject("""
                SELECT cambios -> 'limiteConsumidorFinal' ->> 'despues' FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND exitoso AND cambios -> 'limiteConsumidorFinal' IS NOT NULL
                ORDER BY secuencia DESC LIMIT 1""", String.class))
                .isEqualTo("100.00");

        HttpResponse<String> invalido = parametros("0");
        assertThat(invalido.statusCode()).isEqualTo(422);
        assertThat(invalido.body()).contains("LIMITE_CONSUMIDOR_FINAL_INVALIDO");
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion) {}

    private HttpResponse<String> parametros(String limite) throws Exception {
        return enviar("/api/configuracion/parametros", "PUT",
                "{\"cantidadMaximaPorLinea\":50,\"cierreCiego\":false,\"limiteConsumidorFinal\":%s}".formatted(limite),
                cookieAdmin);
    }

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    private static String cuiNuevo() {
        return "%013d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L));
    }

    /** Un cajero con turno abierto y una venta en curso con un producto nuevo de ese precio. */
    private Cajero cajeroConVenta(String precio) throws Exception {
        String nombre = "Caja U" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "cu.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero del CUI', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "CU" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Refrigeradora\",\"precio\":%s}".formatted(codigo, precio),
                cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), sesion)
                .statusCode()).isEqualTo(200);
        return new Cajero(usuarioId, sesion);
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
