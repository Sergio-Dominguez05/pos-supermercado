package gt.supermercado.pos.facturacion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.clientes.NitsDePrueba;
import gt.supermercado.pos.facturacion.aplicacion.EnvioFacturas;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo;
import gt.supermercado.pos.facturacion.infraestructura.CanalCorreoSimulado;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-05.11 Registrar el correo para el envío de la factura y HU-08.15 Enviar la factura por correo, contra PostgreSQL
 * real, el certificador simulado y el servidor de correo simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CorreoFacturasIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CorreoFacturasIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";
    static final String SERVIDOR = """
            {"servidor":"smtp.supermercado.test","puerto":587,"seguridad":"STARTTLS","usuario":"facturas",
             "secreto":"Clave-SMTP-1","remitente":"facturas@supermercado.test","nombreRemitente":"Supermercado"}""";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    CertificadorFelSimulado certificador;

    @Autowired
    CanalCorreoSimulado correo;

    @Autowired
    EnvioFacturas envio;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
        FacturacionDePrueba.configurarEmisorYEstablecimiento(jdbc);
        assertThat(enviar("/api/configuracion/correo", "PUT", SERVIDOR, cookieAdmin).statusCode()).isEqualTo(200);
    }

    @AfterEach
    void restaurar() {
        certificador.usarEscenario(Escenario.ACEPTAR);
        correo.fallar(false);
    }

    // HU-05.11

    @Test
    void alAsignarUnClienteConCorreoLaFacturaSeEnviaraASuCorreoYSePuedeCambiar() throws Exception {   // crit. 1 y 4
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        long cliente = registrarCliente(cajero, nit, "ana@cliente.test");

        HttpResponse<String> asignado = enviar("/api/ventas/actual/cliente", "PUT",
                "{\"clienteId\":%d}".formatted(cliente), cajero.sesion());
        assertThat(asignado.body()).contains("\"correoFactura\":\"ana@cliente.test\"");

        HttpResponse<String> cambiado = cambiarCorreo(cajero, " ana.nuevo@cliente.test ");
        assertThat(cambiado.statusCode()).isEqualTo(200);
        assertThat(cambiado.body()).contains("\"correoFactura\":\"ana.nuevo@cliente.test\"");
        // El correo nuevo queda en el cliente para sus próximas compras.
        assertThat(jdbc.queryForObject("SELECT correo FROM cliente WHERE id = ?", String.class, cliente))
                .isEqualTo("ana.nuevo@cliente.test");
        assertThat(enviar("/api/ventas/actual/cliente", "GET", null, cajero.sesion()).body())
                .contains("\"correoFactura\":\"ana.nuevo@cliente.test\"");

        // Quitarlo de esta venta no lo borra del cliente.
        assertThat(cambiarCorreo(cajero, "").body()).contains("\"correoFactura\":null");
        assertThat(jdbc.queryForObject("SELECT correo FROM cliente WHERE id = ?", String.class, cliente))
                .isEqualTo("ana.nuevo@cliente.test");
    }

    @Test
    void sinCorreoLaVentaTerminaNormalmenteSinEnviarNada() throws Exception {   // 05.11 crit. 2 y 08.15 crit. 1
        Cajero cajero = cajeroConVenta();
        assertThat(enviar("/api/ventas/actual/cliente", "GET", null, cajero.sesion()).body())
                .contains("\"consumidorFinal\":true", "\"correoFactura\":null");
        int enviadosAntes = correo.envios().size();

        HttpResponse<String> finalizada = finalizar(cajero);

        assertThat(finalizada.statusCode()).isEqualTo(200);
        assertThat(finalizada.body()).contains("\"estado\":\"CERTIFICADA\"");
        long factura = facturaDe(cajero);
        assertThat(jdbc.queryForObject("SELECT receptor_correo FROM factura WHERE id = ?", String.class, factura))
                .isNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tarea_pendiente WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO'""",
                Integer.class, factura)).isZero();
        assertThat(correo.envios()).hasSize(enviadosAntes);
    }

    @Test
    void unCorreoMalEscritoSeRechazaYAConsumidorFinalTambienSeEnvia() throws Exception {   // 05.11 crit. 3
        Cajero cajero = cajeroConVenta();

        for (String invalido : List.of("ana", "ana@cliente", "ana @cliente.test", "@cliente.test")) {
            HttpResponse<String> rechazado = cambiarCorreo(cajero, invalido);
            assertThat(rechazado.statusCode()).as(invalido).isEqualTo(422);
            assertThat(rechazado.body()).as(invalido).contains("CORREO_INVALIDO");
        }
        assertThat(cambiarCorreo(cajero, "cf@cliente.test").body()).contains("\"consumidorFinal\":true",
                "\"correoFactura\":\"cf@cliente.test\"");

        finalizar(cajero);

        assertThat(correo.enviadosA("cf@cliente.test")).hasSize(1);
    }

    // HU-08.15

    @Test
    void alFinalizarLaFacturaSaleConElPdfYElXml() throws Exception {   // 08.15 crit. 1 y 2
        Cajero cajero = cajeroConVenta();
        String destino = "pdf.%d@cliente.test".formatted(System.nanoTime());
        long cliente = registrarCliente(cajero, NitsDePrueba.nuevo(), destino);
        enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":%d}".formatted(cliente), cajero.sesion());

        assertThat(finalizar(cajero).body()).contains("\"estado\":\"CERTIFICADA\"");

        List<MensajeCorreo> enviados = correo.enviadosA(destino);
        assertThat(enviados).hasSize(1);
        MensajeCorreo mensaje = enviados.getFirst();
        Map<String, Object> factura = jdbc.queryForMap("""
                SELECT id, serie, numero, uuid_autorizacion::text AS autorizacion, receptor_correo FROM factura
                WHERE id = ?""", facturaDe(cajero));
        assertThat(factura).containsEntry("receptor_correo", destino);
        assertThat(mensaje.asunto()).contains("serie " + factura.get("serie"), "número " + factura.get("numero"));
        assertThat(mensaje.texto()).contains((String) factura.get("autorizacion"));
        assertThat(mensaje.adjuntos()).extracting(MensajeCorreo.Adjunto::tipo)
                .containsExactly("application/pdf", "application/xml");
        assertThat(new String(mensaje.adjuntos().get(0).contenido(), 0, 5, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
        assertThat(new String(mensaje.adjuntos().get(1).contenido(), StandardCharsets.UTF_8))
                .contains("GTDocumento");
        assertThat(correo.envios().getLast().conexion().servidor()).isEqualTo("smtp.supermercado.test");
        // El envío queda en el historial de la factura (HU-08.13) y la tarea, completa.
        assertThat(enviar("/api/facturas/" + factura.get("id"), "GET", null, cookieAdmin).body())
                .contains("\"tipo\":\"CORREO_ENVIADO\"", "Enviada por correo a " + destino);
        assertThat(jdbc.queryForObject("""
                SELECT completada FROM tarea_pendiente WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO'""",
                Boolean.class, factura.get("id"))).isTrue();
    }

    @Test
    void siElEnvioFallaSeReintentaSinAfectarLaVenta() throws Exception {   // 08.15 crit. 3
        Cajero cajero = cajeroConVenta();
        String destino = "falla.%d@cliente.test".formatted(System.nanoTime());
        cambiarCorreo(cajero, destino);
        correo.fallar(true);

        HttpResponse<String> finalizada = finalizar(cajero);

        assertThat(finalizada.statusCode()).isEqualTo(200);
        assertThat(finalizada.body()).contains("\"estado\":\"CERTIFICADA\"");
        long factura = facturaDe(cajero);
        Map<String, Object> tarea = tareaDeCorreo(factura);
        assertThat(tarea).containsEntry("completada", false).containsEntry("intentos", 1);
        assertThat((String) tarea.get("ultimo_error")).contains("no respondió");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fel_intento WHERE factura_id = ? AND operacion = 'CORREO' AND NOT exitoso""",
                Integer.class, factura)).isEqualTo(1);

        // Antes de su espera no se vuelve a intentar.
        correo.fallar(false);
        envio.enviarPendientes();
        assertThat(correo.enviadosA(destino)).isEmpty();

        jdbc.update("UPDATE tarea_pendiente SET proximo_intento = now() WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO'",
                factura);
        envio.enviarPendientes();

        assertThat(correo.enviadosA(destino)).hasSize(1);
        assertThat(tareaDeCorreo(factura)).containsEntry("completada", true);
        assertThat(enviar("/api/facturas/" + factura, "GET", null, cookieAdmin).body())
                .contains("\"tipo\":\"CORREO_FALLIDO\"", "\"tipo\":\"CORREO_ENVIADO\"");
    }

    @Test
    void despuesDeCincoFallasDejaDeReintentar() throws Exception {   // 08.15 crit. 3
        Cajero cajero = cajeroConVenta();
        cambiarCorreo(cajero, "agotado.%d@cliente.test".formatted(System.nanoTime()));
        correo.fallar(true);
        finalizar(cajero);
        long factura = facturaDe(cajero);

        for (int i = 0; i < 4; i++) {
            jdbc.update("""
                    UPDATE tarea_pendiente SET proximo_intento = now()
                    WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO' AND NOT completada""", factura);
            envio.enviarPendientes();
        }

        Map<String, Object> tarea = tareaDeCorreo(factura);
        assertThat(tarea).containsEntry("completada", true).containsEntry("intentos", 4);
        assertThat((String) tarea.get("ultimo_error")).startsWith("Se agotaron los intentos");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fel_intento WHERE factura_id = ? AND operacion = 'CORREO' AND NOT exitoso""",
                Integer.class, factura)).isEqualTo(5);
    }

    @Test
    void unComprobanteEnContingenciaSeEnviaCuandoSeCertifica() throws Exception {   // 08.15 crit. 1 y 3
        Cajero cajero = cajeroConVenta();
        String destino = "contingencia.%d@cliente.test".formatted(System.nanoTime());
        cambiarCorreo(cajero, destino);
        certificador.usarEscenario(Escenario.SIN_CONEXION);

        assertThat(finalizar(cajero).body()).contains("\"estado\":\"CONTINGENCIA\"");
        assertThat(correo.enviadosA(destino)).isEmpty();
        envio.enviarPendientes();
        assertThat(correo.enviadosA(destino)).isEmpty();

        certificador.usarEscenario(Escenario.ACEPTAR);
        assertThat(enviar("/api/facturas/contingencia/certificar", "POST", null, cookieAdmin).statusCode())
                .isEqualTo(200);

        assertThat(correo.enviadosA(destino)).hasSize(1);
    }

    @Test
    void sinServidorConfiguradoElEnvioEsperaSinGastarIntentos() throws Exception {   // 08.15 crit. 3
        Cajero cajero = cajeroConVenta();
        String destino = "espera.%d@cliente.test".formatted(System.nanoTime());
        cambiarCorreo(cajero, destino);
        jdbc.update("DELETE FROM config_correo");
        try {
            assertThat(finalizar(cajero).body()).contains("\"estado\":\"CERTIFICADA\"");

            long factura = facturaDe(cajero);
            Map<String, Object> tarea = tareaDeCorreo(factura);
            assertThat(tarea).containsEntry("completada", false).containsEntry("intentos", 0);
            assertThat((String) tarea.get("ultimo_error")).contains("Falta configurar el servidor de correo");
            assertThat(correo.enviadosA(destino)).isEmpty();
        } finally {
            enviar("/api/configuracion/correo", "PUT", SERVIDOR, cookieAdmin);
        }
    }

    @Test
    void desdeElDetalleSeReenviaLaFactura() throws Exception {   // 08.15 crit. 4
        Cajero cajero = cajeroConVenta();
        String destino = "reenvio.%d@cliente.test".formatted(System.nanoTime());
        cambiarCorreo(cajero, destino);
        finalizar(cajero);
        long factura = facturaDe(cajero);

        HttpResponse<String> reenviada = enviar("/api/facturas/" + factura + "/correo", "POST", null, cookieAdmin);

        assertThat(reenviada.statusCode()).isEqualTo(200);
        assertThat(reenviada.body()).contains("\"enviado\":true", "Factura enviada a " + destino);
        assertThat(correo.enviadosA(destino)).hasSize(2);
        // El cajero no tiene el permiso del detalle.
        assertThat(enviar("/api/facturas/" + factura + "/correo", "POST", null, cajero.sesion()).statusCode())
                .isEqualTo(403);

        Cajero sinCorreo = cajeroConVenta();
        finalizar(sinCorreo);
        HttpResponse<String> rechazada = enviar("/api/facturas/" + facturaDe(sinCorreo) + "/correo", "POST", null,
                cookieAdmin);
        assertThat(rechazada.statusCode()).isEqualTo(422);
        assertThat(rechazada.body()).contains("FACTURA_SIN_CORREO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tarea_pendiente WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO'""",
                Integer.class, facturaDe(sinCorreo))).isZero();
    }

    @Test
    void elServidorDeCorreoGuardaLaContrasenaCifradaYSePrueba() throws Exception {   // decisión de Sergio: SMTP
        HttpResponse<String> guardado = enviar("/api/configuracion/correo", "GET", null, cookieAdmin);
        assertThat(guardado.body()).contains("\"configurado\":true", "\"servidor\":\"smtp.supermercado.test\"",
                "\"secretoGuardado\":true").doesNotContain("Clave-SMTP-1");
        byte[] cifrada = jdbc.queryForObject("SELECT secreto_cifrado FROM config_correo", byte[].class);
        assertThat(new String(cifrada, StandardCharsets.ISO_8859_1)).doesNotContain("Clave-SMTP-1");

        // Sin contraseña nueva conserva la guardada; la bitácora no la muestra.
        HttpResponse<String> cambio = enviar("/api/configuracion/correo", "PUT", SERVIDOR
                .replace("\"secreto\":\"Clave-SMTP-1\",", "").replace("587", "2525"), cookieAdmin);
        assertThat(cambio.statusCode()).isEqualTo(200);
        assertThat(cambio.body()).contains("\"puerto\":2525", "\"secretoGuardado\":true");
        assertThat(jdbc.queryForObject("""
                SELECT descripcion || ' ' || coalesce(cambios::text, '') FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND entidad = 'config_correo' AND exitoso
                ORDER BY secuencia DESC LIMIT 1""", String.class)).contains("puerto").doesNotContain("Clave-SMTP-1");

        HttpResponse<String> invalido = enviar("/api/configuracion/correo", "PUT",
                SERVIDOR.replace("facturas@supermercado.test", "facturas"), cookieAdmin);
        assertThat(invalido.statusCode()).isEqualTo(422);
        assertThat(invalido.body()).contains("REMITENTE_INVALIDO");

        String destino = "prueba.%d@supermercado.test".formatted(System.nanoTime());
        HttpResponse<String> prueba = enviar("/api/configuracion/correo/probar", "POST",
                "{\"servidor\":%s,\"destinatario\":\"%s\"}".formatted(SERVIDOR, destino), cookieAdmin);
        assertThat(prueba.body()).contains("\"exitosa\":true");
        assertThat(correo.enviadosA(destino)).hasSize(1);
        correo.fallar(true);
        assertThat(enviar("/api/configuracion/correo/probar", "POST",
                "{\"servidor\":%s,\"destinatario\":\"%s\"}".formatted(SERVIDOR, destino), cookieAdmin).body())
                .contains("\"exitosa\":false", "no respondió");

        Cajero cajero = cajeroConVenta();
        assertThat(enviar("/api/configuracion/correo", "GET", null, cajero.sesion()).statusCode()).isEqualTo(403);
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion) {}

    private long registrarCliente(Cajero cajero, String nit, String correoCliente) throws Exception {
        return idDe(enviar("/api/clientes", "POST", "{\"nit\":\"%s\",\"nombre\":\"Cliente con correo\",\"correo\":\"%s\"}"
                .formatted(nit, correoCliente), cajero.sesion()));
    }

    private HttpResponse<String> cambiarCorreo(Cajero cajero, String nuevo) throws Exception {
        return enviar("/api/ventas/actual/cliente/correo", "PUT", "{\"correo\":\"%s\"}".formatted(nuevo),
                cajero.sesion());
    }

    private HttpResponse<String> finalizar(Cajero cajero) throws Exception {
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
        return enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
    }

    private long facturaDe(Cajero cajero) {
        return jdbc.queryForObject("SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?",
                Long.class, cajero.usuarioId());
    }

    private Map<String, Object> tareaDeCorreo(long factura) {
        return jdbc.queryForMap("""
                SELECT completada, intentos, ultimo_error FROM tarea_pendiente
                WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO' ORDER BY id DESC LIMIT 1""", factura);
    }

    /** Un cajero con turno abierto y una venta en curso con un producto nuevo. */
    private Cajero cajeroConVenta() throws Exception {
        String nombre = "Caja M" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "co.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de correos', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "CO" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Arroz 1 lb\",\"precio\":8.50}".formatted(codigo),
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
