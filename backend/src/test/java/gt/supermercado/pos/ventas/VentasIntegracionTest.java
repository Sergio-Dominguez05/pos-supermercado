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

/**
 * EP-06 Ventas contra PostgreSQL real con las migraciones: HU-06.01 Iniciar una venta, HU-06.02 Agregar producto con
 * lector de código de barras, HU-06.03 Agregar producto por búsqueda o código manual y HU-06.14 Cancelar una venta en
 * curso.
 */
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
        // Ninguna de las peticiones que encontró la venta ya creada consumió un número (HU-06.01 crit. 4).
        enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());
        long siguiente = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        assertThat(numeroDe(siguiente)).isEqualTo(numeroDe(ids.getFirst()) + 1);
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

    // HU-06.02 Agregar producto con lector de código de barras

    @Test
    void unProductoEscaneadoSeAgregaConCantidadUnoYSuPrecioVigente() throws Exception {   // crit. 1
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");

        HttpResponse<String> respuesta = escanear(arroz.codigo(), cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"descripcion\":\"Arroz 1 lb\"", "\"cantidad\":1",
                "\"precioUnitario\":5.00", "\"codigoBarras\":\"%s\"".formatted(arroz.codigo()), "\"articulos\":1",
                "\"total\":5.00");
        Map<String, Object> linea = jdbc.queryForMap("""
                SELECT l.cantidad, l.precio_unitario, l.descripcion FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO'""", cajero.usuarioId());
        assertThat(linea).containsEntry("cantidad", 1).containsEntry("descripcion", "Arroz 1 lb");
        assertThat((BigDecimal) linea.get("precio_unitario")).isEqualByComparingTo("5.00");
    }

    @Test
    void escanearElMismoProductoSumaUnoASuLineaSinCrearOtra() throws Exception {   // crit. 2
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");

        escanear(arroz.codigo(), cajero);
        escanear(arroz.codigo(), cajero);
        HttpResponse<String> respuesta = escanear(arroz.codigo(), cajero);

        assertThat(respuesta.body()).contains("\"cantidad\":3", "\"articulos\":3");
        assertThat(lineasEnCurso(cajero)).isEqualTo(1);
    }

    @Test
    void cadaLineaGuardaSuDesgloseYLosTotalesSeRedondeanAlFinal() throws Exception {   // HU-04.03 y HU-04.04
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        for (int i = 0; i < 3; i++) {
            escanear(arroz.codigo(), cajero);
        }

        HttpResponse<String> respuesta = escanear(nuevoProducto("Agua 600 ml", "11.20").codigo(), cajero);

        Map<String, Object> linea = jdbc.queryForMap("""
                SELECT l.subtotal_con_iva, l.base_imponible, l.iva FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND l.producto_id = ?""", cajero.usuarioId(), arroz.id());
        assertThat(linea.get("subtotal_con_iva").toString()).isEqualTo("15.000000");
        assertThat(linea.get("base_imponible").toString()).isEqualTo("13.392857");
        assertThat(linea.get("iva").toString()).isEqualTo("1.607143");
        // 3 x Q5.00 + Q11.20: IVA = 1.607143 + 1.200000 = 2.807143, que da Q2.81; Subtotal = TOTAL - IVA.
        assertThat(respuesta.body()).contains("\"articulos\":4", "\"subtotal\":23.39", "\"iva\":2.81", "\"total\":26.20");
        // HU-04.03 crit. 3: la venta entrega el desglose de cada línea, con sus 6 decimales, para mostrarlo.
        assertThat(respuesta.body()).contains("\"subtotalConIva\":15.000000", "\"baseImponible\":13.392857",
                "\"iva\":1.607143");
    }

    @Test
    void cadaProductoAgregadoDevuelveLosTotalesActualizados() throws Exception {   // HU-06.08 crit. 1 y 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        Producto agua = nuevoProducto("Agua 600 ml", "11.20");

        // Q5.00: IVA = 5.00 - 5.00 / 1.12 = 0.535714, que da Q0.54.
        assertThat(escanear(arroz.codigo(), cajero).body())
                .contains("\"articulos\":1", "\"subtotal\":4.46", "\"iva\":0.54", "\"total\":5.00");
        assertThat(escanear(agua.codigo(), cajero).body())
                .contains("\"articulos\":2", "\"subtotal\":14.46", "\"iva\":1.74", "\"total\":16.20");
        assertThat(escanear(arroz.codigo(), cajero).body())
                .contains("\"articulos\":3", "\"subtotal\":18.93", "\"iva\":2.27", "\"total\":21.20");
        // Al volver a la pantalla (o recargarla) se ven los mismos totales.
        assertThat(enviar("/api/ventas/actual", "GET", null, cajero.sesion()).body())
                .contains("\"articulos\":3", "\"subtotal\":18.93", "\"iva\":2.27", "\"total\":21.20");
    }

    @Test
    void elCodigoEscritoAManoSeNormalizaComoElDelLector() throws Exception {   // HU-03.06 crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto producto = nuevoProducto("Café 400 g", "32.50");

        HttpResponse<String> respuesta = escanear("  " + producto.codigo().toLowerCase() + " ", cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("Café 400 g");
    }

    @Test
    void sinVentaIniciadaElPrimerProductoLaInicia() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();

        assertThat(escanear(nuevoProducto("Sal 1 lb", "3.00").codigo(), cajero).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/ventas/actual", "GET", null, cajero.sesion()).body()).contains("Sal 1 lb");
    }

    @Test
    void unCodigoQueNoExisteNoAgregaNadaYQuedaAnotado() throws Exception {   // crit. 4, HU-03.08
        Cajero cajero = cajeroConTurnoAbierto();
        String codigo = "NOEXISTE" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;

        HttpResponse<String> respuesta = escanear(codigo, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).contains("PRODUCTO_NO_ENCONTRADO");
        assertThat(lineasEnCurso(cajero)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_no_encontrado WHERE codigo = ? AND usuario_id = ?",
                Integer.class, codigo, cajero.usuarioId())).isEqualTo(1);
    }

    @Test
    void unProductoInactivoNoSeAgrega() throws Exception {   // crit. 4, HU-03.09
        Cajero cajero = cajeroConTurnoAbierto();
        Producto galleta = nuevoProducto("Galleta descontinuada", "2.50");
        enviar("/api/productos/" + galleta.id() + "/desactivar", "POST", null, cookieAdmin);

        HttpResponse<String> respuesta = escanear(galleta.codigo(), cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("PRODUCTO_INACTIVO", "no está disponible para la venta");
        assertThat(lineasEnCurso(cajero)).isZero();
    }

    @Test
    void conLaCajaCerradaNoSeAgreganProductos() throws Exception {
        Cajero cajero = cajeroEnCaja();

        HttpResponse<String> respuesta = escanear(nuevoProducto("Leche 1 l", "12.00").codigo(), cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CERRADA");
    }

    @Test
    void escaneosSimultaneosDelMismoProductoSumanTodosEnUnaLinea() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        Producto producto = nuevoProducto("Jabón", "7.00");
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> escanear(producto.codigo(), cajero).statusCode());
        }

        List<Integer> estados = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            for (Future<Integer> estado : hilos.invokeAll(tareas)) {
                estados.add(estado.get());
            }
        }

        assertThat(estados).containsOnly(200);
        assertThat(jdbc.queryForObject("""
                SELECT l.cantidad FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO'""", Integer.class, cajero.usuarioId())).isEqualTo(10);
    }

    @Test
    void escanearYConsultarLaVentaNoConsumeNumerosDeVenta() throws Exception {   // HU-06.01 crit. 4
        Cajero cajero = cajeroConTurnoAbierto();
        Producto producto = nuevoProducto("Fideo", "4.00");
        long primera = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        for (int i = 0; i < 5; i++) {
            escanear(producto.codigo(), cajero);
            enviar("/api/ventas", "POST", null, cajero.sesion());
        }
        enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());

        long segunda = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));

        // Las pruebas corren una tras otra: nadie más toma números de la secuencia entre las dos ventas.
        assertThat(numeroDe(segunda)).isEqualTo(numeroDe(primera) + 1);
    }

    @Test
    void elFlujoCompletoEscanearCancelarYCerrarSesion() throws Exception {   // HU-06.14 y HU-01.16 con productos reales
        Cajero cajero = cajeroConTurnoAbierto();
        escanear(nuevoProducto("Pan dulce", "1.50").codigo(), cajero);

        assertThat(enviar("/api/auth/logout", "POST", null, cajero.sesion()).statusCode()).isEqualTo(409);
        assertThat(enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/auth/logout", "POST", null, cajero.sesion()).statusCode()).isEqualTo(204);
    }

    // HU-06.03 Agregar producto por búsqueda o código manual

    @Test
    void elProductoElegidoEnLaBusquedaSeAgregaALaVenta() throws Exception {   // crit. 2
        Cajero cajero = cajeroConTurnoAbierto();
        Producto cafe = nuevoProducto("Café molido 400 g", "32.50");

        HttpResponse<String> respuesta = elegir(cafe.id(), cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("Café molido 400 g", "\"cantidad\":1", "\"total\":32.50");
    }

    @Test
    void elegirUnProductoQueYaEstaSumaASuLineaIgualQueEscanearlo() throws Exception {   // crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto cafe = nuevoProducto("Café molido 400 g", "32.50");
        escanear(cafe.codigo(), cajero);

        HttpResponse<String> respuesta = elegir(cafe.id(), cajero);

        assertThat(respuesta.body()).contains("\"cantidad\":2", "\"articulos\":2");
        assertThat(lineasEnCurso(cajero)).isEqualTo(1);
    }

    @Test
    void unProductoDesactivadoDespuesDeLaBusquedaNoSeAgrega() throws Exception {   // HU-03.09
        Cajero cajero = cajeroConTurnoAbierto();
        Producto galleta = nuevoProducto("Galleta de temporada", "3.00");
        enviar("/api/productos/" + galleta.id() + "/desactivar", "POST", null, cookieAdmin);

        HttpResponse<String> respuesta = elegir(galleta.id(), cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("PRODUCTO_INACTIVO");
        assertThat(lineasEnCurso(cajero)).isZero();
    }

    @Test
    void unProductoQueNoExisteDa404() throws Exception {
        assertThat(elegir(999_999_999L, cajeroConTurnoAbierto()).statusCode()).isEqualTo(404);
    }

    @Test
    void seIndicaElCodigoOElProductoPeroNoAmbosNiNinguno() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        Producto producto = nuevoProducto("Té 20 sobres", "9.00");

        HttpResponse<String> ninguno = enviar("/api/ventas/actual/lineas", "POST", "{}", cajero.sesion());
        HttpResponse<String> ambos = enviar("/api/ventas/actual/lineas", "POST",
                "{\"codigo\":\"%s\",\"productoId\":%d}".formatted(producto.codigo(), producto.id()), cajero.sesion());

        assertThat(ninguno.statusCode()).isEqualTo(400);
        assertThat(ambos.statusCode()).isEqualTo(400);
        assertThat(lineasEnCurso(cajero)).isZero();
    }

    // HU-06.09 Mantener el precio durante la venta

    @Test
    void cadaLineaGuardaElPrecioVigenteAlAgregarla() throws Exception {   // crit. 1
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), cajero);

        assertThat(precioDeLineas(cajero, arroz)).containsExactly("5.00");
    }

    @Test
    void unCambioDePrecioDuranteLaVentaNoCambiaLasLineasYaAgregadas() throws Exception {   // crit. 2, HU-03.02 crit. 4
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), cajero);
        escanear(arroz.codigo(), cajero);

        cambiarPrecio(arroz, "5.50");
        HttpResponse<String> venta = enviar("/api/ventas/actual", "GET", null, cajero.sesion());

        assertThat(precioDeLineas(cajero, arroz)).containsExactly("5.00");
        assertThat(venta.body()).contains("\"cantidad\":2", "\"precioUnitario\":5.00", "\"total\":10.00");
    }

    @Test
    void loQueSeAgregaDespuesDelCambioUsaElPrecioNuevoEnOtraLinea() throws Exception {   // crit. 3, HU-03.02 crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), cajero);
        escanear(arroz.codigo(), cajero);

        cambiarPrecio(arroz, "5.50");
        escanear(arroz.codigo(), cajero);
        HttpResponse<String> respuesta = elegir(arroz.id(), cajero);   // también por búsqueda (HU-06.03)

        // 2 × Q5.00 en su línea original y 2 × Q5.50 en una línea nueva.
        assertThat(precioDeLineas(cajero, arroz)).containsExactly("5.00", "5.50");
        assertThat(jdbc.queryForList("""
                SELECT l.cantidad FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO' ORDER BY l.id""", Integer.class, cajero.usuarioId()))
                .containsExactly(2, 2);
        assertThat(respuesta.body()).contains("\"articulos\":4", "\"total\":21.00");
    }

    @Test
    void unProductoQueEntraALaVentaDespuesDelCambioUsaElPrecioNuevo() throws Exception {   // crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto azucar = nuevoProducto("Azúcar 1 lb", "6.00");
        cambiarPrecio(azucar, "6.75");

        escanear(azucar.codigo(), cajero);

        assertThat(precioDeLineas(cajero, azucar)).containsExactly("6.75");
    }

    // HU-06.14 Cancelar una venta en curso

    @Test
    void cancelarDejaLaVentaCanceladaYLaCajaListaParaLaSiguiente() throws Exception {   // crit. 3 y HU-06.01 crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        long venta = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        agregarLinea(venta, nuevoProducto("Arroz 1 lb", "5.00"), 3);

        HttpResponse<String> respuesta = enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"estado\":\"CANCELADA\"");
        assertThat(jdbc.queryForObject("SELECT estado FROM venta WHERE id = ?", String.class, venta)).isEqualTo("CANCELADA");
        assertThat(enviar("/api/ventas/actual", "GET", null, cajero.sesion()).statusCode()).isEqualTo(204);
        long siguiente = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        assertThat(siguiente).isNotEqualTo(venta);
    }

    @Test
    void elCajeroCancelaSinAutorizacionDeUnSupervisor() throws Exception {   // crit. 2
        Cajero cajero = cajeroConTurnoAbierto();   // rol Cajero
        long venta = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        agregarLinea(venta, nuevoProducto("Frijol 1 lb", "8.50"), 1);

        assertThat(enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion()).statusCode()).isEqualTo(200);
    }

    @Test
    void laCancelacionQuedaEnLaBitacoraConElDetalleDeLosProductos() throws Exception {   // crit. 4
        Cajero cajero = cajeroConTurnoAbierto();
        long venta = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        agregarLinea(venta, arroz, 3);

        enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT usuario_id, exitoso, entidad, entidad_id, descripcion, cambios::text AS cambios FROM bitacora
                WHERE accion = 'VENTA_CANCELADA' AND entidad_id = ?""", venta);
        assertThat(registro).containsEntry("usuario_id", cajero.usuarioId()).containsEntry("exitoso", true)
                .containsEntry("entidad", "venta");
        assertThat((String) registro.get("descripcion")).contains("cancelada", "3 artículos", "Q15.00");
        assertThat((String) registro.get("cambios")).contains(arroz.codigo(), "Arroz 1 lb", "\"cantidad\": 3");
    }

    @Test
    void sinVentaEnCursoNoHayNadaQueCancelar() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();

        HttpResponse<String> respuesta = enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("SIN_VENTA_EN_CURSO");
    }

    @Test
    void conLaCajaCerradaNoSeCancela() throws Exception {
        HttpResponse<String> respuesta = enviar("/api/ventas/actual/cancelar", "POST", null, cajeroEnCaja().sesion());

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CERRADA");
    }

    // HU-01.16 crit. 2: no se cierra sesión con una venta en curso (reemplaza a SinVentasEnCurso)

    @Test
    void conUnaVentaConProductosNoSeCierraSesionHastaCancelarla() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        long venta = idDe(enviar("/api/ventas", "POST", null, cajero.sesion()));
        agregarLinea(venta, nuevoProducto("Azúcar 1 lb", "6.25"), 2);

        HttpResponse<String> rechazado = enviar("/api/auth/logout", "POST", null, cajero.sesion());

        assertThat(rechazado.statusCode()).isEqualTo(409);
        assertThat(rechazado.body()).contains("VENTA_EN_CURSO");
        assertThat(enviar("/api/auth/sesion", "GET", null, cajero.sesion()).statusCode()).isEqualTo(200);

        enviar("/api/ventas/actual/cancelar", "POST", null, cajero.sesion());
        assertThat(enviar("/api/auth/logout", "POST", null, cajero.sesion()).statusCode()).isEqualTo(204);
    }

    @Test
    void unaVentaVaciaNoImpideCerrarSesion() throws Exception {
        Cajero cajero = cajeroConTurnoAbierto();
        enviar("/api/ventas", "POST", null, cajero.sesion());

        assertThat(enviar("/api/auth/logout", "POST", null, cajero.sesion()).statusCode()).isEqualTo(204);
    }

    // HU-06.04 Indicar la cantidad de un producto

    @Test
    void conElMultiplicadorSeAgreganVariasUnidadesDeUnaVez() throws Exception {   // crit. 1
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");

        HttpResponse<String> respuesta = escanear(arroz.codigo(), 6, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"cantidad\":6", "\"articulos\":6", "\"total\":30.00");
        assertThat(lineasEnCurso(cajero)).isEqualTo(1);
        // Sin multiplicador vuelve a ser una unidad, que se suma a la misma línea (HU-06.02 crit. 2).
        assertThat(escanear(arroz.codigo(), cajero).body()).contains("\"cantidad\":7", "\"total\":35.00");
    }

    @Test
    void elMultiplicadorSeSumaALaLineaYTambienValeEnLaBusqueda() throws Exception {   // crit. 1 y HU-06.03
        Cajero cajero = cajeroConTurnoAbierto();
        Producto agua = nuevoProducto("Agua 600 ml", "11.20");
        escanear(agua.codigo(), cajero);

        HttpResponse<String> respuesta = enviar("/api/ventas/actual/lineas", "POST",
                "{\"productoId\":%d,\"cantidad\":3}".formatted(agua.id()), cajero.sesion());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"cantidad\":4", "\"articulos\":4", "\"total\":44.80");
        assertThat(lineasEnCurso(cajero)).isEqualTo(1);
    }

    @Test
    void laCantidadDebeSerUnEnteroMayorQueCero() throws Exception {   // crit. 3
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");

        for (String cantidad : List.of("0", "-2", "10000")) {
            HttpResponse<String> respuesta = enviar("/api/ventas/actual/lineas", "POST",
                    "{\"codigo\":\"%s\",\"cantidad\":%s,\"confirmada\":true}".formatted(arroz.codigo(), cantidad),
                    cajero.sesion());
            assertThat(respuesta.statusCode()).as("cantidad " + cantidad).isEqualTo(422);
            assertThat(respuesta.body()).contains("CANTIDAD_INVALIDA");
        }
        for (String cantidad : List.of("2.5", "\"seis\"")) {
            HttpResponse<String> respuesta = enviar("/api/ventas/actual/lineas", "POST",
                    "{\"codigo\":\"%s\",\"cantidad\":%s}".formatted(arroz.codigo(), cantidad), cajero.sesion());
            assertThat(respuesta.statusCode()).as("cantidad " + cantidad).isBetween(400, 422);
        }
        assertThat(lineasEnCurso(cajero)).isZero();
    }

    @Test
    void unaCantidadMayorAlLimiteSePideConfirmarAntesDeAgregarla() throws Exception {   // crit. 4
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");

        HttpResponse<String> sinConfirmar = escanear(arroz.codigo(), 51, cajero);

        assertThat(sinConfirmar.statusCode()).isEqualTo(422);
        assertThat(sinConfirmar.body()).contains("CANTIDAD_POR_CONFIRMAR", "51", "50");
        assertThat(lineasEnCurso(cajero)).isZero();

        HttpResponse<String> confirmada = enviar("/api/ventas/actual/lineas", "POST",
                "{\"codigo\":\"%s\",\"cantidad\":51,\"confirmada\":true}".formatted(arroz.codigo()), cajero.sesion());
        assertThat(confirmada.statusCode()).isEqualTo(200);
        assertThat(confirmada.body()).contains("\"cantidad\":51", "\"total\":255.00");
    }

    @Test
    void elLimiteEsElConfiguradoYHastaElNoSePideConfirmacion() throws Exception {   // crit. 4 y HU-12.13
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        assertThat(escanear(arroz.codigo(), 50, cajero).statusCode()).isEqualTo(200);

        String anterior = jdbc.queryForObject("SELECT valor::text FROM parametro WHERE clave = 'cantidad_max_linea'",
                String.class);
        jdbc.update("UPDATE parametro SET valor = '10' WHERE clave = 'cantidad_max_linea'");
        try {
            assertThat(escanear(arroz.codigo(), 10, cajero).statusCode()).isEqualTo(200);
            assertThat(escanear(arroz.codigo(), 11, cajero).body()).contains("CANTIDAD_POR_CONFIRMAR");
        } finally {
            jdbc.update("UPDATE parametro SET valor = ?::jsonb WHERE clave = 'cantidad_max_linea'", anterior);
        }
    }

    @Test
    void sePuedeCambiarLaCantidadDeUnaLineaConSuPropioPrecio() throws Exception {   // crit. 2 y HU-04.04 crit. 6
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), 3, cajero);
        cambiarPrecio(arroz, "6.00");   // la línea conserva Q5.00 (HU-06.09)

        HttpResponse<String> respuesta = cambiarCantidad(lineaDe(cajero, arroz), 5, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        // 5 x Q5.00 = Q25.00: IVA = 25.00 - 25.00 / 1.12 = 2.678571, que da Q2.68.
        assertThat(respuesta.body()).contains("\"cantidad\":5", "\"precioUnitario\":5.00", "\"articulos\":5",
                "\"subtotal\":22.32", "\"iva\":2.68", "\"total\":25.00");
        // También se puede bajar la cantidad.
        assertThat(cambiarCantidad(lineaDe(cajero, arroz), 2, cajero).body())
                .contains("\"cantidad\":2", "\"total\":10.00");
        assertThat(lineasEnCurso(cajero)).isEqualTo(1);
    }

    @Test
    void alCambiarLaCantidadSeAplicanLasMismasReglas() throws Exception {   // crit. 2, 3 y 4
        Cajero cajero = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), cajero);
        long linea = lineaDe(cajero, arroz);

        assertThat(cambiarCantidad(linea, 0, cajero).body()).contains("CANTIDAD_INVALIDA");
        assertThat(cambiarCantidad(linea, 80, cajero).body()).contains("CANTIDAD_POR_CONFIRMAR");
        assertThat(enviar("/api/ventas/actual/lineas/" + linea + "/cantidad", "PUT", "{}", cajero.sesion()).statusCode())
                .isEqualTo(400);
        assertThat(enviar("/api/ventas/actual", "GET", null, cajero.sesion()).body()).contains("\"cantidad\":1");

        HttpResponse<String> confirmada = enviar("/api/ventas/actual/lineas/" + linea + "/cantidad", "PUT",
                "{\"cantidad\":80,\"confirmada\":true}", cajero.sesion());
        assertThat(confirmada.statusCode()).isEqualTo(200);
        assertThat(confirmada.body()).contains("\"cantidad\":80");
    }

    @Test
    void noSePuedeCambiarUnaLineaDeOtraVenta() throws Exception {   // crit. 2: solo la venta en curso propia
        Cajero ana = cajeroConTurnoAbierto();
        Cajero beto = cajeroConTurnoAbierto();
        Producto arroz = nuevoProducto("Arroz 1 lb", "5.00");
        escanear(arroz.codigo(), ana);
        escanear(nuevoProducto("Sal 1 lb", "3.00").codigo(), beto);

        HttpResponse<String> respuesta = cambiarCantidad(lineaDe(ana, arroz), 9, beto);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).contains("LINEA_NO_ENCONTRADA");
        assertThat(enviar("/api/ventas/actual", "GET", null, ana.sesion()).body()).contains("\"cantidad\":1");
    }

    @Test
    void sinTurnoAbiertoNoSeCambiaLaCantidad() throws Exception {
        Cajero cajero = cajeroEnCaja();

        HttpResponse<String> respuesta = cambiarCantidad(1, 2, cajero);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CAJA_CERRADA");
    }

    // Apoyo

    private HttpResponse<String> escanear(String codigo, int cantidad, Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/lineas", "POST",
                "{\"codigo\":\"%s\",\"cantidad\":%d}".formatted(codigo, cantidad), cajero.sesion());
    }

    private HttpResponse<String> cambiarCantidad(long linea, int cantidad, Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/lineas/" + linea + "/cantidad", "PUT",
                "{\"cantidad\":%d}".formatted(cantidad), cajero.sesion());
    }

    private long lineaDe(Cajero cajero, Producto producto) {
        return jdbc.queryForObject("""
                SELECT l.id FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO' AND l.producto_id = ?""",
                Long.class, cajero.usuarioId(), producto.id());
    }

    private HttpResponse<String> escanear(String codigo, Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/lineas", "POST", "{\"codigo\":\"%s\"}".formatted(codigo), cajero.sesion());
    }

    private HttpResponse<String> elegir(long productoId, Cajero cajero) throws Exception {
        return enviar("/api/ventas/actual/lineas", "POST", "{\"productoId\":%d}".formatted(productoId), cajero.sesion());
    }

    /** El administrador cambia el precio (HU-03.02). */
    private void cambiarPrecio(Producto producto, String precio) throws Exception {
        HttpResponse<String> editado = enviar("/api/productos/" + producto.id(), "PUT",
                "{\"descripcion\":\"%s\",\"precio\":%s}".formatted(producto.descripcion(), precio), cookieAdmin);
        assertThat(editado.statusCode()).isEqualTo(200);
    }

    private List<String> precioDeLineas(Cajero cajero, Producto producto) {
        return jdbc.queryForList("""
                SELECT l.precio_unitario::text FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO' AND l.producto_id = ? ORDER BY l.id""",
                String.class, cajero.usuarioId(), producto.id());
    }

    private long numeroDe(long venta) {
        return jdbc.queryForObject("SELECT numero FROM venta WHERE id = ?", Long.class, venta);
    }

    private int lineasEnCurso(Cajero cajero) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO'""", Integer.class, cajero.usuarioId());
    }

    record Producto(long id, String codigo, String descripcion, BigDecimal precio) {}

    private Producto nuevoProducto(String descripcion, String precio) throws Exception {
        String codigo = "VT" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
        HttpResponse<String> creado = enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":%s}".formatted(codigo, descripcion, precio),
                cookieAdmin);
        assertThat(creado.statusCode()).isEqualTo(201);
        return new Producto(idDe(creado), codigo, descripcion, new BigDecimal(precio));
    }

    /** Agregar productos llega con HU-06.02; mientras tanto la línea se escribe como la guardará la venta. */
    private void agregarLinea(long venta, Producto producto, int cantidad) {
        var linea = gt.supermercado.pos.impuestos.dominio.CalculadoraIva.linea(producto.precio(), cantidad);
        jdbc.update("""
                INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario, subtotal_con_iva,
                                         base_imponible, iva)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", venta, producto.id(), producto.descripcion(), cantidad,
                producto.precio(), linea.subtotalConIva(), linea.base(), linea.iva());
    }

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
