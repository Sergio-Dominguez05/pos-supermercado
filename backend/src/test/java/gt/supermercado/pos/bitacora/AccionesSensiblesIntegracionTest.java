package gt.supermercado.pos.bitacora;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.clientes.NitsDePrueba;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static gt.supermercado.pos.bitacora.dominio.AccionSensible.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-11.01 Registrar automáticamente las acciones sensibles, contra PostgreSQL real. Recorre cada acción del
 * catálogo que ya existe y comprueba que la operación, por sí sola, deja exactamente un registro con su acción,
 * su módulo y su resultado (crit. 2), también cuando falla (crit. 3). Las acciones de módulos futuros están en
 * {@link CatalogoAccionesSensiblesTest#PENDIENTES}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + AccionesSensiblesIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class AccionesSensiblesIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String CONTRASENA = "Clave2026segura";
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
    private long adminId;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = cookie(enviar("/api/auth/login", "POST", login("admin", "contrasena", CONTRASENA_ADMIN), null),
                "POS_SESION");
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
    }

    // Sesión

    @Test
    void inicioDeSesionExitosoYFallido() throws Exception {
        String supervisor = usuario(2, CONTRASENA);
        long id = idDeUsuario(supervisor);

        assertThat(registrosDe(() -> enviar("/api/auth/login", "POST", login(supervisor, "contrasena", CONTRASENA), null)))
                .containsExactly(new Registro(INICIO_SESION, true, id));
        assertThat(registrosDe(() -> enviar("/api/auth/login", "POST", login(supervisor, "contrasena", "Incorrecta2026"), null)))
                .containsExactly(new Registro(INICIO_SESION, false, id));
    }

    @Test
    void inicioDeSesionConPinExitosoYFallido() throws Exception {
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);

        assertThat(registrosDe(() -> enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), null)))
                .containsExactly(new Registro(INICIO_SESION, true, id));
        assertThat(registrosDe(() -> enviar("/api/auth/login-pin", "POST", login(cajero, "pin", "9999"), null)))
                .containsExactly(new Registro(INICIO_SESION, false, id));
    }

    @Test
    void cierreDeSesion() throws Exception {
        String supervisor = usuario(2, CONTRASENA);
        String sesion = sesionDe(supervisor, "contrasena", CONTRASENA);

        assertThat(registrosDe(() -> enviar("/api/auth/logout", "POST", null, sesion)))
                .containsExactly(new Registro(CIERRE_SESION, true, idDeUsuario(supervisor)));
    }

    @Test
    void accesoDenegadoPorFaltaDePermiso() throws Exception {
        String cajero = usuario(3, PIN);
        String sesion = sesionDe(cajero, "pin", PIN);

        HttpResponse<String>[] respuesta = respuestaVacia();
        assertThat(registrosDe(() -> respuesta[0] = enviar("/api/usuarios", "GET", null, sesion)))
                .containsExactly(new Registro(ACCESO_DENEGADO, false, idDeUsuario(cajero)));
        assertThat(respuesta[0].statusCode()).isEqualTo(403);
    }

    // Usuarios y permisos

    @Test
    void creacionDeUsuarioExitosaYRechazada() throws Exception {
        String username = nuevoUsername();
        String cuerpo = "{\"nombreCompleto\":\"Usuario auditado\",\"username\":\"%s\",\"rol\":\"SUPERVISOR\",\"credencialTemporal\":\"%s\"}"
                .formatted(username, CONTRASENA);

        assertThat(registrosDe(() -> enviar("/api/usuarios", "POST", cuerpo, cookieAdmin)))
                .containsExactly(new Registro(USUARIO_CREADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/usuarios", "POST", cuerpo, cookieAdmin)))   // nombre repetido
                .containsExactly(new Registro(USUARIO_CREADO, false, adminId));
    }

    @Test
    void edicionYCambioDeRolExitososYRechazados() throws Exception {
        long id = idDeUsuario(usuario(2, CONTRASENA));

        assertThat(registrosDe(() -> enviar("/api/usuarios/" + id, "PUT",
                "{\"nombreCompleto\":\"Ahora administrador\",\"rol\":\"ADMIN\"}", cookieAdmin)))
                .containsExactly(new Registro(USUARIO_EDITADO, true, adminId));
        // El cambio de rol queda en el mismo registro, con el rol anterior y el nuevo.
        assertThat(jdbc.queryForObject("""
                SELECT cambios -> 'rol' ->> 'antes' || '>' || (cambios -> 'rol' ->> 'despues') FROM bitacora
                WHERE accion = 'USUARIO_EDITADO' AND entidad_id = ? ORDER BY secuencia DESC LIMIT 1""", String.class, id))
                .isEqualTo("SUPERVISOR>ADMIN");

        // Pasar a Cajero exige un PIN válido.
        assertThat(registrosDe(() -> enviar("/api/usuarios/" + id, "PUT",
                "{\"nombreCompleto\":\"Ahora cajero\",\"rol\":\"CAJERO\",\"credencialTemporal\":\"12\"}", cookieAdmin)))
                .containsExactly(new Registro(USUARIO_EDITADO, false, adminId));
    }

    @Test
    void desactivacionYActivacionExitosasYRechazadas() throws Exception {
        long id = idDeUsuario(usuario(2, CONTRASENA));

        assertThat(registrosDe(() -> enviar("/api/usuarios/" + id + "/desactivar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(USUARIO_DESACTIVADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/usuarios/" + id + "/activar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(USUARIO_ACTIVADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/usuarios/" + adminId + "/desactivar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(USUARIO_DESACTIVADO, false, adminId));   // a sí mismo
    }

    @Test
    void restablecimientoDeContrasenaYPinExitosoYRechazado() throws Exception {
        long supervisor = idDeUsuario(usuario(2, CONTRASENA));
        long cajero = idDeUsuario(usuario(3, PIN));

        assertThat(registrosDe(() -> enviar("/api/usuarios/" + supervisor + "/credencial", "POST",
                "{\"credencialTemporal\":\"Temporal2026\"}", cookieAdmin)))
                .containsExactly(new Registro(CREDENCIAL_RESTABLECIDA, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/usuarios/" + cajero + "/credencial", "POST",
                "{\"credencialTemporal\":\"1357\"}", cookieAdmin)))
                .containsExactly(new Registro(CREDENCIAL_RESTABLECIDA, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/usuarios/" + cajero + "/credencial", "POST",
                "{\"credencialTemporal\":\"abc\"}", cookieAdmin)))
                .containsExactly(new Registro(CREDENCIAL_RESTABLECIDA, false, adminId));
    }

    @Test
    void cambioDeLaCredencialPropiaExitosoYRechazado() throws Exception {
        String supervisor = usuario(2, CONTRASENA);
        long id = idDeUsuario(supervisor);
        String sesion = sesionDe(supervisor, "contrasena", CONTRASENA);

        assertThat(registrosDe(() -> enviar("/api/auth/credencial", "PUT",
                "{\"actual\":\"Incorrecta2026\",\"nueva\":\"Nueva2026clave\",\"confirmacion\":\"Nueva2026clave\"}", sesion)))
                .containsExactly(new Registro(CREDENCIAL_CAMBIADA, false, id));
        assertThat(registrosDe(() -> enviar("/api/auth/credencial", "PUT",
                "{\"actual\":\"%s\",\"nueva\":\"Nueva2026clave\",\"confirmacion\":\"Nueva2026clave\"}".formatted(CONTRASENA),
                sesion)))
                .containsExactly(new Registro(CREDENCIAL_CAMBIADA, true, id));
    }

    // Configuración

    @Test
    void altaYEdicionDeCajasExitosasYRechazadas() throws Exception {
        String nombre = "Caja auditada " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        HttpResponse<String>[] creada = respuestaVacia();

        assertThat(registrosDe(() -> creada[0] = enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin)))
                .containsExactly(new Registro(CAJA_CREADA, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin)))
                .containsExactly(new Registro(CAJA_CREADA, false, adminId));   // nombre repetido

        long id = idDe(creada[0]);
        assertThat(registrosDe(() -> enviar("/api/cajas/" + id, "PUT", "{\"nombre\":\"%s\",\"serieFel\":\"B07\"}".formatted(nombre), cookieAdmin)))
                .containsExactly(new Registro(CAJA_EDITADA, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/cajas/" + id, "PUT", "{\"nombre\":\"   \"}", cookieAdmin)))
                .containsExactly(new Registro(CAJA_EDITADA, false, adminId));   // nombre vacío
    }

    @Test
    void activacionDesactivacionYVinculacionDeEquipoDeCajas() throws Exception {
        String nombre = "Caja equipo " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));

        assertThat(registrosDe(() -> enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(EQUIPO_VINCULADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/cajas/" + id + "/desactivar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(CAJA_DESACTIVADA, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/cajas/" + id + "/equipo", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(EQUIPO_VINCULADO, false, adminId));   // caja inactiva
        assertThat(registrosDe(() -> enviar("/api/cajas/" + id + "/activar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(CAJA_ACTIVADA, true, adminId));
    }

    // Catálogo

    @Test
    void altaDeProductoExitosaYRechazada() throws Exception {
        String codigo = "AUD-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        String producto = "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto auditado\",\"precio\":9.99}".formatted(codigo);

        assertThat(registrosDe(() -> enviar("/api/productos", "POST", producto, cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_CREADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/productos", "POST", producto, cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_CREADO, false, adminId));   // código repetido
    }

    @Test
    void edicionDeDescripcionYPrecioExitosaYRechazada() throws Exception {
        String codigo = "AUD-E" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = idDe(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Antes\",\"precio\":1.00}".formatted(codigo), cookieAdmin));

        assertThat(registrosDe(() -> enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"Después\",\"precio\":1.00}", cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_EDITADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"Después\",\"precio\":2.50}", cookieAdmin)))
                .containsExactly(new Registro(PRECIO_CAMBIADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"  \",\"precio\":2.50}", cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_EDITADO, false, adminId));   // descripción vacía
        assertThat(registrosDe(() -> enviar("/api/productos/" + id, "PUT", "{\"descripcion\":\"Después\",\"precio\":0}", cookieAdmin)))
                .containsExactly(new Registro(PRECIO_CAMBIADO, false, adminId));   // precio en cero
    }

    @Test
    void desactivacionYActivacionDeProductos() throws Exception {
        String codigo = "AUD-A" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long id = idDe(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Estado\",\"precio\":1.00}".formatted(codigo), cookieAdmin));

        assertThat(registrosDe(() -> enviar("/api/productos/" + id + "/desactivar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_DESACTIVADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/productos/" + id + "/activar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(PRODUCTO_ACTIVADO, true, adminId));
    }

    // Caja

    @Test
    void aperturaDeTurnoExitosaYRechazada() throws Exception {
        String nombre = "Caja turno " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), equipo), "POS_SESION");

        assertThat(registrosDe(() -> enviar("/api/turnos", "POST", null, sesion)))
                .containsExactly(new Registro(TURNO_ABIERTO, true, id));
        assertThat(registrosDe(() -> enviar("/api/turnos", "POST", null, sesion)))
                .containsExactly(new Registro(TURNO_ABIERTO, false, id));   // ya tiene el turno abierto
    }

    // Ventas

    @Test
    void cancelacionDeVentaExitosaYRechazada() throws Exception {
        String nombre = "Caja venta " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), equipo), "POS_SESION");
        enviar("/api/turnos", "POST", null, sesion);
        long venta = idDe(enviar("/api/ventas", "POST", null, sesion));
        String codigo = "AS" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        long producto = idDe(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto auditado\",\"precio\":11.20}".formatted(codigo),
                cookieAdmin));
        jdbc.update("""
                INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario, subtotal_con_iva,
                                         base_imponible, iva)
                VALUES (?, ?, 'Producto auditado', 1, 11.20, 11.20, 10.00, 1.20)""", venta, producto);

        assertThat(registrosDe(() -> enviar("/api/ventas/actual/cancelar", "POST", null, sesion)))
                .containsExactly(new Registro(VENTA_CANCELADA, true, id));
        assertThat(registrosDe(() -> enviar("/api/ventas/actual/cancelar", "POST", null, sesion)))
                .containsExactly(new Registro(VENTA_CANCELADA, false, id));   // ya no hay venta en curso
    }

    @Test
    void eliminacionDeLineaExitosaYRechazada() throws Exception {   // HU-06.07 crit. 3
        String nombre = "Caja linea " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), equipo), "POS_SESION");
        enviar("/api/turnos", "POST", null, sesion);
        String codigo = "AL" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto auditado\",\"precio\":11.20}".formatted(codigo),
                cookieAdmin);
        enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), sesion);
        long linea = jdbc.queryForObject("""
                SELECT l.id FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.usuario_id = ?""", Long.class, id);

        assertThat(registrosDe(() -> enviar("/api/ventas/actual/lineas/" + linea, "DELETE", null, sesion)))
                .containsExactly(new Registro(LINEA_VENTA_ELIMINADA, true, id));
        assertThat(registrosDe(() -> enviar("/api/ventas/actual/lineas/" + linea, "DELETE", null, sesion)))
                .containsExactly(new Registro(LINEA_VENTA_ELIMINADA, false, id));   // ya estaba eliminada
    }

    // Pagos

    @Test
    void eliminacionDePagoExitosaYRechazada() throws Exception {   // HU-07.06 crit. 4
        String nombre = "Caja pago " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), equipo), "POS_SESION");
        enviar("/api/turnos", "POST", null, sesion);
        String codigo = "AP" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto auditado\",\"precio\":11.20}".formatted(codigo),
                cookieAdmin);
        enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), sesion);
        enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}", sesion);
        long pago = jdbc.queryForObject("SELECT id FROM pago WHERE usuario_id = ?", Long.class, id);

        assertThat(registrosDe(() -> enviar("/api/ventas/actual/pagos/" + pago, "DELETE", null, sesion)))
                .containsExactly(new Registro(PAGO_TARJETA_ELIMINADO, true, id));
        assertThat(registrosDe(() -> enviar("/api/ventas/actual/pagos/" + pago, "DELETE", null, sesion)))
                .containsExactly(new Registro(PAGO_TARJETA_ELIMINADO, false, id));   // ya estaba eliminado
    }

    @Test
    void finalizacionDeVentaExitosaYRechazada() throws Exception {   // HU-07.08
        FacturacionDePrueba.configurarEmisorYEstablecimiento(jdbc);
        String nombre = "Caja fin " + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long cajaId = idDe(enviar("/api/cajas", "POST", "{\"nombre\":\"%s\"}".formatted(nombre), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + cajaId + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String cajero = usuario(3, PIN);
        long id = idDeUsuario(cajero);
        String sesion = cookie(enviar("/api/auth/login-pin", "POST", login(cajero, "pin", PIN), equipo), "POS_SESION");
        enviar("/api/turnos", "POST", null, sesion);
        String codigo = "AF" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto auditado\",\"precio\":11.20}".formatted(codigo),
                cookieAdmin);
        enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), sesion);
        long venta = jdbc.queryForObject("SELECT id FROM venta WHERE usuario_id = ?", Long.class, id);

        assertThat(registrosDe(() -> enviar("/api/ventas/actual/finalizar", "POST", null, sesion)))
                .containsExactly(new Registro(VENTA_FINALIZADA, false, id));   // sin pago
        enviar("/api/ventas/actual/pagos", "POST", "{\"tipoTarjeta\":\"DEBITO\",\"autorizacion\":\"123456\"}", sesion);
        assertThat(registrosDe(() -> enviar("/api/ventas/actual/finalizar", "POST", null, sesion)))
                .containsExactly(new Registro(VENTA_FINALIZADA, true, id));
        assertThat(jdbc.queryForObject("""
                SELECT entidad_id FROM bitacora WHERE accion = 'VENTA_FINALIZADA' AND exitoso AND usuario_id = ?""",
                Long.class, id)).isEqualTo(venta);
    }

    @Test
    void reenvioDeFacturaRechazado() throws Exception {   // HU-08.06: el éxito lo registra el rechazo del certificador
        // Un reenvío sin factura rechazada se rechaza y queda en la bitácora; el rechazo del certificador (éxito) lo
        // prueba RechazosIntegracionTest, porque necesita una venta cobrada y el simulador que rechaza.
        assertThat(registrosDe(() -> enviar("/api/facturas/rechazadas/999999999/reenviar", "POST", null, cookieAdmin)))
                .containsExactly(new Registro(FACTURA_RECHAZADA_CERTIFICADOR, false, adminId));
    }

    @Test
    void registroDeClienteExitosoYRechazado() throws Exception {   // HU-05.05 crit. 4
        String nit = NitsDePrueba.nuevo();

        assertThat(registrosDe(() -> enviar("/api/clientes", "POST",
                "{\"nit\":\"%s\",\"nombre\":\"Cliente auditado\"}".formatted(nit), cookieAdmin)))
                .containsExactly(new Registro(CLIENTE_REGISTRADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/clientes", "POST",
                "{\"nit\":\"%s\",\"nombre\":\"\"}".formatted(NitsDePrueba.nuevo()), cookieAdmin)))
                .containsExactly(new Registro(CLIENTE_REGISTRADO, false, adminId));   // sin nombre
    }

    @Test
    void cambioDeLaConexionFelExitosoYRechazado() throws Exception {   // HU-12.03 y HU-12.15 crit. 4
        String cuerpo = """
                {"certificador":"Certificador auditado","urlServicio":"https://certificador.test/api",                "usuario":"usuario-fel","secreto":"llave","ambiente":"PRUEBAS","timeoutMs":%d}""";

        assertThat(registrosDe(() -> enviar("/api/configuracion/fel", "PUT", cuerpo.formatted(8000), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/configuracion/fel", "PUT", cuerpo.formatted(10), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, false, adminId));   // tiempo límite inválido
    }

    @Test
    void cambioDeLosDatosDelEmisorExitosoYRechazado() throws Exception {   // HU-12.01 y HU-12.15 crit. 4
        String cuerpo = """
                {"nit":"%s","razonSocial":"Emisor auditado %d, S.A.","nombreComercial":"Auditado",\
                "direccionFiscal":"Zona 1, Guatemala","afiliacionIva":"GEN"}""";

        assertThat(registrosDe(() -> enviar("/api/configuracion/emisor", "PUT",
                cuerpo.formatted("1234560", System.nanoTime()), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/configuracion/emisor", "PUT",
                cuerpo.formatted("1234561", System.nanoTime()), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, false, adminId));   // NIT inválido
    }

    @Test
    void cambioDelEstablecimientoExitosoYRechazado() throws Exception {   // HU-12.02 y HU-12.15 crit. 4
        String cuerpo = """
                {"codigoSat":"%s","nombre":"Establecimiento auditado %d","direccion":"Zona 1, Guatemala",\
                "codigoPostal":"01001","municipio":"Guatemala","departamento":"Guatemala"}""";

        assertThat(registrosDe(() -> enviar("/api/configuracion/establecimiento", "PUT",
                cuerpo.formatted("1", System.nanoTime()), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, true, adminId));
        assertThat(registrosDe(() -> enviar("/api/configuracion/establecimiento", "PUT",
                cuerpo.formatted("0", System.nanoTime()), cookieAdmin)))
                .containsExactly(new Registro(PARAMETRO_MODIFICADO, false, adminId));   // código inválido
    }

    // Criterio 2: sin intervención del usuario, en el momento

    @Test
    void elRegistroQuedaGuardadoAlTerminarLaPeticionConElModuloDelCatalogo() throws Exception {
        String supervisor = usuario(2, CONTRASENA);
        long antes = ultimaSecuencia();
        enviar("/api/auth/login", "POST", login(supervisor, "contrasena", "Incorrecta2026"), null);

        // Sin ninguna otra llamada, el intento ya está en la bitácora, con el módulo del catálogo.
        assertThat(jdbc.queryForList("SELECT modulo FROM bitacora WHERE secuencia > ?", String.class, antes))
                .containsExactly(INICIO_SESION.modulo());
    }

    // Apoyo

    /** Lo que se compara de cada registro: la acción, si salió bien y quién la hizo. */
    record Registro(AccionSensible accion, boolean exitoso, Long usuarioId) {}

    @FunctionalInterface
    interface Operacion {
        void ejecutar() throws Exception;
    }

    /** Los registros que la operación agregó a la bitácora, comprobando que cada uno lleva el módulo de su acción. */
    private List<Registro> registrosDe(Operacion operacion) throws Exception {
        long antes = ultimaSecuencia();
        operacion.ejecutar();
        return jdbc.query("SELECT modulo, accion, exitoso, usuario_id FROM bitacora WHERE secuencia > ? ORDER BY secuencia",
                (fila, n) -> {
                    AccionSensible accion = AccionSensible.valueOf(fila.getString("accion"));
                    assertThat(fila.getString("modulo")).isEqualTo(accion.modulo());
                    return new Registro(accion, fila.getBoolean("exitoso"), fila.getObject("usuario_id", Long.class));
                }, antes);
    }

    private long ultimaSecuencia() {
        return jdbc.queryForObject("SELECT coalesce(max(secuencia), 0) FROM bitacora", Long.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String>[] respuestaVacia() {
        return new HttpResponse[1];
    }

    private static String nuevoUsername() {
        return "as.u" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
    }

    /** Un usuario nuevo con su credencial definitiva (sin cambio obligatorio pendiente). */
    private String usuario(int rolId, String credencial) {
        String username = nuevoUsername();
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Usuario auditado', ?, ?, false)""", username, rolId, codificador.encode(credencial));
        return username;
    }

    private long idDeUsuario(String username) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String sesionDe(String username, String campo, String valor) throws Exception {
        String ruta = "pin".equals(campo) ? "/api/auth/login-pin" : "/api/auth/login";
        return cookie(enviar(ruta, "POST", login(username, campo, valor), null), "POS_SESION");
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
