package gt.supermercado.pos.caja;

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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.MINUTES;

/** HU-09.01 Abrir el turno de caja, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + TurnosIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class TurnosIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

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
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
    }

    // Crit. 1: al iniciar sesión en una caja cerrada se pide abrir el turno

    @Test
    void enUnaCajaCerradaNoHayTurnoActualYSePuedeAbrir() throws Exception {
        Caja caja = nuevaCaja();
        String sesion = sesionDeCajero(nuevoCajero(), caja);

        assertThat(enviar("/api/turnos/actual", "GET", null, sesion).statusCode()).isEqualTo(204);
    }

    // Crit. 2 y 3: caja, usuario y fecha y hora; sin fondo inicial

    @Test
    void laAperturaRegistraCajaUsuarioYFechaYHoraSinFondoInicial() throws Exception {
        Caja caja = nuevaCaja();
        String cajero = nuevoCajero();
        String sesion = sesionDeCajero(cajero, caja);

        HttpResponse<String> respuesta = enviar("/api/turnos", "POST", null, sesion);

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body()).contains("\"cajaNombre\":\"%s\"".formatted(caja.nombre()),
                "\"usuarioNombre\":\"Cajero de turno\"", "\"estado\":\"ABIERTO\"", "\"abiertoEn\":")
                .doesNotContain("fondo");
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT t.caja_id, u.username, t.estado, t.abierto_en FROM turno t JOIN usuario u ON u.id = t.usuario_id
                WHERE t.caja_id = ?""", caja.id());
        assertThat(fila).containsEntry("caja_id", caja.id()).containsEntry("username", cajero)
                .containsEntry("estado", "ABIERTO");
        assertThat(jdbc.queryForObject("SELECT abierto_en FROM turno WHERE caja_id = ?", OffsetDateTime.class, caja.id()))
                .isCloseTo(OffsetDateTime.now(), within(1, MINUTES));
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'turno'""", String.class))
                .noneMatch(columna -> columna.contains("fondo"));
    }

    @Test
    void despuesDeAbrirElTurnoActualEsElDelUsuario() throws Exception {
        Caja caja = nuevaCaja();
        String sesion = sesionDeCajero(nuevoCajero(), caja);
        enviar("/api/turnos", "POST", null, sesion);

        HttpResponse<String> actual = enviar("/api/turnos/actual", "GET", null, sesion);

        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(actual.body()).contains("\"cajaId\":" + caja.id(), "\"estado\":\"ABIERTO\"", "\"propio\":true");
    }

    @Test
    void otroUsuarioEnLaMismaCajaVeQueElTurnoNoEsSuyo() throws Exception {
        Caja caja = nuevaCaja();
        enviar("/api/turnos", "POST", null, sesionDeCajero(nuevoCajero(), caja));

        HttpResponse<String> actual = enviar("/api/turnos/actual", "GET", null, sesionDeCajero(nuevoCajero(), caja));

        assertThat(actual.body()).contains("\"propio\":false", "\"usuarioNombre\":\"Cajero de turno\"");
    }

    // Crit. 4: bitácora

    @Test
    void laAperturaQuedaEnLaBitacoraConLaCaja() throws Exception {
        Caja caja = nuevaCaja();
        String cajero = nuevoCajero();
        long id = idDe(enviar("/api/turnos", "POST", null, sesionDeCajero(cajero, caja)));

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT u.username, b.descripcion, b.caja_id, b.exitoso FROM bitacora b JOIN usuario u ON u.id = b.usuario_id
                WHERE b.accion = 'TURNO_ABIERTO' AND b.entidad = 'turno' AND b.entidad_id = ?""", id);
        assertThat(registro).containsEntry("username", cajero).containsEntry("exitoso", true)
                .containsEntry("caja_id", caja.id())
                .containsEntry("descripcion", "Turno abierto en la caja '%s'".formatted(caja.nombre()));
    }

    // Reglas de apertura

    @Test
    void sinCajaVinculadaNoSePuedeAbrirTurno() throws Exception {
        String cajero = nuevoCajero();
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), null), "POS_SESION");

        HttpResponse<String> respuesta = enviar("/api/turnos", "POST", null, sesion);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("SIN_CAJA");
        assertThat(enviar("/api/turnos/actual", "GET", null, sesion).statusCode()).isEqualTo(204);
    }

    @Test
    void noSeAbreDosVecesElMismoTurno() throws Exception {
        String sesion = sesionDeCajero(nuevoCajero(), nuevaCaja());
        enviar("/api/turnos", "POST", null, sesion);

        assertThat(enviar("/api/turnos", "POST", null, sesion).body()).contains("TURNO_YA_ABIERTO");
    }

    @Test
    void enUnaCajaConElTurnoDeOtroUsuarioNoSeAbreOtro() throws Exception {
        Caja caja = nuevaCaja();
        enviar("/api/turnos", "POST", null, sesionDeCajero(nuevoCajero(), caja));

        HttpResponse<String> segundo = enviar("/api/turnos", "POST", null, sesionDeCajero(nuevoCajero(), caja));

        assertThat(segundo.statusCode()).isEqualTo(422);
        assertThat(segundo.body()).contains("CAJA_CON_TURNO_DE_OTRO_USUARIO", "Cajero de turno");
    }

    @Test
    void unUsuarioConTurnoAbiertoNoAbreOtroEnOtraCaja() throws Exception {
        String cajero = nuevoCajero();
        Caja primera = nuevaCaja();
        enviar("/api/turnos", "POST", null, sesionDeCajero(cajero, primera));

        HttpResponse<String> otra = enviar("/api/turnos", "POST", null, sesionDeCajero(cajero, nuevaCaja()));

        assertThat(otra.statusCode()).isEqualTo(422);
        assertThat(otra.body()).contains("TURNO_ABIERTO_EN_OTRA_CAJA", primera.nombre());
    }

    @Test
    void laBaseDeDatosImpideDosTurnosAbiertosEnLaMismaCaja() throws Exception {
        Caja caja = nuevaCaja();
        long uno = idDeUsuario(nuevoCajero());
        long dos = idDeUsuario(nuevoCajero());
        jdbc.update("INSERT INTO turno (caja_id, usuario_id) VALUES (?, ?)", caja.id(), uno);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO turno (caja_id, usuario_id) VALUES (?, ?)", caja.id(), dos))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // Pendientes heredados: HU-01.08 crit. 3 y HU-12.05 crit. 3

    @Test
    void noSeDesactivaAUnUsuarioConSuCajaAbierta() throws Exception {
        String cajero = nuevoCajero();
        enviar("/api/turnos", "POST", null, sesionDeCajero(cajero, nuevaCaja()));

        HttpResponse<String> respuesta = enviar("/api/usuarios/" + idDeUsuario(cajero) + "/desactivar", "POST", null,
                cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_ABIERTA");
    }

    @Test
    void noSeDesactivaUnaCajaConUnTurnoAbierto() throws Exception {
        Caja caja = nuevaCaja();
        enviar("/api/turnos", "POST", null, sesionDeCajero(nuevoCajero(), caja));

        HttpResponse<String> respuesta = enviar("/api/cajas/" + caja.id() + "/desactivar", "POST", null, cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CON_TURNO_ABIERTO");
    }

    // Permisos

    @Test
    void sinSesionNoSeConsultaNiSeAbreTurno() throws Exception {
        assertThat(enviar("/api/turnos/actual", "GET", null, null).statusCode()).isEqualTo(401);
        assertThat(enviar("/api/turnos", "POST", null, null).statusCode()).isEqualTo(401);
    }

    // Apoyo

    record Caja(long id, String nombre, String equipo) {}

    private Caja nuevaCaja() throws Exception {
        String nombre = "Caja T" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        return new Caja(id, nombre, equipo);
    }

    /** Un cajero nuevo en cada prueba: un turno abierto no se cierra todavía (HU-09.08). */
    private String nuevoCajero() {
        String username = "tu.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de turno', 3, ?, false)""", username, codificador.encode(PIN));
        return username;
    }

    private String sesionDeCajero(String username, Caja caja) throws Exception {
        return cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), caja.equipo()), "POS_SESION");
    }

    private long idDeUsuario(String username) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private static String login(String username, String campo, String valor) {
        return "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor);
    }

    private static long idDe(HttpResponse<String> creada) {
        String cuerpo = creada.body();
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
