package gt.supermercado.pos.catalogo;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03.12 Exportar el catálogo (y HU-03.05 Cargarlo, con la misma plantilla), contra PostgreSQL real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ArchivoCatalogoIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ArchivoCatalogoIntegracionTest {

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

    // HU-03.12

    @Test
    void exportaTodoElCatalogoConEstadoYUltimaModificacion() throws Exception {   // 03.12 crit. 1
        String activo = codigo();
        String inactivo = codigo();
        crear(activo, "Café molido, \"tostado\" 1 lb", "45.50");
        long id = crear(inactivo, "=Cloro 1 gal", "18.00");
        enviar("/api/productos/" + id + "/desactivar", "POST", null, cookieAdmin);

        HttpResponse<byte[]> csv = bytes("/api/productos/exportar?formato=CSV", cookieAdmin);

        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Disposition").orElseThrow()).contains("attachment", "catalogo_",
                ".csv");
        String texto = new String(csv.body(), StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿Código,Descripción,Precio,Estado,Última modificación\r\n");
        assertThat(texto).contains(activo + ",\"Café molido, \"\"tostado\"\" 1 lb\",45.50,Activo,")
                .contains(inactivo + ",'=Cloro 1 gal,18.00,Inactivo,");   // los inactivos también, sin fórmulas

        HttpResponse<byte[]> excel = bytes("/api/productos/exportar?formato=XLSX", cookieAdmin);
        assertThat(excel.statusCode()).isEqualTo(200);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.body()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Última modificación");
            Row fila = buscar(hoja, activo);
            assertThat(fila.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);   // el precio es un número
            assertThat(fila.getCell(2).getNumericCellValue()).isEqualTo(45.50);
            assertThat(fila.getCell(3).getStringCellValue()).isEqualTo("Activo");
            assertThat(fila.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);   // y la fecha, una fecha
        }
    }

    @Test
    void lasPrimerasColumnasSonLasDeLaPlantillaDeCarga() throws Exception {   // 03.12 crit. 2
        HttpResponse<byte[]> excel = bytes("/api/productos/exportar?formato=XLSX", cookieAdmin);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.body()))) {
            Row titulos = libro.getSheetAt(0).getRow(0);
            assertThat(titulos.getCell(0).getStringCellValue()).isEqualTo("Código");
            assertThat(titulos.getCell(1).getStringCellValue()).isEqualTo("Descripción");
            assertThat(titulos.getCell(2).getStringCellValue()).isEqualTo("Precio");
        }
    }

    @Test
    void laExportacionQuedaEnLaBitacoraYSoloLaHaceQuienCargaElCatalogo() throws Exception {   // 03.12 crit. 3
        bytes("/api/productos/exportar?formato=CSV", cookieAdmin);

        assertThat(jdbc.queryForMap("""
                SELECT cambios ->> 'formato' AS formato, (cambios ->> 'productos')::int AS productos FROM bitacora
                WHERE accion = 'CATALOGO_EXPORTADO' AND usuario_id = ? ORDER BY secuencia DESC LIMIT 1""", adminId))
                .containsEntry("formato", "CSV")
                .containsEntry("productos", jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class));

        String username = "cat.s" + SIGUIENTE.incrementAndGet() + "." + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor del catálogo', 2, ?, false)""", username, codificador.encode("Clave2026segura"));
        String supervisor = cookie(enviar("/api/auth/login", "POST", login(username, "Clave2026segura"), null));
        assertThat(bytes("/api/productos/exportar?formato=CSV", supervisor).statusCode()).isEqualTo(403);
        assertThat(bytes("/api/productos/exportar?formato=PDF", cookieAdmin).statusCode()).isEqualTo(400);
    }

    // Apoyo

    private static String codigo() {
        return "EXP" + SIGUIENTE.incrementAndGet() + "-" + System.nanoTime() % 1_000_000;
    }

    private long crear(String codigo, String descripcion, String precio) throws Exception {
        HttpResponse<String> respuesta = enviar("/api/productos", "POST",
                "{\"codigoBarras\":\"%s\",\"descripcion\":\"%s\",\"precio\":%s}".formatted(codigo,
                        descripcion.replace("\"", "\\\""), precio), cookieAdmin);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
        return jdbc.queryForObject("SELECT id FROM producto WHERE codigo_barras = ?", Long.class, codigo);
    }

    private static Row buscar(Sheet hoja, String codigo) {
        for (Row fila : hoja) {
            if (fila.getCell(0) != null && codigo.equals(fila.getCell(0).getStringCellValue())) {
                return fila;
            }
        }
        throw new AssertionError("No se encontró el producto " + codigo);
    }

    private static String login(String username, String contrasena) {
        return "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena);
    }

    private static String cookie(HttpResponse<String> respuesta) {
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("POS_SESION=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<byte[]> bytes(String ruta, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("Cookie", cookie)
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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
