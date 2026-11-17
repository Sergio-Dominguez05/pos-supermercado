package gt.supermercado.pos.configuracion;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-12.01 Configurar los datos del emisor, contra PostgreSQL real con las migraciones. Los datos son una sola fila:
 * cada prueba guarda primero los que necesita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + EmisorIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class EmisorIntegracionTest {

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

    @Test
    void seRegistranLosDatosFiscalesConElNitNormalizado() throws Exception {   // crit. 1, 2 y HU-05.03 crit. 3
        HttpResponse<String> guardado = guardar(emisor(" 576937-k ", "Supermercados del Valle, S.A."), cookieAdmin);

        assertThat(guardado.statusCode()).isEqualTo(200);
        assertThat(guardado.body()).contains("\"configurado\":true", "\"nit\":\"576937K\"",
                "\"razonSocial\":\"Supermercados del Valle, S.A.\"", "\"nombreComercial\":\"Súper del Valle\"",
                "\"telefono\":\"2222-3333\"", "\"correo\":\"facturas@delvalle.com\"");
        assertThat(enviar("/api/configuracion/emisor", "GET", null, cookieAdmin).body()).contains("\"nit\":\"576937K\"");
        assertThat(jdbc.queryForObject("SELECT nit FROM emisor", String.class)).isEqualTo("576937K");
    }

    @Test
    void unNitConElVerificadorEquivocadoNoSeAcepta() throws Exception {   // crit. 2
        guardar(emisor("1234560", "Empresa válida, S.A."), cookieAdmin);

        HttpResponse<String> respuesta = guardar(emisor("1234561", "Empresa inválida, S.A."), cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("NIT_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT razon_social FROM emisor", String.class))
                .isEqualTo("Empresa válida, S.A.");
    }

    @Test
    void losCamposObligatoriosYElFormatoDeTelefonoYCorreoSeValidan() throws Exception {   // crit. 1
        String base = emisor("1234560", "Empresa, S.A.");
        assertThat(guardar(base.replace("\"Súper del Valle\"", "\" \""), cookieAdmin).body())
                .contains("NOMBRE_COMERCIAL_REQUERIDO");
        assertThat(guardar(base.replace("\"2222-3333\"", "\"22\""), cookieAdmin).body()).contains("TELEFONO_INVALIDO");
        assertThat(guardar(base.replace("\"facturas@delvalle.com\"", "\"facturas\""), cookieAdmin).body())
                .contains("CORREO_INVALIDO");
        // HU-08.02: la afiliación al IVA es obligatoria, porque el DTE la pide.
        assertThat(guardar(base.replace(",\"afiliacionIva\":\"GEN\"", ""), cookieAdmin).body())
                .contains("AFILIACION_IVA_REQUERIDA");
        assertThat(guardar(base, cookieAdmin).body()).contains("\"afiliacionIva\":\"GEN\"");
        // Teléfono y correo son opcionales.
        assertThat(guardar(base.replace("\"2222-3333\"", "null").replace("\"facturas@delvalle.com\"", "\"\""),
                cookieAdmin).body()).contains("\"telefono\":null", "\"correo\":null");
    }

    @Test
    void soloQuienModificaLaConfiguracionVeOCambiaElEmisor() throws Exception {   // HU-12.15 crit. 3
        for (String sesion : new String[] {sesionDe(usuario(2)), sesionDe(usuario(3))}) {
            assertThat(enviar("/api/configuracion/emisor", "GET", null, sesion).statusCode()).isEqualTo(403);
            assertThat(guardar(emisor("1234560", "Empresa, S.A."), sesion).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void cadaCambioQuedaEnLaBitacoraConElValorAnteriorYElNuevo() throws Exception {   // HU-12.15 crit. 4
        guardar(emisor("1234560", "Razón anterior, S.A."), cookieAdmin);

        guardar(emisor("1234560", "Razón nueva, S.A."), cookieAdmin);

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT modulo, entidad, cambios::text AS cambios FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND exitoso AND entidad = 'emisor'
                ORDER BY secuencia DESC LIMIT 1""");
        assertThat(registro).containsEntry("modulo", "configuracion");
        assertThat((String) registro.get("cambios")).contains("\"razonSocial\"", "Razón anterior, S.A.",
                "Razón nueva, S.A.").doesNotContain("\"nit\"");
        // Guardar lo mismo no deja otro registro.
        long antes = jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class);
        guardar(emisor("1234560", "Razón nueva, S.A."), cookieAdmin);
        assertThat(jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class)).isEqualTo(antes);
    }

    // Apoyo

    private static String emisor(String nit, String razonSocial) {
        return """
                {"nit":"%s","razonSocial":"%s","nombreComercial":"Súper del Valle",\
                "direccionFiscal":"5a. avenida 10-20, zona 1, Guatemala","telefono":"2222-3333",\
                "correo":"facturas@delvalle.com","afiliacionIva":"GEN"}""".formatted(nit, razonSocial);
    }

    private HttpResponse<String> guardar(String cuerpo, String sesion) throws Exception {
        return enviar("/api/configuracion/emisor", "PUT", cuerpo, sesion);
    }

    private String usuario(int rolId) {
        String username = "em.u" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Usuario emisor', ?, ?, false)""", username, rolId, codificador.encode(
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
        String nombre = "Caja E" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
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
