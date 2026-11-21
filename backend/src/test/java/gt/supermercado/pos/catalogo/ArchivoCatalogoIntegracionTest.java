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

    // HU-03.05

    @Test
    void laPlantillaTraeCodigoDescripcionYPrecio() throws Exception {   // 03.05 crit. 1
        HttpResponse<byte[]> csv = bytes("/api/productos/plantilla?formato=CSV", cookieAdmin);
        assertThat(new String(csv.body(), StandardCharsets.UTF_8)).isEqualTo("﻿Código,Descripción,Precio\r\n");

        HttpResponse<byte[]> excel = bytes("/api/productos/plantilla?formato=XLSX", cookieAdmin);
        assertThat(excel.headers().firstValue("Content-Disposition").orElseThrow()).contains("plantilla_catalogo.xlsx");
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.body()))) {
            Row titulos = libro.getSheetAt(0).getRow(0);
            assertThat(titulos.getLastCellNum()).isEqualTo((short) 3);
            assertThat(titulos.getCell(1).getStringCellValue()).isEqualTo("Descripción");
        }
    }

    @Test
    void laRevisionMuestraNuevosActualizadosYErroresSinGuardarNada() throws Exception {   // 03.05 crit. 2 y 4
        String existente = codigo();
        crear(existente, "Azucar 1 lb", "6.00");
        String nuevo = codigo();
        String repetido = codigo();
        byte[] archivo = csv(nuevo + ",Frijol negro 1 lb,8.50",
                existente + ",Azúcar 1 lb,\"6,25\"",              // cambia descripción y precio (coma decimal)
                ",Sin código,3.00",                               // código vacío
                codigo() + ",Precio malo,abc",                    // precio inválido
                codigo() + ",Precio negativo,-1",
                repetido + ",Uno,1.00",
                repetido + ",Dos,2.00",                           // repetido en el archivo: las dos filas
                ",,");                                            // fila vacía: se ignora

        HttpResponse<String> revision = archivoA("/api/productos/carga/revision", archivo, "catalogo.csv");

        assertThat(revision.statusCode()).as(revision.body()).isEqualTo(200);
        String cuerpo = revision.body();
        assertThat(cuerpo).contains("\"filas\":7", "\"codigo\":\"" + nuevo + "\"", "\"precio\":8.50",
                "\"descripcionAntes\":\"Azucar 1 lb\"", "\"descripcionDespues\":\"Azúcar 1 lb\"",
                "\"precioAntes\":6.00", "\"precioDespues\":6.25", "El código está vacío.", "El precio \\\"abc\\\" no es válido",
                "repetido en el archivo (filas 7, 8)", "\"huella\":\"");
        assertThat(cuerpo.split("\"motivo\"", -1)).hasSize(6);   // 5 filas con error
        // Crit. 5: la revisión no carga nada.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM producto WHERE codigo_barras = ?", Integer.class, nuevo))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE codigo_barras = ?", String.class,
                existente)).isEqualTo("Azucar 1 lb");
    }

    @Test
    void alConfirmarSeCreanYActualizanLosValidosYQuedaEnLaBitacora() throws Exception {   // 03.05 crit. 3, 5 y 6
        String existente = codigo();
        long id = crear(existente, "Leche 1 L", "11.00");
        enviar("/api/productos/" + id + "/desactivar", "POST", null, cookieAdmin);
        String nuevo = codigo();
        byte[] archivo = excel(new Object[][] {{"Código", "Descripción", "Precio", "Estado"},
                {nuevo, "Atún en agua 5 oz", 12.5, "Activo"},
                {existente, "Leche entera 1 L", 11.75, "Activo"},
                {codigo(), "", 3.0, "Activo"}});   // descripción vacía: no se carga
        String huella = huella(archivoA("/api/productos/carga/revision", archivo, "catalogo.xlsx").body());

        // Crit. 5: sin la huella del archivo revisado no se carga.
        assertThat(archivoA("/api/productos/carga?huella=otra", archivo, "catalogo.xlsx").statusCode()).isEqualTo(422);
        HttpResponse<String> carga = archivoA("/api/productos/carga?huella=" + huella, archivo, "catalogo.xlsx");

        assertThat(carga.statusCode()).as(carga.body()).isEqualTo(200);
        assertThat(carga.body()).contains("\"creados\":1", "\"actualizados\":1", "\"errores\":1");
        assertThat(jdbc.queryForMap("SELECT descripcion, precio, activo FROM producto WHERE codigo_barras = ?", nuevo))
                .containsEntry("descripcion", "Atún en agua 5 oz").containsEntry("activo", true);
        // Crit. 3: el existente cambia descripción y precio; su estado no cambia.
        var actualizado = jdbc.queryForMap("SELECT descripcion, precio, activo FROM producto WHERE id = ?", id);
        assertThat(actualizado).containsEntry("descripcion", "Leche entera 1 L").containsEntry("activo", false);
        assertThat((java.math.BigDecimal) actualizado.get("precio")).isEqualByComparingTo("11.75");
        // Crit. 6 y HU-03.11: el resumen de la carga y el cambio de precio para el historial.
        assertThat(jdbc.queryForMap("""
                SELECT (cambios ->> 'creados')::int AS creados, (cambios ->> 'actualizados')::int AS actualizados
                FROM bitacora WHERE accion = 'CATALOGO_CARGADO' AND exitoso AND usuario_id = ?
                ORDER BY secuencia DESC LIMIT 1""", adminId)).containsEntry("creados", 1).containsEntry("actualizados", 1);
        assertThat(enviar("/api/productos/" + id + "/precios", "GET", null, cookieAdmin).body())
                .contains("\"anterior\":11.00", "\"nuevo\":11.75");
    }

    @Test
    void unArchivoSinLasColumnasDeLaPlantillaSeRechaza() throws Exception {   // 03.05 crit. 1
        HttpResponse<String> respuesta = archivoA("/api/productos/carga/revision",
                "Nombre,Valor\r\nArroz,5\r\n".getBytes(StandardCharsets.UTF_8), "otro.csv");

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("PLANTILLA_INVALIDA");
        // Excel en español guarda el CSV con punto y coma: también se lee.
        String codigo = codigo();
        assertThat(archivoA("/api/productos/carga/revision", ("Codigo;Descripcion;Precio\r\n%s;Sal 1 lb;2,50\r\n"
                .formatted(codigo)).getBytes(StandardCharsets.UTF_8), "puntoycoma.csv").body())
                .contains("\"codigo\":\"" + codigo + "\"", "\"precio\":2.50");
    }

    @Test
    void unCatalogoExportadoSeVuelveACargarSinCambios() throws Exception {   // 03.12 crit. 2 con 03.05
        crear(codigo(), "=Producto con signo", "4.00");
        byte[] exportado = bytes("/api/productos/exportar?formato=XLSX", cookieAdmin).body();

        String revision = archivoA("/api/productos/carga/revision", exportado, "catalogo.xlsx").body();

        assertThat(revision).contains("\"nuevos\":[]", "\"actualizados\":[]", "\"errores\":[]");
        assertThat(revision).contains("\"sinCambios\":" + jdbc.queryForObject("SELECT count(*) FROM producto",
                Integer.class));
    }

    private static byte[] csv(String... filas) {
        return ("Código,Descripción,Precio\r\n" + String.join("\r\n", filas) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] excel(Object[][] filas) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Hoja1");
            for (int r = 0; r < filas.length; r++) {
                Row fila = hoja.createRow(r);
                for (int c = 0; c < filas[r].length; c++) {
                    if (filas[r][c] instanceof Double numero) {
                        fila.createCell(c).setCellValue(numero);
                    } else {
                        fila.createCell(c).setCellValue(String.valueOf(filas[r][c]));
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static String huella(String revision) {
        return revision.replaceAll("(?s).*\"huella\":\"([0-9a-f]+)\".*", "$1");
    }

    private HttpResponse<String> archivoA(String ruta, byte[] contenido, String nombre) throws Exception {
        String limite = "----limite" + System.nanoTime();
        java.io.ByteArrayOutputStream cuerpo = new java.io.ByteArrayOutputStream();
        cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(contenido);
        cuerpo.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "multipart/form-data; boundary=" + limite).header("Cookie", cookieAdmin)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
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
