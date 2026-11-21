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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-05: HU-05.01 Facturar como Consumidor Final, HU-05.02 Buscar cliente por NIT, HU-05.03 Validar el NIT, HU-05.04
 * Consultar el nombre del contribuyente y HU-05.05 Registrar un cliente nuevo, contra PostgreSQL real y el certificador
 * simulado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ClientesIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ClientesIntegracionTest {

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

    // HU-05.02 y HU-05.03

    @Test
    void unNitRegistradoCargaNombreYDireccionConOSinGuion() throws Exception {   // 05.02 crit. 1 y 3
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Ferretería El Clavo\",\"direccion\":\"4a. calle 3-20, zona 1\"}"
                .formatted(nit));

        for (String escrito : new String[] {nit, NitsDePrueba.conGuion(nit), " " + nit.toLowerCase() + " "}) {
            HttpResponse<String> busqueda = buscar(cajero, escrito);
            assertThat(busqueda.statusCode()).as(escrito).isEqualTo(200);
            assertThat(busqueda.body()).contains("\"origen\":\"REGISTRADO\"", "\"nit\":\"" + nit + "\"",
                    "\"nombre\":\"Ferretería El Clavo\"", "\"direccion\":\"4a. calle 3-20, zona 1\"");
        }
    }

    @Test
    void unNitConElVerificadorIncorrectoNoSeUsa() throws Exception {   // 05.03 crit. 1 y 2
        Cajero cajero = cajeroConVenta();
        String invalido = NitsDePrueba.invalido(NitsDePrueba.nuevo());

        HttpResponse<String> busqueda = buscar(cajero, invalido);

        assertThat(busqueda.statusCode()).isEqualTo(422);
        assertThat(busqueda.body()).contains("NIT_INVALIDO");
        assertThat(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"X\"}".formatted(invalido)).body())
                .contains("NIT_INVALIDO");
    }

    // HU-05.04

    @Test
    void unNitNuevoSeConsultaEnElCertificadorYSeGuardaParaLaProxima() throws Exception {   // crit. 1 y 2
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();

        HttpResponse<String> primera = buscar(cajero, NitsDePrueba.conGuion(nit));

        assertThat(primera.body()).contains("\"origen\":\"CONSULTADO\"", "\"nombre\":\"CONTRIBUYENTE SIMULADO " + nit,
                "\"direccion\":\"Ciudad\"");
        assertThat(buscar(cajero, nit).body()).contains("\"origen\":\"REGISTRADO\"");
        assertThat(jdbc.queryForMap("""
                SELECT b.usuario_id, b.exitoso, b.descripcion FROM bitacora b
                WHERE b.accion = 'CLIENTE_REGISTRADO' AND b.entidad_id = (SELECT id FROM cliente WHERE identificacion = ?)""",
                nit)).containsEntry("usuario_id", cajero.usuarioId()).containsEntry("exitoso", true);
    }

    @Test
    void siElNitNoExisteOElServicioNoRespondeSeAvisaYNoSeRegistra() throws Exception {   // crit. 3 y 4
        Cajero cajero = cajeroConVenta();
        String noExiste = NitsDePrueba.nuevo();
        String sinRespuesta = NitsDePrueba.nuevo();

        simulador.usarEscenario(Escenario.RECHAZAR);
        assertThat(buscar(cajero, noExiste).body()).contains("\"origen\":\"NO_EXISTE\"", "\"cliente\":null",
                "Escriba el nombre del cliente o facture como CF");
        simulador.usarEscenario(Escenario.SIN_CONEXION);
        assertThat(buscar(cajero, sinRespuesta).body()).contains("\"origen\":\"SIN_CONSULTA\"",
                "No se pudo consultar el NIT");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE identificacion IN (?, ?)", Integer.class,
                noExiste, sinRespuesta)).isZero();
    }

    // HU-05.05

    @Test
    void seRegistraConNitYNombreYLaDireccionEsCiudad() throws Exception {   // crit. 1 y 4
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();

        HttpResponse<String> registrado = registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\" Ana López \"}"
                .formatted(NitsDePrueba.conGuion(nit)));

        assertThat(registrado.statusCode()).isEqualTo(201);
        assertThat(registrado.body()).contains("\"nit\":\"" + nit + "\"", "\"nombre\":\"Ana López\"",
                "\"direccion\":\"Ciudad\"", "\"correo\":null");
        assertThat(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"\"}".formatted(NitsDePrueba.nuevo())).body())
                .contains("NOMBRE_CLIENTE_REQUERIDO");
        assertThat(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"B\",\"correo\":\"sin-arroba\"}"
                .formatted(NitsDePrueba.nuevo())).body()).contains("CORREO_INVALIDO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'CLIENTE_REGISTRADO' AND usuario_id = ? AND NOT exitoso""",
                Integer.class, cajero.usuarioId())).isEqualTo(2);
    }

    @Test
    void unNitYaRegistradoNoSeDuplica() throws Exception {   // crit. 2
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        long id = idDe(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Primero\"}".formatted(nit)));

        HttpResponse<String> otraVez = registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Segundo\"}"
                .formatted(NitsDePrueba.conGuion(nit)));

        assertThat(idDe(otraVez)).isEqualTo(id);
        assertThat(otraVez.body()).contains("\"nombre\":\"Primero\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE identificacion = ?", Integer.class, nit))
                .isEqualTo(1);
    }

    // El cliente en la venta y en la factura (HU-05.01, HU-05.02 y HU-05.08)

    @Test
    void cadaVentaEmpiezaComoConsumidorFinalYVuelveConUnaAccion() throws Exception {   // 05.01 crit. 1 y 2
        Cajero cajero = cajeroConVenta();
        long cliente = idDe(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Cliente\"}".formatted(NitsDePrueba.nuevo())));

        assertThat(clienteDeLaVenta(cajero).body()).contains("\"consumidorFinal\":true", "\"cliente\":null");
        assertThat(asignar(cajero, cliente).body()).contains("\"consumidorFinal\":false", "\"id\":" + cliente);
        assertThat(clienteDeLaVenta(cajero).body()).contains("\"consumidorFinal\":false");
        assertThat(asignar(cajero, null).body()).contains("\"consumidorFinal\":true");
        assertThat(asignar(cajero, 999_999_999L).body()).contains("CLIENTE_NO_ENCONTRADO");
    }

    @Test
    void laFacturaSaleConElNitYElNombreDelCliente() throws Exception {   // 05.02 y HU-08.02 crit. 1
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        long cliente = idDe(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Ferretería El Clavo\"}".formatted(nit)));
        pagar(cajero);
        // Con el pago ya registrado todavía se puede cambiar el cliente (decisión de Sergio).
        assertThat(asignar(cajero, cliente).statusCode()).isEqualTo(200);

        HttpResponse<String> finalizada = enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());

        assertThat(finalizada.body()).contains("\"estado\":\"CERTIFICADA\"");
        Map<String, Object> factura = jdbc.queryForMap("""
                SELECT f.receptor_tipo, f.receptor_id, f.receptor_nombre, f.xml_enviado, v.cliente_id
                FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.usuario_id = ?""", cajero.usuarioId());
        assertThat(factura).containsEntry("receptor_tipo", "NIT").containsEntry("receptor_id", nit)
                .containsEntry("receptor_nombre", "Ferretería El Clavo").containsEntry("cliente_id", cliente);
        assertThat((String) factura.get("xml_enviado")).contains("IDReceptor=\"" + nit + "\"",
                "NombreReceptor=\"Ferretería El Clavo\"");
    }

    @Test
    void sinClienteLaFacturaVaAConsumidorFinal() throws Exception {   // 05.01 crit. 3
        Cajero cajero = cajeroConVenta();
        pagar(cajero);

        enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());

        assertThat(jdbc.queryForMap("""
                SELECT f.receptor_id, f.receptor_nombre FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE v.usuario_id = ?""", cajero.usuarioId())).containsEntry("receptor_id", "CF")
                .containsEntry("receptor_nombre", "Consumidor Final");
    }

    // HU-05.09

    @Test
    void seCorrigenNombreDireccionYCorreoDuranteLaVentaSinTocarLasFacturasEmitidas() throws Exception {   // crit. 1 y 3
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        long cliente = idDe(registrar(cajero, "{\"nit\":\"%s\",\"nombre\":\"Ferreteria Clavo\"}".formatted(nit)));
        asignar(cajero, cliente);
        pagar(cajero);
        enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());   // factura con el nombre mal escrito
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(ultimoProducto()),
                cajero.sesion()).statusCode()).isEqualTo(200);
        asignar(cajero, cliente);

        HttpResponse<String> editado = editar(cajero, cliente, "{\"nit\":\"%s\",\"nombre\":\"  Ferretería El Clavo \","
                + "\"direccion\":\"5a. calle 3-20, zona 1\",\"correo\":\"compras@elclavo.gt\"}".formatted("1234567"));

        assertThat(editado.statusCode()).as(editado.body()).isEqualTo(200);
        assertThat(editado.body()).contains("\"nit\":\"" + nit + "\"", "\"nombre\":\"Ferretería El Clavo\"",
                "zona 1", "compras@elclavo.gt");   // crit. 2: el NIT enviado se ignora
        // Crit. 1: durante la venta, la factura en curso sale con los datos nuevos.
        assertThat(clienteDeLaVenta(cajero).body()).contains("Ferretería El Clavo");
        pagar(cajero);
        enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion());
        // Crit. 3: la factura ya emitida conserva los datos con que se emitió.
        assertThat(jdbc.queryForList("""
                SELECT f.receptor_nombre FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE v.usuario_id = ? ORDER BY f.id""", String.class, cajero.usuarioId()))
                .containsExactly("Ferreteria Clavo", "Ferretería El Clavo");
    }

    @Test
    void laIdentificacionNoCambiaYLaEdicionQuedaEnLaBitacoraConAntesYDespues() throws Exception {   // crit. 2 y 4
        Cajero cajero = cajeroConVenta();
        String nit = NitsDePrueba.nuevo();
        long cliente = idDe(registrar(cajero,
                "{\"nit\":\"%s\",\"nombre\":\"Abarrotes Luz\",\"correo\":\"luz@correo.com\"}".formatted(nit)));

        editar(cajero, cliente, "{\"nombre\":\"Abarrotes La Luz\",\"direccion\":\"\",\"correo\":\"\"}");

        assertThat(jdbc.queryForObject("SELECT identificacion FROM cliente WHERE id = ?", String.class, cliente))
                .isEqualTo(nit);
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT exitoso, descripcion, cambios -> 'nombre' ->> 'antes' AS antes,
                       cambios -> 'nombre' ->> 'despues' AS despues, cambios -> 'correo' ->> 'antes' AS correo_antes,
                       jsonb_exists(cambios, 'direccion') AS cambio_direccion
                FROM bitacora WHERE accion = 'CLIENTE_EDITADO' AND entidad_id = ?""", cliente);
        assertThat(registro).containsEntry("exitoso", true).containsEntry("antes", "Abarrotes Luz")
                .containsEntry("despues", "Abarrotes La Luz").containsEntry("correo_antes", "luz@correo.com")
                .containsEntry("cambio_direccion", false);   // la dirección seguía siendo "Ciudad"
        assertThat((String) registro.get("descripcion")).contains(nit);

        // Sin cambios no se registra nada; con datos inválidos se rechaza y queda el intento.
        editar(cajero, cliente, "{\"nombre\":\"Abarrotes La Luz\",\"direccion\":\"Ciudad\",\"correo\":null}");
        HttpResponse<String> invalido = editar(cajero, cliente, "{\"nombre\":\" \",\"correo\":\"no-es-correo\"}");
        assertThat(invalido.statusCode()).isEqualTo(422);
        assertThat(invalido.body()).contains("NOMBRE_CLIENTE_REQUERIDO");
        assertThat(jdbc.queryForList("""
                SELECT exitoso FROM bitacora WHERE accion = 'CLIENTE_EDITADO' AND entidad_id = ? ORDER BY secuencia""",
                Boolean.class, cliente)).containsExactly(true, false);
        assertThat(editar(cajero, 999_999_999L, "{\"nombre\":\"Nadie\"}").statusCode()).isEqualTo(404);
    }

    // Apoyo

    record Cajero(long usuarioId, String sesion) {}

    private HttpResponse<String> editar(Cajero cajero, long clienteId, String cuerpo) throws Exception {
        return enviar("/api/clientes/" + clienteId, "PUT", cuerpo, cajero.sesion());
    }

    /** El código del último producto que creó {@link #cajeroConVenta()}, para vender otra unidad. */
    private String ultimoProducto() {
        return jdbc.queryForObject("SELECT codigo_barras FROM producto WHERE codigo_barras LIKE 'CL%' ORDER BY id DESC "
                + "LIMIT 1", String.class);
    }

    private HttpResponse<String> buscar(Cajero cajero, String nit) throws Exception {
        return enviar("/api/clientes/nit/" + nit.replace(" ", "%20"), "GET", null, cajero.sesion());
    }

    private HttpResponse<String> registrar(Cajero cajero, String cuerpo) throws Exception {
        return enviar("/api/clientes", "POST", cuerpo, cajero.sesion());
    }

    private HttpResponse<String> clienteDeLaVenta(Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/cliente", "GET", null, cajero.sesion());
    }

    private HttpResponse<String> asignar(Cajero cajero, Long clienteId) throws Exception {
        return enviar("/api/ventas/actual/cliente", "PUT", "{\"clienteId\":%s}".formatted(clienteId), cajero.sesion());
    }

    private void pagar(Cajero cajero) throws Exception {
        assertThat(enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}",
                cajero.sesion()).statusCode()).isEqualTo(200);
    }

    /** Un cajero con turno abierto y una venta en curso con un producto nuevo. */
    private Cajero cajeroConVenta() throws Exception {
        String nombre = "Caja C" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "cl.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de clientes', 3, ?, false)""", username, codificador.encode(PIN));
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        assertThat(enviar("/api/turnos", "POST", null, sesion).statusCode()).isEqualTo(201);
        String codigo = "CL" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Clavos 1 lb\",\"precio\":12.50}".formatted(codigo),
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
