package gt.supermercado.pos.ventas;

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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-06.01 Iniciar una venta, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + VentasIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class VentasIntegracionTest {

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

    // Crit. 1 y 4: con la caja abierta, la venta queda lista y tiene un número interno único

    @Test
    void conElTurnoAbiertoSeIniciaUnaVentaVaciaConSuNumero() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();

        HttpResponse<String> respuesta = enviar("/api/ventas", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"EN_CURSO\"", "\"lineas\":[]", "\"articulos\":0",
                "\"subtotal\":0.00", "\"iva\":0.00", "\"total\":0.00", "\"cajaNombre\":\"%s\"".formatted(cajero.caja()));
        Map<String, Object> venta = jdbc.queryForMap("""
                SELECT v.numero, v.caja_id, v.usuario_id, v.turno_id, v.tasa_iva, t.estado AS estado_turno
                FROM venta v JOIN turno t ON t.id = v.turno_id WHERE v.id = ?""", idDe(respuesta));
        assertThat(venta).containsEntry("usuario_id", cajero.usuarioId()).containsEntry("estado_turno", "ABIERTO");
        assertThat(respuesta.body()).contains("\"numero\":" + venta.get("numero"));
        // HU-04.01 crit. 3: la tasa queda guardada en la venta.
        assertThat((BigDecimal) venta.get("tasa_iva")).isEqualByComparingTo("0.12");
    }

    @Test
    void iniciarOtraVezDevuelveLaMismaVentaEnCurso() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        long primera = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));

        long segunda = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        HttpResponse<String> actual = enviar("/api/ventas/actual", "GET", null, cajero.sesion());

        assertThat(segunda).isEqualTo(primera);
        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(idDe(actual)).isEqualTo(primera);
    }

    @Test
    void sinVentaIniciadaNoHayVentaActual() throws Exception {
        assertThat(enviar("/api/ventas/actual", "GET", null, cajeroConTurnoAbierto().sesion()).statusCode()).isEqualTo(204);
    }

    @Test
    void cadaVentaTieneUnNumeroDistinto() throws Exception {
        List<Long> numeros = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> venta = enviar("/api/ventas", "POST", null, cajeroConTurnoAbierto().sesion());
            numeros.add(jdbc.queryForObject("SELECT numero FROM venta WHERE id = ?", Long.class, idDe(venta)));
        }

        assertThat(numeros).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE tablename = 'venta' AND indexdef LIKE 'CREATE UNIQUE INDEX%(numero)'""",
                Integer.class)).isEqualTo(1);
    }

    // Crit. 2: sin caja abierta no se vende

    @Test
    void conLaCajaCerradaNoSeIniciaLaVenta() throws Exception {
        Cajero cajero = cajeroEnCaja();   // sin abrir el turno

        HttpResponse<String> respuesta = enviar("/api/ventas", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CERRADA", "Abra su turno");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venta WHERE usuario_id = ?", Integer.class, cajero.usuarioId()))
                .isZero();
    }

    @Test
    void enUnEquipoSinCajaNoSeIniciaLaVenta() throws Exception {
        String username = nuevoCajero();
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), null), "POS_SESION");

        HttpResponse<String> respuesta = enviar("/api/ventas", "POST", null, sesion);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CERRADA", "no está vinculado");
    }

    @Test
    void enElTurnoDeOtroUsuarioNoSeVende() throws Exception {
        Cajero duenoDelTurno = cajeroConTurnoAbierto();
        String otro = cookie(enviar("/api/auth/login-pin", "POST", login(nuevoCajero(), "pin", PIN),
                duenoDelTurno.equipo()), "POS_SESION");

        HttpResponse<String> respuesta = enviar("/api/ventas", "POST", null, otro);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CON_TURNO_DE_OTRO_USUARIO");
    }

    @Test
    void sinSesionNoHayVenta() throws Exception {
        assertThat(enviar("/api/ventas", "POST", null, null).statusCode()).isEqualTo(401);
        assertThat(enviar("/api/ventas/actual", "GET", null, null).statusCode()).isEqualTo(401);
    }

    // Un turno tiene como máximo una venta en curso

    @Test
    void variasPeticionesAlMismoTiempoIniciaUnaSolaVenta() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        List<Callable<Long>> tareas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tareas.add(() -> idDe(enviar("/api/ventas", "POST", null, cajero.sesion())));
        }

        List<Long> ids = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            for (Future<Long> id : hilos.invokeAll(tareas)) {
                ids.add(id.get());
            }
        }

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venta WHERE usuario_id = ?", Integer.class, cajero.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    void laBaseDeDatosImpideDosVentasEnCursoEnElMismoTurno() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        long venta = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        Map<String, Object> fila = jdbc.queryForMap("SELECT turno_id, caja_id, usuario_id FROM venta WHERE id = ?", venta);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO venta (turno_id, caja_id, usuario_id, tasa_iva) VALUES (?, ?, ?, 0.12)""",
                fila.get("turno_id"), fila.get("caja_id"), fila.get("usuario_id")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // Apoyo

    record Cajero(long usuarioId, String caja, String equipo, String sesion) {}

    /** Un cajero que inició sesión en una caja nueva, sin abrir el turno. */
    private Cajero cajeroEnCaja() throws Exception {
        String nombre = "Caja V" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = nuevoCajero();
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(username, "pin", PIN), equipo), "POS_SESION");
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, nombre, equipo, sesion);
    }

    private Cajero cajeroConTurnoAbierto() throws Exception {
        Cajero cajero = cajeroEnCaja();
        assertThat(enviar("/api/turnos", "POST", null, cajero.sesion()).statusCode()).isEqualTo(201);
        return cajero;
    }

    private String nuevoCajero() {
        String username = "ve.c" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero de venta', 3, ?, false)""", username, codificador.encode(PIN));
        return username;
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
