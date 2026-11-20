package gt.supermercado.pos.caja;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.facturacion.FacturacionDePrueba;
import gt.supermercado.pos.impresion.infraestructura.CanalImpresionSimulado;
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
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-09.08 Cerrar la caja, HU-09.09 Justificar diferencias y HU-09.10 Imprimir el comprobante de cierre, contra
 * PostgreSQL real, el certificador y la impresora simulados.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + CierreIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CierreIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String PIN = "4821";
    static final String CONTRASENA_SUPERVISOR = "Clave2026segura";
    private static final Charset PAGINA_850 = Charset.forName("IBM850");

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    CanalImpresionSimulado impresoraSimulada;

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

    @Test
    void elResumenSumaPorTarjetaYDescuentaLasAnuladas() throws Exception {   // 09.08 crit. 3 y decisión de Sergio
        Cajero cajero = cajeroConTurno();
        vender(cajero, "10.00", "DEBITO");
        vender(cajero, "5.00", "CREDITO");
        long anulada = vender(cajero, "3.00", "DEBITO");
        assertThat(enviar("/api/facturas/" + anulada + "/anular", "POST", "{\"motivo\":\"Error de cobro\"}",
                cookieAdmin).statusCode()).isEqualTo(200);

        HttpResponse<String> resumen = enviar("/api/turnos/actual/cierre", "GET", null, cajero.sesion());

        assertThat(resumen.statusCode()).isEqualTo(200);
        assertThat(resumen.body()).contains("\"cierreCiego\":false", "\"ventas\":3", "\"debito\":10.00",
                "\"credito\":5.00", "\"anuladas\":1",
                "\"totalAnulado\":3.00", "\"totalRegistrado\":15.00");
    }

    @Test
    void elSupervisorConsultaCualquierTurnoAbiertoYElCajeroSoloElPropio() throws Exception {   // 09.06
        Cajero cajero = cajeroConTurno();
        vender(cajero, "10.00", "DEBITO");
        vender(cajero, "5.00", "CREDITO");
        Cajero supervisor = supervisorConTurno();

        HttpResponse<String> abiertosSupervisor = enviar("/api/turnos/abiertos", "GET", null,
                supervisor.sesion());
        HttpResponse<String> resumenAjeno = enviar("/api/turnos/" + cajero.turnoId() + "/resumen", "GET", null,
                supervisor.sesion());

        assertThat(abiertosSupervisor.statusCode()).isEqualTo(200);
        assertThat(abiertosSupervisor.body()).contains("\"id\":" + cajero.turnoId(),
                "\"id\":" + supervisor.turnoId());
        assertThat(resumenAjeno.statusCode()).isEqualTo(200);
        assertThat(resumenAjeno.body()).contains("\"ventas\":2", "\"debito\":10.00", "\"credito\":5.00",
                "\"totalRegistrado\":15.00");
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, cajero.turnoId()))
                .isEqualTo("ABIERTO");

        HttpResponse<String> abiertosCajero = enviar("/api/turnos/abiertos", "GET", null, cajero.sesion());
        assertThat(abiertosCajero.body()).contains("\"id\":" + cajero.turnoId())
                .doesNotContain("\"id\":" + supervisor.turnoId());
        assertThat(enviar("/api/turnos/" + supervisor.turnoId() + "/resumen", "GET", null, cajero.sesion())
                .statusCode()).isEqualTo(403);
    }

    @Test
    void elCierreCiegoOcultaElTotalAlCajeroHastaQueConfirmaSuMonto() throws Exception {   // 09.11 crit. 1 a 3
        String anterior = jdbc.queryForObject("SELECT valor::text FROM parametro WHERE clave = 'cierre_ciego'",
                String.class);
        jdbc.update("UPDATE parametro SET valor = 'true'::jsonb WHERE clave = 'cierre_ciego'");
        try {
            Cajero cajero = cajeroConTurno();
            vender(cajero, "10.00", "DEBITO");

            HttpResponse<String> oculto = enviar("/api/turnos/actual/cierre", "GET", null, cajero.sesion());
            assertThat(oculto.body()).contains("\"cierreCiego\":true", "\"resumen\":null")
                    .doesNotContain("totalRegistrado", "debito", "credito");
            assertThat(enviar("/api/turnos/abiertos", "GET", null, cajero.sesion()).body()).isEqualTo("[]");
            assertThat(enviar("/api/turnos/" + cajero.turnoId() + "/resumen", "GET", null, cajero.sesion())
                    .statusCode()).isEqualTo(403);

            HttpResponse<String> comparacion = enviar("/api/turnos/actual/cierre/comparar", "POST",
                    "{\"totalLote\":8.00}", cajero.sesion());
            assertThat(comparacion.statusCode()).isEqualTo(200);
            assertThat(comparacion.body()).contains("\"totalRegistrado\":10.00", "\"diferencia\":-2.00");
            assertThat(cerrar(cajero, "8.00", "Faltan dos quetzales en la terminal").body())
                    .contains("\"totalRegistrado\":10.00", "\"diferencia\":-2.00");

            Cajero supervisor = supervisorConTurno();
            vender(supervisor, "7.00", "CREDITO");
            assertThat(enviar("/api/turnos/actual/cierre", "GET", null, supervisor.sesion()).body())
                    .contains("\"cierreCiego\":false", "\"totalRegistrado\":7.00");
        } finally {
            jdbc.update("UPDATE parametro SET valor = ?::jsonb WHERE clave = 'cierre_ciego'", anterior);
        }
    }

    @Test
    void sinDiferenciaSeCierraSeGuardaYSeImprimeElComprobante() throws Exception {   // 09.08 crit. 4 a 6, 09.10
        Cajero cajero = cajeroConTurno();
        vender(cajero, "12.50", "DEBITO");

        HttpResponse<String> cierre = cerrar(cajero, "12.50", null);

        assertThat(cierre.statusCode()).isEqualTo(200);
        assertThat(cierre.body()).contains("\"diferencia\":0.00", "\"totalLote\":12.50", "\"totalRegistrado\":12.50",
                "\"impresa\":true");
        Map<String, Object> turno = jdbc.queryForMap("""
                SELECT estado, cerrado_en IS NOT NULL AS con_hora, cerrado_por, total_registrado::text AS registrado,
                       total_lote::text AS lote, diferencia::text AS diferencia
                FROM turno WHERE id = ?""", cajero.turnoId());
        assertThat(turno).containsEntry("estado", "CERRADO").containsEntry("con_hora", true)
                .containsEntry("cerrado_por", cajero.usuarioId()).containsEntry("registrado", "12.50")
                .containsEntry("lote", "12.50").containsEntry("diferencia", "0.00");
        String comprobante = new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850);
        assertThat(comprobante).contains("CIERRE DE CAJA", "Tarjeta de débito", "Q12.50", "DIFERENCIA",
                "Firma del cajero", "Firma del supervisor");
        assertThat(comprobante.split("CIERRE DE CAJA", -1)).hasSize(3);   // las dos copias de cierre de la impresora
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'TURNO_CERRADO' AND exitoso AND entidad_id = ?""",
                Integer.class, cajero.turnoId())).isEqualTo(1);

        // Crit. 4: cerrado, no se modifica y ya no se vende en él.
        assertThatThrownBy(() -> jdbc.update("UPDATE turno SET total_lote = 0 WHERE id = ?", cajero.turnoId()))
                .hasMessageContaining("ya está cerrado");
        assertThat(enviar("/api/ventas", "POST", null, cajero.sesion()).body()).contains("CAJA_CERRADA");
    }

    @Test
    void unaDiferenciaExigeJustificacion() throws Exception {   // 09.08 crit. 3 y 09.09 crit. 1
        Cajero cajero = cajeroConTurno();
        vender(cajero, "20.00", "CREDITO");

        HttpResponse<String> sinJustificar = cerrar(cajero, "18.00", " ");
        assertThat(sinJustificar.statusCode()).isEqualTo(422);
        assertThat(sinJustificar.body()).contains("JUSTIFICACION_REQUERIDA", "Q-2.00");
        assertThat(cerrar(cajero, null, null).body()).contains("TOTAL_LOTE_INVALIDO");

        HttpResponse<String> justificado = cerrar(cajero, "18.00", "Un pago se registró dos veces en la terminal");

        assertThat(justificado.statusCode()).isEqualTo(200);
        assertThat(justificado.body()).contains("\"diferencia\":-2.00",
                "\"justificacion\":\"Un pago se registró dos veces en la terminal\"");
        assertThat(new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850))
                .contains("Justificación: Un pago se registró dos veces en", "la terminal");   // en dos renglones
    }

    @Test
    void conVentasPorResolverNoSeCierra() throws Exception {   // 09.08 crit. 1
        Cajero cajero = cajeroConTurno();
        agregarProducto(cajero, "4.00");
        assertThat(enviar("/api/ventas/actual/suspender", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(cerrar(cajero, "0", null).body()).contains("VENTAS_SUSPENDIDAS");

        long suspendida = jdbc.queryForObject(
                "SELECT id FROM venta WHERE turno_id = ? AND estado = 'SUSPENDIDA'", Long.class, cajero.turnoId());
        assertThat(enviar("/api/ventas/suspendidas/" + suspendida + "/retomar", "POST", null, cajero.sesion())
                .statusCode()).isEqualTo(200);
        assertThat(cerrar(cajero, "0", null).body()).contains("VENTA_EN_CURSO");

        assertThat(enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        enviar("/api/ventas", "POST", null, cajero.sesion());   // la pantalla deja lista una venta vacía
        assertThat(cerrar(cajero, "0", null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venta WHERE turno_id = ? AND estado = 'EN_CURSO'",
                Integer.class, cajero.turnoId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'TURNO_CERRADO' AND NOT exitoso AND usuario_id = ?""",
                Integer.class, cajero.usuarioId())).isEqualTo(2);
    }

    @Test
    void losCierresAnterioresSeConsultanYSeReimprimen() throws Exception {   // 09.10 crit. 3
        Cajero cajero = cajeroConTurno();
        vender(cajero, "7.00", "DEBITO");
        cerrar(cajero, "7.00", null);

        HttpResponse<String> cierres = enviar("/api/turnos/cierres", "GET", null, cajero.sesion());
        assertThat(cierres.statusCode()).isEqualTo(200);
        assertThat(cierres.body()).contains("\"turnoId\":" + cajero.turnoId(), "\"totalLote\":7.00");
        Cajero otro = cajeroConTurno();
        assertThat(enviar("/api/turnos/cierres", "GET", null, otro.sesion()).body())
                .doesNotContain("\"turnoId\":" + cajero.turnoId() + ",");

        HttpResponse<String> reimpresion = enviar("/api/turnos/" + cajero.turnoId() + "/cierre/reimprimir", "POST",
                null, cajero.sesion());
        assertThat(reimpresion.body()).contains("\"impresa\":true");
        String copia = new String(impresoraSimulada.ultimoA(cajero.host()).datos(), PAGINA_850);
        assertThat(copia).contains("COPIA - REIMPRESIÓN", "CIERRE DE CAJA");
        assertThat(enviar("/api/turnos/" + cajero.turnoId() + "/cierre/reimprimir", "POST", null, otro.sesion())
                .body()).contains("CIERRE_DE_OTRO_USUARIO");
    }

    // HU-09.12 Cerrar la caja de otro usuario

    @Test
    void elSupervisorCierraElTurnoDeOtroCajeroYQuedanLosDos() throws Exception {   // 09.12 crit. 1 a 3
        Cajero cajero = cajeroConTurno();
        vender(cajero, "10.00", "DEBITO");
        long supervisorId = supervisor();
        String supervisor = sesionDeSupervisor();

        HttpResponse<String> abiertos = enviar("/api/turnos/por-cerrar", "GET", null, supervisor);
        assertThat(abiertos.statusCode()).isEqualTo(200);
        assertThat(abiertos.body()).contains("\"id\":" + cajero.turnoId(), "Cajero que cierra");
        assertThat(enviar("/api/turnos/" + cajero.turnoId() + "/cierre", "GET", null, supervisor).body())
                .contains("\"ventas\":1", "\"totalRegistrado\":10.00");

        HttpResponse<String> sinMotivo = enviar("/api/turnos/" + cajero.turnoId() + "/cerrar", "POST",
                "{\"totalLote\":9.00,\"justificacion\":null}", supervisor);
        assertThat(sinMotivo.statusCode()).isEqualTo(422);
        assertThat(sinMotivo.body()).contains("JUSTIFICACION_REQUERIDA");

        HttpResponse<String> cerrado = enviar("/api/turnos/" + cajero.turnoId() + "/cerrar", "POST",
                "{\"totalLote\":9.00,\"justificacion\":\"El cajero se retiró sin cerrar\"}", supervisor);

        assertThat(cerrado.statusCode()).as(cerrado.body()).isEqualTo(200);
        var turno = jdbc.queryForMap("""
                SELECT estado, usuario_id, cerrado_por, total_registrado::text AS registrado, diferencia::text AS diferencia
                FROM turno WHERE id = ?""", cajero.turnoId());
        assertThat(turno.get("estado")).isEqualTo("CERRADO");
        assertThat(turno.get("usuario_id")).isEqualTo(cajero.usuarioId());   // crit. 2: el cajero del turno
        assertThat(turno.get("cerrado_por")).isEqualTo(supervisorId);        // y el supervisor que lo cerró
        assertThat(turno.get("diferencia")).isEqualTo("-1.00");
        var registro = jdbc.queryForMap("""
                SELECT descripcion, cambios->>'cajero' AS cajero, caja_id FROM bitacora
                WHERE accion = 'TURNO_CERRADO_POR_SUPERVISOR' AND exitoso AND usuario_id = ?
                ORDER BY secuencia DESC LIMIT 1""", supervisorId);
        assertThat((String) registro.get("descripcion")).startsWith("Turno de Cajero que cierra cerrado por supervisor");
        assertThat(registro.get("cajero")).isEqualTo("Cajero que cierra");
        // El comprobante sale en la impresora de la caja del turno.
        assertThat(impresoraSimulada.ultimoA(cajero.host())).isNotNull();
        // El cajero ya no tiene turno abierto.
        assertThat(enviar("/api/turnos/actual/cierre", "GET", null, cajero.sesion()).statusCode()).isEqualTo(422);
        assertThat(enviar("/api/turnos/por-cerrar", "GET", null, supervisor).body())
                .doesNotContain("\"id\":" + cajero.turnoId() + ",");
    }

    @Test
    void noSeCierraUnTurnoYaCerradoNiElPropioPorEstaVia() throws Exception {   // 09.12
        Cajero cajero = cajeroConTurno();
        String supervisor = sesionDeSupervisor();
        assertThat(cerrar(cajero, "0", null).statusCode()).isEqualTo(200);

        HttpResponse<String> yaCerrado = enviar("/api/turnos/" + cajero.turnoId() + "/cerrar", "POST",
                "{\"totalLote\":0}", supervisor);
        assertThat(yaCerrado.statusCode()).isEqualTo(422);
        assertThat(yaCerrado.body()).contains("TURNO_YA_CERRADO");

        Cajero otro = cajeroConTurno();
        HttpResponse<String> propio = enviar("/api/turnos/" + otro.turnoId() + "/cerrar", "POST", "{\"totalLote\":0}",
                otro.sesion());
        assertThat(propio.statusCode()).isEqualTo(403);   // el cajero no tiene el permiso
        assertThat(enviar("/api/turnos/por-cerrar", "GET", null, otro.sesion()).statusCode()).isEqualTo(403);
    }

    @Test
    void conVentasPorResolverElSupervisorTampocoCierra() throws Exception {   // 09.12 con las reglas de 09.08 crit. 1
        Cajero cajero = cajeroConTurno();
        agregarProducto(cajero, "4.00");
        String supervisor = sesionDeSupervisor();

        HttpResponse<String> respuesta = enviar("/api/turnos/" + cajero.turnoId() + "/cerrar", "POST",
                "{\"totalLote\":0}", supervisor);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("VENTA_EN_CURSO");
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, cajero.turnoId()))
                .isEqualTo("ABIERTO");
    }

    // HU-09.13 Consultar cierres anteriores

    @Test
    void elSupervisorConsultaLosCierresConFiltros() throws Exception {   // 09.13 crit. 1 y 2
        Cajero conDiferencia = cajeroConTurno();
        vender(conDiferencia, "10.00", "DEBITO");
        assertThat(cerrar(conDiferencia, "9.00", "Faltó un voucher").statusCode()).isEqualTo(200);
        Cajero cuadrado = cajeroConTurno();
        assertThat(cerrar(cuadrado, "0", null).statusCode()).isEqualTo(200);
        String supervisor = sesionDeSupervisor();
        String hoy = jdbc.queryForObject("SELECT (now() AT TIME ZONE 'America/Guatemala')::date::text", String.class);
        String periodo = "desde=" + hoy + "&hasta=" + hoy;

        HttpResponse<String> todos = enviar("/api/cierres?" + periodo, "GET", null, supervisor);
        assertThat(todos.statusCode()).isEqualTo(200);
        assertThat(todos.body()).contains("\"turnoId\":" + conDiferencia.turnoId(), "\"turnoId\":" + cuadrado.turnoId(),
                "\"cajaNombre\"", "\"usuarioNombre\":\"Cajero que cierra\"", "\"totalRegistrado\":10.00",
                "\"totalLote\":9.00", "\"diferencia\":-1.00", "\"tamanoPagina\":50");

        // Crit. 2: por cajero, por caja y solo con diferencias.
        assertThat(enviar("/api/cierres?" + periodo + "&cajeroId=" + cuadrado.usuarioId(), "GET", null, supervisor)
                .body()).contains("\"turnoId\":" + cuadrado.turnoId()).doesNotContain(
                "\"turnoId\":" + conDiferencia.turnoId() + ",").contains("\"total\":1");
        long caja = jdbc.queryForObject("SELECT caja_id FROM turno WHERE id = ?", Long.class, conDiferencia.turnoId());
        assertThat(enviar("/api/cierres?cajaId=" + caja, "GET", null, supervisor).body())
                .contains("\"turnoId\":" + conDiferencia.turnoId()).contains("\"total\":1");
        String diferencias = enviar("/api/cierres?" + periodo + "&soloConDiferencias=true", "GET", null, supervisor)
                .body();
        assertThat(diferencias).contains("\"turnoId\":" + conDiferencia.turnoId())
                .doesNotContain("\"turnoId\":" + cuadrado.turnoId() + ",");
        // Un día sin cierres de estos cajeros.
        assertThat(enviar("/api/cierres?desde=2020-01-01&hasta=2020-01-01", "GET", null, supervisor).body())
                .contains("\"cierres\":[]", "\"total\":0");

        HttpResponse<String> invertidas = enviar("/api/cierres?desde=" + hoy + "&hasta=2020-01-01", "GET", null,
                supervisor);
        assertThat(invertidas.statusCode()).isEqualTo(422);
        assertThat(invertidas.body()).contains("FILTRO_FECHAS_INVERTIDAS");

        HttpResponse<String> opciones = enviar("/api/cierres/opciones", "GET", null, supervisor);
        assertThat(opciones.body()).contains("\"id\":" + caja, "\"id\":" + cuadrado.usuarioId());
        // El cajero no consulta los cierres de los demás.
        assertThat(enviar("/api/cierres", "GET", null, cuadrado.sesion()).statusCode()).isEqualTo(403);
    }

    @Test
    void elDetalleTraeElCierreYLoCobradoYSeReimprime() throws Exception {   // 09.13 crit. 3
        Cajero cajero = cajeroConTurno();
        vender(cajero, "10.00", "DEBITO");
        vender(cajero, "5.00", "CREDITO");
        assertThat(cerrar(cajero, "14.00", "Faltó un voucher de crédito").statusCode()).isEqualTo(200);
        String supervisor = sesionDeSupervisor();

        HttpResponse<String> detalle = enviar("/api/cierres/" + cajero.turnoId(), "GET", null, supervisor);

        assertThat(detalle.statusCode()).isEqualTo(200);
        assertThat(detalle.body()).contains("\"turnoId\":" + cajero.turnoId(), "\"abiertoEn\"", "\"cerradoEn\"",
                "\"cerradoPor\":\"Cajero que cierra\"", "\"justificacion\":\"Faltó un voucher de crédito\"",
                "\"ventas\":2", "\"debito\":10.00", "\"credito\":5.00", "\"diferencia\":-1.00");
        assertThat(enviar("/api/cierres/999999999", "GET", null, supervisor).statusCode()).isEqualTo(404);
        assertThat(enviar("/api/cierres/" + cajero.turnoId(), "GET", null, cajero.sesion()).statusCode())
                .isEqualTo(403);
        // La reimpresión es la de HU-09.10: el supervisor reimprime el cierre de cualquier cajero.
        assertThat(enviar("/api/turnos/" + cajero.turnoId() + "/cierre/reimprimir", "POST", null, supervisor)
                .statusCode()).isEqualTo(200);
    }

    // Apoyo

    private String supervisorUsername;

    /** Un supervisor con contraseña definitiva; devuelve su id. */
    private long supervisor() {
        if (supervisorUsername == null) {
            supervisorUsername = "ci.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
            jdbc.update("""
                    INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                    VALUES (?, 'Supervisora del cierre', 2, ?, false)""", supervisorUsername,
                    codificador.encode("Clave2026segura"));
        }
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, supervisorUsername);
    }

    private String sesionDeSupervisor() throws Exception {
        supervisor();
        return cookie(enviar("/api/auth/login", "POST", login(supervisorUsername, "contrasena", "Clave2026segura"),
                null), "POS_SESION");
    }

    record Cajero(long usuarioId, String sesion, long turnoId, String host) {}

    private HttpResponse<String> cerrar(Cajero cajero, String totalLote, String justificacion) throws Exception {
        String cuerpo = "{\"totalLote\":%s,\"justificacion\":%s}".formatted(totalLote,
                justificacion == null ? "null" : "\"" + justificacion + "\"");
        return enviar("/api/turnos/actual/cerrar", "POST", cuerpo, cajero.sesion());
    }

    /** Un cajero con turno abierto en una caja nueva cuya impresora saca dos copias de cada cierre. */
    private Cajero cajeroConTurno() throws Exception {
        return usuarioConTurno(false);
    }

    private Cajero supervisorConTurno() throws Exception {
        return usuarioConTurno(true);
    }

    private Cajero usuarioConTurno(boolean supervisor) throws Exception {
        String host = "cierre-" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000 + ".local";
        long impresora = idDe(enviar("/api/configuracion/impresoras", "POST",
                "{\"nombre\":\"Térmica %s\",\"host\":\"%s\",\"anchoMm\":80,\"copiasCierre\":2}".formatted(host, host),
                cookieAdmin));
        String nombre = "Caja CI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 100_000;
        long caja = idDe(enviar("/api/cajas", "POST",
                "{\"nombre\":\"%s\",\"impresoraId\":%d}".formatted(nombre, impresora), cookieAdmin));
        String equipo = cookie(enviar("/api/cajas/" + caja + "/equipo", "POST", null, cookieAdmin), "POS_EQUIPO");
        String username = "ci." + (supervisor ? "s" : "c") + SIGUIENTE.incrementAndGet() + "."
                + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false)""", username, supervisor ? "Supervisor que cierra" : "Cajero que cierra",
                supervisor ? 2 : 3, codificador.encode(supervisor ? CONTRASENA_SUPERVISOR : PIN));
        String sesion = cookie(enviar(supervisor ? "/api/auth/login" : "/api/auth/login-pin", "POST",
                login(username, supervisor ? "contrasena" : "pin", supervisor ? CONTRASENA_SUPERVISOR : PIN), equipo),
                "POS_SESION");
        HttpResponse<String> turno = enviar("/api/turnos", "POST", null, sesion);
        assertThat(turno.statusCode()).isEqualTo(201);
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        return new Cajero(usuarioId, sesion, idDe(turno), host);
    }

    private void agregarProducto(Cajero cajero, String precio) throws Exception {
        String codigo = "CI" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        assertThat(enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"Producto del cierre\",\"precio\":%s}".formatted(codigo,
                        precio), cookieAdmin).statusCode()).isEqualTo(201);
        assertThat(enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo),
                cajero.sesion()).statusCode()).isEqualTo(200);
    }

    /** Una venta cobrada con la tarjeta indicada y finalizada; devuelve el id de su factura. */
    private long vender(Cajero cajero, String precio, String tarjeta) throws Exception {
        agregarProducto(cajero, precio);
        assertThat(enviar("/api/ventas/actual/pagos", "POST",
                "{\"tipoTarjeta\":\"%s\",\"autorizacion\":\"123456\"}".formatted(tarjeta), cajero.sesion())
                .statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual/finalizar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        return jdbc.queryForObject("""
                SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id WHERE v.turno_id = ?
                ORDER BY f.id DESC LIMIT 1""", Long.class, cajero.turnoId());
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
