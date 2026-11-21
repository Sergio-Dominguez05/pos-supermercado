package gt.supermercado.pos.bitacora;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-11.10 Conservar la bitácora, contra PostgreSQL real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ConservacionBitacoraIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ConservacionBitacoraIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;
    private long adminId;

    @BeforeEach
    void preparar() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", CONTRASENA_ADMIN), null));
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
    }

    @AfterEach
    void plazoPorOmision() {
        jdbc.update("UPDATE bitacora_conservacion SET plazo_anios = 5 WHERE id = 1");
    }

    @Test
    void elPlazoEsConfigurableYNuncaMenorAlMinimo() throws Exception {   // crit. 1
        HttpResponse<String> estado = enviar("/api/configuracion/bitacora", "GET", null, cookieAdmin);
        assertThat(estado.statusCode()).isEqualTo(200);
        assertThat(estado.body()).contains("\"plazoAnios\":5", "\"plazoMinimoAnios\":4", "\"registrosActivos\":");

        HttpResponse<String> corto = enviar("/api/configuracion/bitacora", "PUT", "{\"plazoAnios\":3}", cookieAdmin);
        assertThat(corto.statusCode()).isEqualTo(422);
        assertThat(corto.body()).contains("PLAZO_CONSERVACION_INVALIDO");

        HttpResponse<String> cambio = enviar("/api/configuracion/bitacora", "PUT", "{\"plazoAnios\":7}", cookieAdmin);
        assertThat(cambio.statusCode()).isEqualTo(200);
        assertThat(cambio.body()).contains("\"plazoAnios\":7");
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Guatemala"));
        assertThat(cambio.body()).contains("\"plazoCumplidoAntesDe\":\"" + hoy.minusYears(7) + "\"");
        assertThat(jdbc.queryForMap("""
                SELECT cambios -> 'plazoConservacionAnios' ->> 'antes' AS antes,
                       cambios -> 'plazoConservacionAnios' ->> 'despues' AS despues
                FROM bitacora WHERE accion = 'PARAMETRO_MODIFICADO' AND entidad = 'bitacora_conservacion' AND exitoso
                ORDER BY secuencia DESC LIMIT 1""")).containsEntry("antes", "5").containsEntry("despues", "7");
    }

    @Test
    void losRegistrosAntiguosSeArchivanYSeSiguenConsultandoYVerificando() throws Exception {   // crit. 2
        String marca = "Registro antiguo " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO bitacora (ocurrido_en, usuario_id, rol, modulo, accion, exitoso, descripcion)
                VALUES ('2020-03-10T16:00:00Z', ?, 'ADMIN', 'bitacora', 'BITACORA_CONSULTADA', true, ?),
                       ('2020-03-11T16:00:00Z', ?, 'ADMIN', 'bitacora', 'BITACORA_CONSULTADA', false, ?)""",
                adminId, marca, adminId, marca);
        // El último registro de la cadena nunca se archiva (su hash es la cabeza): detrás va uno reciente.
        jdbc.update("""
                INSERT INTO bitacora (usuario_id, rol, modulo, accion, exitoso, descripcion)
                VALUES (?, 'ADMIN', 'bitacora', 'BITACORA_CONSULTADA', true, 'Registro reciente')""", adminId);
        long secuencia = jdbc.queryForObject("SELECT min(secuencia) FROM bitacora WHERE descripcion = ?", Long.class,
                marca);
        String hash = jdbc.queryForObject("SELECT encode(hash, 'hex') FROM bitacora WHERE secuencia = ?", String.class,
                secuencia);

        HttpResponse<String> archivo = enviar("/api/configuracion/bitacora/archivo", "POST",
                "{\"antesDe\":\"2021-01-01\"}", cookieAdmin);

        assertThat(archivo.statusCode()).as(archivo.body()).isEqualTo(200);
        assertThat(archivo.body()).contains("\"archivados\":");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE descripcion = ?", Integer.class, marca))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_archivo WHERE descripcion = ?", Integer.class,
                marca)).isEqualTo(2);
        // Se movieron con su misma secuencia y su mismo hash: nada se reescribió.
        assertThat(jdbc.queryForObject("SELECT encode(hash, 'hex') FROM bitacora_archivo WHERE secuencia = ?",
                String.class, secuencia)).isEqualTo(hash);
        // Se siguen consultando con los filtros de siempre, y la cadena sigue íntegra.
        assertThat(enviar("/api/bitacora?desde=2020-03-10&hasta=2020-03-11&modulo=bitacora", "GET", null, cookieAdmin)
                .body()).contains(marca, "\"total\":2");
        long id = jdbc.queryForObject("SELECT id FROM bitacora_archivo WHERE secuencia = ?", Long.class, secuencia);
        assertThat(enviar("/api/bitacora/" + id, "GET", null, cookieAdmin).body()).contains(marca);
        assertThat(enviar("/api/bitacora/verificacion", "GET", null, cookieAdmin).body())
                .contains("\"integra\":true", "\"problemas\":[]");
        // HU-11.09: la exportación también incluye lo archivado.
        assertThat(enviar("/api/bitacora/exportar?formato=CSV&desde=2020-03-10&hasta=2020-03-11&modulo=bitacora", "GET",
                null, cookieAdmin).body()).contains(marca);
        assertThat(jdbc.queryForObject("""
                SELECT cambios ->> 'antesDe' FROM bitacora WHERE accion = 'BITACORA_ARCHIVADA' AND exitoso
                ORDER BY secuencia DESC LIMIT 1""", String.class)).isEqualTo("2021-01-01");
    }

    @Test
    void nadaSeBorraNiAunConLaMarcaDeArchivoYElArchivoTampocoSeModifica() {   // crit. 3
        assertThatThrownBy(() -> jdbc.execute((ConnectionCallback<Void>) conexion -> {
            conexion.setAutoCommit(false);
            try (Statement sql = conexion.createStatement()) {
                sql.execute("SET LOCAL pos.archivando_bitacora = 'si'");
                sql.execute("DELETE FROM bitacora WHERE secuencia = (SELECT max(secuencia) - 1 FROM bitacora)");
            } finally {
                conexion.rollback();
                conexion.setAutoCommit(true);
            }
            return null;
        })).rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.update("UPDATE bitacora_archivo SET descripcion = 'cambiada'"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM bitacora_archivo"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE bitacora_archivo"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
    }

    @Test
    void soloSeArchivaLoDeHaceMasDeNoventaDiasYSoloElAdministrador() throws Exception {
        LocalDate reciente = LocalDate.now(ZoneId.of("America/Guatemala")).minusDays(30);
        HttpResponse<String> rechazado = enviar("/api/configuracion/bitacora/archivo", "POST",
                "{\"antesDe\":\"%s\"}".formatted(reciente), cookieAdmin);
        assertThat(rechazado.statusCode()).isEqualTo(422);
        assertThat(rechazado.body()).contains("ARCHIVO_DEMASIADO_RECIENTE");
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT archivar_bitacora(now() - interval '10 days')", Long.class))
                .rootCause().hasMessageContaining("más de 90 días");

        String username = "con.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor de conservación', 2, ?, false)""", username, codificador.encode("Clave2026segura"));
        String supervisor = cookie(enviar("/api/auth/login", "POST", login(username, "Clave2026segura"), null));
        assertThat(enviar("/api/configuracion/bitacora", "GET", null, supervisor).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/configuracion/bitacora", "PUT", "{\"plazoAnios\":6}", supervisor).statusCode())
                .isEqualTo(403);
        assertThat(enviar("/api/configuracion/bitacora/archivo", "POST", "{\"antesDe\":\"2021-01-01\"}", supervisor)
                .statusCode()).isEqualTo(403);
    }

    private static String login(String username, String contrasena) {
        return "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena);
    }

    private static String cookie(HttpResponse<String> respuesta) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("POS_SESION=")).findFirst().orElseThrow();
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
