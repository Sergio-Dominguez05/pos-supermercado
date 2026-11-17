package gt.supermercado.pos.configuracion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.aplicacion.GeneracionDte;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.impuestos.dominio.CalculadoraIva;
import gt.supermercado.pos.impuestos.dominio.LineaCalculada;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-12.02 Configurar el establecimiento registrado en la SAT, contra PostgreSQL real con las migraciones. El
 * establecimiento de la sucursal de V1 es una sola fila: cada prueba guarda primero el que necesita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + EstablecimientoIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class EstablecimientoIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    GeneracionDte generacionDte;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
    }

    @Test
    void seRegistranCodigoNombreYDireccionDelEstablecimiento() throws Exception {   // crit. 1
        HttpResponse<String> guardado = guardar(establecimiento("1", "Súper del Valle, zona 1"), cookieAdmin);

        assertThat(guardado.statusCode()).isEqualTo(200);
        assertThat(guardado.body()).contains("\"configurado\":true", "\"codigoSat\":\"1\"",
                "\"nombre\":\"Súper del Valle, zona 1\"", "\"direccion\":\"6a. avenida 5-10, zona 1, Guatemala\"");
        assertThat(enviar("/api/configuracion/establecimiento", "GET", null, cookieAdmin).body())
                .contains("\"codigoSat\":\"1\"");
        // Crit. 3: es el establecimiento de la sucursal de V1.
        assertThat(jdbc.queryForObject("""
                SELECT s.codigo FROM establecimiento e JOIN sucursal s ON s.id = e.sucursal_id""", String.class))
                .isEqualTo("MATRIZ");
    }

    @Test
    void elCodigoEsElNumeroQueAsignoLaSat() throws Exception {   // crit. 1
        guardar(establecimiento("1", "Establecimiento válido"), cookieAdmin);

        for (String codigo : new String[] {"0", "01", "A1", "", "1234567"}) {
            HttpResponse<String> respuesta = guardar(establecimiento(codigo, "Establecimiento inválido"), cookieAdmin);
            assertThat(respuesta.statusCode()).as(codigo).isEqualTo(422);
            assertThat(respuesta.body()).as(codigo).contains("CODIGO_ESTABLECIMIENTO_INVALIDO");
        }
        assertThat(guardar(establecimiento("1", " "), cookieAdmin).body()).contains("NOMBRE_ESTABLECIMIENTO_REQUERIDO");
        assertThat(jdbc.queryForObject("SELECT nombre FROM establecimiento", String.class))
                .isEqualTo("Establecimiento válido");
    }

    @Test
    void laDireccionDesglosadaQuePideElDteEsObligatoria() throws Exception {   // HU-08.02
        String base = establecimiento("1", "Matriz");
        assertThat(guardar(base.replace("01001", "1001"), cookieAdmin).body()).contains("CODIGO_POSTAL_INVALIDO");
        assertThat(guardar(base.replace("\"municipio\":\"Guatemala\"", "\"municipio\":\"\""), cookieAdmin).body())
                .contains("MUNICIPIO_REQUERIDO");
        assertThat(guardar(base.replace("\"departamento\":\"Guatemala\"", "\"departamento\":\"\""), cookieAdmin)
                .body()).contains("DEPARTAMENTO_REQUERIDO");
        assertThat(guardar(base, cookieAdmin).body()).contains("\"codigoPostal\":\"01001\"",
                "\"municipio\":\"Guatemala\"", "\"departamento\":\"Guatemala\"");
    }

    /** HU-08.02 y HU-12.01/12.02 crit. 2 y 3: el DTE se arma con la configuración guardada. */
    @Test
    void elDteUsaElEmisorYElEstablecimientoConfigurados() throws Exception {
        guardar(establecimiento("1", "Súper del Valle, zona 1"), cookieAdmin);
        assertThat(enviar("/api/configuracion/emisor", "PUT", """
                {"nit":"576937-K","razonSocial":"Supermercados del Valle, S.A.","nombreComercial":"Súper del Valle",\
                "direccionFiscal":"Zona 1, Guatemala","afiliacionIva":"GEN"}""", cookieAdmin).statusCode())
                .isEqualTo(200);
        LineaCalculada arroz = CalculadoraIva.linea(new BigDecimal("5.00"), 3);
        Venta venta = new Venta(1, 1, 1, 1, "Caja 1", 1, EstadoVenta.EN_CURSO, new BigDecimal("0.12"),
                OffsetDateTime.now(), null, List.of(new LineaVenta(1, 1, "7401", "Arroz 1 lb", 3,
                arroz.precioUnitario(), arroz.subtotalConIva(), arroz.base(), arroz.iva())));

        String xml = generacionDte.generar(venta, Receptor.consumidorFinal(), OffsetDateTime.now());

        assertThat(xml).contains("NITEmisor=\"576937K\"", "NombreEmisor=\"Supermercados del Valle, S.A.\"",
                "CodigoEstablecimiento=\"1\"", "NombreComercial=\"Súper del Valle, zona 1\"",
                "<dte:CodigoPostal>01001</dte:CodigoPostal>", "IDReceptor=\"CF\"", "<dte:GranTotal>15.00</dte:GranTotal>");
    }

    @Test
    void cadaSucursalTieneSuPropioEstablecimiento() throws Exception {   // crit. 3
        String codigoAjeno = String.valueOf(900_000 + SIGUIENTE.incrementAndGet());
        long otra = jdbc.queryForObject("INSERT INTO sucursal (codigo, nombre) VALUES (?, ?) RETURNING id", Long.class,
                "S" + codigoAjeno, "Sucursal de prueba " + codigoAjeno);
        jdbc.update("""
                INSERT INTO establecimiento (sucursal_id, codigo_sat, nombre, direccion, modificado_por)
                VALUES (?, ?, 'Otra sucursal', 'Zona 10', (SELECT id FROM usuario WHERE username = 'admin'))""",
                otra, codigoAjeno);

        HttpResponse<String> respuesta = guardar(establecimiento(codigoAjeno, "Matriz"), cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CODIGO_ESTABLECIMIENTO_DUPLICADO");
    }

    @Test
    void soloQuienModificaLaConfiguracionVeOCambiaElEstablecimiento() throws Exception {   // HU-12.15 crit. 3
        for (String sesion : new String[] {sesionDe(usuario(2)), sesionDe(usuario(3))}) {
            assertThat(enviar("/api/configuracion/establecimiento", "GET", null, sesion).statusCode()).isEqualTo(403);
            assertThat(guardar(establecimiento("1", "Matriz"), sesion).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void cadaCambioQuedaEnLaBitacoraConElValorAnteriorYElNuevo() throws Exception {   // HU-12.15 crit. 4
        guardar(establecimiento("1", "Nombre anterior"), cookieAdmin);

        guardar(establecimiento("1", "Nombre nuevo"), cookieAdmin);

        String cambios = jdbc.queryForObject("""
                SELECT cambios::text FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND exitoso AND entidad = 'establecimiento'
                ORDER BY secuencia DESC LIMIT 1""", String.class);
        assertThat(cambios).contains("\"nombre\"", "Nombre anterior", "Nombre nuevo").doesNotContain("\"codigoSat\"");
        long antes = jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class);
        guardar(establecimiento("1", "Nombre nuevo"), cookieAdmin);
        assertThat(jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class)).isEqualTo(antes);
    }

    // Apoyo

    private static String establecimiento(String codigo, String nombre) {
        return """
                {"codigoSat":"%s","nombre":"%s","direccion":"6a. avenida 5-10, zona 1, Guatemala",\
                "codigoPostal":"01001","municipio":"Guatemala","departamento":"Guatemala"}"""
                .formatted(codigo, nombre);
    }

    private HttpResponse<String> guardar(String cuerpo, String sesion) throws Exception {
        return enviar("/api/configuracion/establecimiento", "PUT", cuerpo, sesion);
    }

    private String usuario(int rolId) {
        String username = "es.u" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Usuario establecimiento', ?, ?, false)""", username, rolId, codificador.encode(
                rolId == 3 ? PIN : "Clave2026segura"));
        return username;
    }

    /** Supervisor con contraseña; Cajero con PIN en una caja vinculada. */
    private String sesionDe(String username) throws Exception {
        int rol = jdbc.queryForObject("SELECT rol_id FROM usuario WHERE username = ?", Integer.class, username);
        if (rol != 3) {
            return cookie(enviar("/api/auth/login", "POST", login(username, "contrasena", "Clave2026segura"), null),
                    "POS_SESION");
        }
        String nombre = "Caja S" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        String creada = enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin).body();
        int inicio = creada.indexOf("\"id\":") + 5;
        long caja = Long.parseLong(creada.substring(inicio, creada.indexOf(',', inicio)));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        return cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
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
