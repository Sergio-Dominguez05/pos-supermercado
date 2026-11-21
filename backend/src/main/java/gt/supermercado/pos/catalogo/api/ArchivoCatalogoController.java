package gt.supermercado.pos.catalogo.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catálogo en archivos de Excel o CSV: HU-03.12 exportar (y HU-03.05 la carga, con la misma plantilla). Usa el
 * permiso de cargar el catálogo, que es el de las operaciones masivas sobre él (el Administrador).
 */
@RestController
@RequestMapping("/api/productos")
public class ArchivoCatalogoController {

    private final ExportacionCatalogo exportacion;

    public ArchivoCatalogoController(ExportacionCatalogo exportacion) {
        this.exportacion = exportacion;
    }

    /** HU-03.12: todo el catálogo en {@code XLSX} o {@code CSV}; queda en la bitácora. */
    @GetMapping("/exportar")
    @PreAuthorize("hasAuthority('CATALOGO_IMPORTAR')")
    public ResponseEntity<byte[]> exportar(@RequestParam Formato formato, @AuthenticationPrincipal SesionActiva sesion) {
        ArchivoExportado archivo = exportacion.exportar(formato, sesion);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
