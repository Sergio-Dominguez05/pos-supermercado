package gt.supermercado.pos.catalogo.api;

import gt.supermercado.pos.catalogo.aplicacion.CargaCatalogo;
import gt.supermercado.pos.catalogo.aplicacion.CargaCatalogo.Resultado;
import gt.supermercado.pos.catalogo.aplicacion.CargaCatalogo.Revision;
import gt.supermercado.pos.catalogo.aplicacion.ExportacionCatalogo;
import gt.supermercado.pos.catalogo.aplicacion.ExportacionCatalogo.ArchivoExportado;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Formato;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * El catálogo en archivos de Excel o CSV: HU-03.12 exportar y HU-03.05 cargar, con la misma plantilla. Usa el permiso
 * de cargar el catálogo, que es el de las operaciones masivas sobre él (el Administrador).
 */
@RestController
@RequestMapping("/api/productos")
public class ArchivoCatalogoController {

    private final ExportacionCatalogo exportacion;
    private final CargaCatalogo carga;

    public ArchivoCatalogoController(ExportacionCatalogo exportacion, CargaCatalogo carga) {
        this.exportacion = exportacion;
        this.carga = carga;
    }

    /** HU-03.12: todo el catálogo en {@code XLSX} o {@code CSV}; queda en la bitácora. */
    @GetMapping("/exportar")
    @PreAuthorize("hasAuthority('CATALOGO_IMPORTAR')")
    public ResponseEntity<byte[]> exportar(@RequestParam Formato formato, @AuthenticationPrincipal SesionActiva sesion) {
        ArchivoExportado archivo = exportacion.exportar(formato, sesion);
        return descarga(archivo.nombre(), archivo.tipoContenido(), archivo.contenido());
    }

    /** HU-03.05 crit. 1: la plantilla de carga, con las columnas código, descripción y precio. */
    @GetMapping("/plantilla")
    @PreAuthorize("hasAuthority('CATALOGO_IMPORTAR')")
    public ResponseEntity<byte[]> plantilla(@RequestParam Formato formato) {
        return descarga("plantilla_catalogo." + formato.extension(), formato.tipoContenido(), carga.plantilla(formato));
    }

    /** HU-03.05 crit. 2: lo que haría la carga del archivo, sin guardar nada. 422 si no se puede leer. */
    @PostMapping(path = "/carga/revision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CATALOGO_IMPORTAR')")
    public Revision revisar(@RequestPart("archivo") MultipartFile archivo) {
        return carga.revisar(bytes(archivo));
    }

    /**
     * HU-03.05 crit. 5: carga el archivo ya revisado; {@code huella} es la que dio la revisión. 422 si el archivo cambió
     * o no tiene ningún producto válido.
     */
    @PostMapping(path = "/carga", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CATALOGO_IMPORTAR')")
    public Resultado cargar(@RequestPart("archivo") MultipartFile archivo, @RequestParam String huella,
                            @AuthenticationPrincipal SesionActiva sesion) {
        return carga.aplicar(bytes(archivo), huella, sesion);
    }

    private static byte[] bytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static ResponseEntity<byte[]> descarga(String nombre, String tipo, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build()
                        .toString())
                .body(contenido);
    }
}
