package gt.supermercado.pos.bitacora;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11.02 Guardar la información completa de cada evento, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + BitacoraCompletaIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class BitacoraCompletaIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
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
    private long cajaId;
    private String equipo;

    @BeforeEach
    void cajaVinculadaYCajero() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"admin\",\"contrasena\":\"" + CONTRASENA_ADMIN + "\"}", null), "POS_SESION");
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('bc.cajera', 'Cajera de la bitácora', 3, ?, false) ON CONFLICT (username) DO NOTHING""",
                codificador.encode(PIN));
        enviar("/api/cajas", "POST", "{\"nombre\":\"Caja Bitácora\"}", cookieAdmin);
        cajaId = jdbc.queryForObject("SELECT id FROM caja WHERE nombre = 'Caja Bitácora'", Long.class);
        equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
    }

    // Criterio 1: fecha, usuario, rol, caja, acción, módulo, resultado y descripción

    @Test
    void elInicioDeSesionEnUnaCajaGuardaTodaLaInformacion() throws Exception {
        long cajera = idDe("bc.cajera");
        long antes = ultimoId();

        enviar("/api/auth/login-pin", "POST", "{\"username\":\"bc.cajera\",\"pin\":\"" + PIN + "\"}", equipo);

        Map<String, Object> registro = registroDe(cajera, "INICIO_SESION", antes);
        assertThat(registro.get("ocurrido_en")).isNotNull();
        assertThat(registro.get("usuario_id")).isEqualTo(cajera);
        assertThat(registro.get("rol")).isEqualTo("CAJERO");
        assertThat(registro.get("caja_id")).isEqualTo(cajaId);
        assertThat(registro.get("modulo")).isEqualTo("seguridad");
        assertThat(registro.get("exitoso")).isEqualTo(true);
        assertThat((String) registro.get("descripcion")).isNotBlank();
    }

    @Test
    void elIntentoFallidoEnUnaCajaTambienGuardaLaCaja() throws Exception {
        long cajera = idDe("bc.cajera");
        long antes = ultimoId();

        enviar("/api/auth/login-pin", "POST", "{\"username\":\"bc.cajera\",\"pin\":\"9999\"}", equipo);

        Map<String, Object> registro = registroDe(cajera, "INICIO_SESION", antes);
        assertThat(registro.get("exitoso")).isEqualTo(false);
        assertThat(registro.get("caja_id")).isEqualTo(cajaId);
    }

    @Test
    void lasAccionesConSesionEnUnaCajaGuardanEsaCaja() throws Exception {
        long cajera = idDe("bc.cajera");
        String sesion = cookie(enviar("/api/auth/login-pin", "POST",
                "{\"username\":\"bc.cajera\",\"pin\":\"" + PIN + "\"}", equipo), "POS_SESION");
        long antes = ultimoId();

        enviar("/api/auth/logout", "POST", null, sesion);

        assertThat(registroDe(cajera, "CIERRE_SESION", antes).get("caja_id")).isEqualTo(cajaId);
    }

    @Test
    void fueraDeUnaCajaElEventoQuedaSinCaja() throws Exception {
        long admin = idDe("admin");
        long antes = ultimoId();

        enviar("/api/usuarios", "POST",
                "{\"nombreCompleto\":\"Sin caja\",\"username\":\"bc.sincaja\",\"rol\":\"CAJERO\",\"credencialTemporal\":\"5151\"}",
                cookieAdmin);

        assertThat(registroDe(admin, "USUARIO_CREADO", antes).get("caja_id")).isNull();
    }

    // Criterio 2: referencia al documento afectado

    @Test
    void guardaLaReferenciaAlDocumentoAfectado() throws Exception {
        long admin = idDe("admin");
        long antes = ultimoId();

        enviar("/api/usuarios", "POST",
                "{\"nombreCompleto\":\"Referencia\",\"username\":\"bc.referencia\",\"rol\":\"CAJERO\",\"credencialTemporal\":\"5252\"}",
                cookieAdmin);

        Map<String, Object> registro = registroDe(admin, "USUARIO_CREADO", antes);
        assertThat(registro.get("entidad")).isEqualTo("usuario");
        assertThat(registro.get("entidad_id")).isEqualTo(idDe("bc.referencia"));
    }

    // Criterio 3: fecha y hora del servidor

    @Test
    void laFechaLaPoneElServidorDeBaseDeDatos() throws Exception {
        long cajera = idDe("bc.cajera");
        long antes = ultimoId();
        OffsetDateTime desde = jdbc.queryForObject("SELECT now()", OffsetDateTime.class);

        enviar("/api/auth/login-pin", "POST", "{\"username\":\"bc.cajera\",\"pin\":\"" + PIN + "\"}", equipo);

        OffsetDateTime hasta = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        OffsetDateTime ocurrido = (OffsetDateTime) jdbc.queryForObject(
                "SELECT ocurrido_en FROM bitacora WHERE id = ?", OffsetDateTime.class,
                registroDe(cajera, "INICIO_SESION", antes).get("id"));
        assertThat(ocurrido).isBetween(desde, hasta);
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'bitacora' AND column_name = 'ocurrido_en'""", String.class)).isEqualTo("now()");
    }

    // Criterio 4: nunca contraseñas ni PIN

    @Test
    void ningunaOperacionDejaContrasenasNiPinEnNingunaColumnaDeLaBitacora() throws Exception {
        String contrasena = "SecretoUnico2026x";
        String contrasenaNueva = "OtroSecreto2027y";
        String pin = "713529";
        String pinRestablecido = "864213";

        enviar("/api/usuarios", "POST", ("{\"nombreCompleto\":\"Secreto\",\"username\":\"bc.secreto\",\"rol\":\"SUPERVISOR\","
                + "\"credencialTemporal\":\"%s\"}").formatted(contrasena), cookieAdmin);
        enviar("/api/usuarios", "POST", ("{\"nombreCompleto\":\"Secreto PIN\",\"username\":\"bc.secreto.pin\",\"rol\":\"CAJERO\","
                + "\"credencialTemporal\":\"%s\"}").formatted(pin), cookieAdmin);
        enviar("/api/auth/login", "POST", "{\"username\":\"bc.secreto\",\"contrasena\":\"%sMal\"}".formatted(contrasena), null);
        enviar("/api/auth/login", "POST", "{\"username\":\"%s\",\"contrasena\":\"x\"}".formatted(contrasena), null);
        String sesion = cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"bc.secreto\",\"contrasena\":\"%s\"}".formatted(contrasena), null), "POS_SESION");
        enviar("/api/auth/credencial", "PUT", "{\"actual\":\"%s\",\"nueva\":\"%s\",\"confirmacion\":\"%s\"}"
                .formatted(contrasena, contrasenaNueva, contrasenaNueva), sesion);
        enviar("/api/usuarios/" + idDe("bc.secreto.pin") + "/credencial", "POST",
                "{\"credencialTemporal\":\"%s\"}".formatted(pinRestablecido), cookieAdmin);
        enviar("/api/auth/login-pin", "POST", "{\"username\":\"bc.secreto.pin\",\"pin\":\"%s\"}".formatted(pinRestablecido), equipo);

        // Se revisan todas las columnas salvo hash y hash_anterior: son SHA-256 en hexadecimal (128 caracteres al azar
        // por fila) y, con miles de registros, contienen por casualidad los dígitos de un PIN de vez en cuando. No
        // pueden guardar un secreto: los calcula el trigger a partir de las demás columnas, que sí se revisan.
        for (String secreto : new String[] {contrasena, contrasenaNueva, pin, pinRestablecido}) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM bitacora b WHERE (to_jsonb(b) - 'hash' - 'hash_anterior')::text LIKE ?""",
                    Integer.class, "%" + secreto + "%")).as("la bitácora no debe contener '%s'", secreto).isZero();
        }
    }

    private Map<String, Object> registroDe(long usuarioId, String accion, long desdeId) {
        return jdbc.queryForMap("""
                SELECT id, ocurrido_en, usuario_id, rol, caja_id, modulo, accion, exitoso, descripcion, entidad, entidad_id
                FROM bitacora WHERE usuario_id = ? AND accion = ? AND id > ? ORDER BY id DESC LIMIT 1""",
                usuarioId, accion, desdeId);
    }

    private long ultimoId() {
        return jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM bitacora", Long.class);
    }

    private long idDe(String username) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
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
