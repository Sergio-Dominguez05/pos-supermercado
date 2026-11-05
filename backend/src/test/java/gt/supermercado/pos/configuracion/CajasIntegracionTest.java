package gt.supermercado.pos.configuracion;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-12.05 Registrar las cajas, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CajasIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CajasIntegracionTest {

    /** La misma que usan las pruebas de seguridad, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = cookie(login("/api/auth/login", "admin", "contrasena", CONTRASENA_ADMIN, null),
                "POS_SESION");
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('cj.cajera', 'Cajera de caja', 3, ?, false) ON CONFLICT (username) DO NOTHING""",
                codificador.encode(PIN));
    }

    // Criterios 1 y 2: crear y editar con nombre y serie de facturación

    @Test
    void seCreaUnaCajaConNombreYSerieEnLaSucursalMatriz() throws Exception {
        HttpResponse<String> respuesta = crear("  Caja Rápida 1 ", "a01");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body()).contains("\"nombre\":\"Caja Rápida 1\"", "\"serieFel\":\"A01\"",
                "\"activo\":true", "\"equipoVinculado\":false");
        assertThat(jdbc.queryForObject("""
                SELECT s.codigo FROM caja c JOIN sucursal s ON s.id = c.sucursal_id WHERE c.nombre = 'Caja Rápida 1'""",
                String.class)).isEqualTo("MATRIZ");
        assertThat(enviar("/api/cajas", "GET", null, cookieAdmin).body()).contains("Caja Rápida 1");
    }

    @Test
    void laSerieEsOpcional() throws Exception {
        HttpResponse<String> respuesta = crear("Caja sin serie", "  ");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body()).contains("\"serieFel\":null");
    }

    @Test
    void elNombreNoSeRepiteAunqueCambienLasMayusculas() throws Exception {
        crear("Caja Repetida", null);

        HttpResponse<String> repetida = crear("CAJA repetida", null);

        assertThat(repetida.statusCode()).isEqualTo(422);
        assertThat(repetida.body()).contains("CAJA_DUPLICADA", "Ya existe una caja con ese nombre.");
    }

    // #136: los espacios tampoco distinguen una caja de otra

    @Test
    void elNombreNoSeRepiteAunqueCambienLosEspacios() throws Exception {
        crear("Caja Espacios", null);

        assertThat(crear("cajaespacios", null).body()).contains("CAJA_DUPLICADA");
        assertThat(crear(" C a j a   E s p a c i o s ", null).body()).contains("CAJA_DUPLICADA");
    }

    @Test
    void alEditarTampocoSePuedeUsarElNombreDeOtraCajaConOtrosEspacios() throws Exception {
        crear("Caja Norte", null);
        long otra = idDe(crear("Caja Sur", null));

        assertThat(editar(otra, "CAJANORTE", null).body()).contains("CAJA_DUPLICADA");
    }

    @Test
    void elNombreSeGuardaConUnSoloEspacioEntrePalabras() throws Exception {
        HttpResponse<String> creada = crear("  Caja    Express  ", null);

        assertThat(creada.statusCode()).isEqualTo(201);
        assertThat(creada.body()).contains("\"nombre\":\"Caja Express\"");
    }

    @Test
    void laBaseDeDatosTambienImpideDuplicadosQueSoloDifierenEnEspacios() {
        jdbc.update("INSERT INTO caja (sucursal_id, nombre) SELECT id, 'Caja Directa' FROM sucursal WHERE codigo = 'MATRIZ'");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO caja (sucursal_id, nombre) SELECT id, 'CAJADIRECTA' FROM sucursal WHERE codigo = 'MATRIZ'"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void elNombreYLaSerieDebenTenerUnFormatoValido() throws Exception {
        assertThat(crear("   ", null).body()).contains("NOMBRE_CAJA_INVALIDO");
        assertThat(crear("x".repeat(41), null).body()).contains("NOMBRE_CAJA_INVALIDO");
        assertThat(crear("Caja serie mala", "A 01").body()).contains("SERIE_INVALIDA");
        assertThat(crear("Caja serie larga", "A".repeat(21)).body()).contains("SERIE_INVALIDA");
    }

    @Test
    void seEditaElNombreYLaSerieYQuedaEnLaBitacoraConAntesYDespues() throws Exception {
        long id = idDe(crear("Caja Vieja", "V1"));

        HttpResponse<String> respuesta = editar(id, "Caja Nueva", "N1");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"nombre\":\"Caja Nueva\"", "\"serieFel\":\"N1\"");
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT cambios->'nombre'->>'antes' AS antes, cambios->'nombre'->>'despues' AS despues,
                       cambios->'serieFel'->>'despues' AS serie
                FROM bitacora WHERE accion = 'CAJA_EDITADA' AND entidad_id = ? ORDER BY id DESC LIMIT 1""", id);
        assertThat(registro).containsEntry("antes", "Caja Vieja").containsEntry("despues", "Caja Nueva")
                .containsEntry("serie", "N1");
    }

    @Test
    void alEditarNoSePuedeUsarElNombreDeOtraCaja() throws Exception {
        crear("Caja Ocupada", null);
        long id = idDe(crear("Caja Libre", null));

        assertThat(editar(id, "caja ocupada", null).body()).contains("CAJA_DUPLICADA");
        assertThat(editar(id, "CAJA LIBRE", null).statusCode()).as("su propio nombre con otras mayúsculas").isEqualTo(200);
    }

    // Criterio 1: activar y desactivar (el crit. 3 se prueba en GestionCajasTest)

    @Test
    void seDesactivaYSeActivaYQuedaEnLaBitacora() throws Exception {
        long id = idDe(crear("Caja Temporal", null));

        assertThat(enviar("/api/cajas/" + id + "/desactivar", "POST", null, cookieAdmin).body()).contains("\"activo\":false");
        assertThat(enviar("/api/cajas/" + id + "/activar", "POST", null, cookieAdmin).body()).contains("\"activo\":true");
        assertThat(jdbc.queryForList("""
                SELECT accion FROM bitacora WHERE entidad = 'caja' AND entidad_id = ? AND exitoso ORDER BY id""",
                String.class, id)).containsExactly("CAJA_CREADA", "CAJA_DESACTIVADA", "CAJA_ACTIVADA");
    }

    // Criterio 4: cada equipo físico queda vinculado a una caja

    @Test
    void vincularElEquipoDejaUnaCookieHttpOnlyYSoloGuardaSuHash() throws Exception {
        long id = idDe(crear("Caja Equipo", null));

        HttpResponse<String> respuesta = enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin);

        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("POS_EQUIPO=")).findFirst().orElseThrow();
        String token = setCookie.substring("POS_EQUIPO=".length(), setCookie.indexOf(';'));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Max-Age=");
        assertThat(respuesta.body()).contains("\"equipoVinculado\":true").doesNotContain(token);
        byte[] guardado = jdbc.queryForObject("SELECT token_equipo_hash FROM caja WHERE id = ?", byte[].class, id);
        assertThat(guardado).hasSize(32);
        assertThat(new String(guardado)).doesNotContain(token);
    }

    @Test
    void elEquipoSabeAQueCajaEstaVinculado() throws Exception {
        long id = idDe(crear("Caja Consulta", null));
        String equipo = vincular(id);

        assertThat(enviar("/api/cajas/equipo", "GET", null, cookieAdmin + "; " + equipo).body())
                .contains("\"nombre\":\"Caja Consulta\"");
        assertThat(enviar("/api/cajas/equipo", "GET", null, cookieAdmin).statusCode()).isEqualTo(204);
    }

    @Test
    void quienIniciaSesionEnUnEquipoVinculadoQuedaEnEsaCaja() throws Exception {
        long id = idDe(crear("Caja Ventas", null));
        String equipo = vincular(id);

        String sesion = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, equipo), "POS_SESION");

        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).body()).contains("\"cajaNombre\":\"Caja Ventas\"");
        assertThat(jdbc.queryForObject("""
                SELECT caja_id FROM sesion WHERE usuario_id = (SELECT id FROM usuario WHERE username = 'cj.cajera')
                ORDER BY id DESC LIMIT 1""", Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("""
                SELECT descripcion FROM bitacora WHERE accion = 'INICIO_SESION' AND exitoso
                  AND usuario_id = (SELECT id FROM usuario WHERE username = 'cj.cajera') ORDER BY id DESC LIMIT 1""",
                String.class)).isEqualTo("Inicio de sesión con PIN en la caja 'Caja Ventas'");
    }

    @Test
    void sinEquipoVinculadoLaSesionNoTieneCaja() throws Exception {
        String sesion = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, null), "POS_SESION");

        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).body()).contains("\"cajaNombre\":null");
    }

    @Test
    void alVincularOtroEquipoElAnteriorDejaDeEstarAsociado() throws Exception {
        long id = idDe(crear("Caja Reemplazo", null));
        String equipoViejo = vincular(id);
        String equipoNuevo = vincular(id);

        String sesionVieja = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, equipoViejo), "POS_SESION");
        String sesionNueva = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, equipoNuevo), "POS_SESION");

        assertThat(enviar("/api/auth/sesion", "GET", null, sesionVieja).body()).contains("\"cajaNombre\":null");
        assertThat(enviar("/api/auth/sesion", "GET", null, sesionNueva).body()).contains("\"cajaNombre\":\"Caja Reemplazo\"");
    }

    @Test
    void unaCajaInactivaNoAsociaSesionesNiAdmiteVincularEquipos() throws Exception {
        long id = idDe(crear("Caja Cerrada", null));
        String equipo = vincular(id);
        enviar("/api/cajas/" + id + "/desactivar", "POST", null, cookieAdmin);

        String sesion = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, equipo), "POS_SESION");

        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).body()).contains("\"cajaNombre\":null");
        HttpResponse<String> vinculo = enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin);
        assertThat(vinculo.statusCode()).isEqualTo(422);
        assertThat(vinculo.body()).contains("CAJA_INACTIVA");
    }

    // Permisos y datos inválidos

    @Test
    void soloQuienTienePermisoDeConfiguracionAdministraLasCajas() throws Exception {
        long id = idDe(crear("Caja Protegida", null));
        String cajera = cookie(login("/api/auth/login-pin", "cj.cajera", "pin", PIN, null), "POS_SESION");

        assertThat(enviar("/api/cajas", "GET", null, cajera).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/cajas", "POST", "{\"nombre\":\"Intrusa\"}", cajera).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/cajas/" + id + "/equipo", "POST", null, cajera).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/cajas", "GET", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void unaCajaQueNoExisteDa404() throws Exception {
        assertThat(enviar("/api/cajas/999999", "GET", null, cookieAdmin).statusCode()).isEqualTo(404);
        assertThat(enviar("/api/cajas/999999/equipo", "POST", null, cookieAdmin).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> crear(String nombre, String serie) throws Exception {
        return enviar("/api/cajas", "POST", cuerpo(nombre, serie), cookieAdmin);
    }

    private HttpResponse<String> editar(long id, String nombre, String serie) throws Exception {
        return enviar("/api/cajas/" + id, "PUT", cuerpo(nombre, serie), cookieAdmin);
    }

    private static String cuerpo(String nombre, String serie) {
        return serie == null
                ? "{\"nombre\":\"%s\"}".formatted(nombre)
                : "{\"nombre\":\"%s\",\"serieFel\":\"%s\"}".formatted(nombre, serie);
    }

    /** Vincula y devuelve la cookie del equipo lista para el encabezado {@code Cookie}. */
    private String vincular(long id) throws Exception {
        return cookie(enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
    }

    private long idDe(HttpResponse<String> creada) {
        String cuerpo = creada.body();
        int inicio = cuerpo.indexOf("\"id\":") + 5;
        return Long.parseLong(cuerpo.substring(inicio, cuerpo.indexOf(',', inicio)));
    }

    private HttpResponse<String> login(String ruta, String username, String campo, String valor, String cookieEquipo)
            throws Exception {
        return enviar(ruta, "POST", "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), cookieEquipo);
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
