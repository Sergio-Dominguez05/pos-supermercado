package gt.supermercado.pos.catalogo.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Formato;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-03.12 Exportar el catálogo.
 * <ul>
 *   <li>Crit. 1: todo el catálogo, activos e inactivos, con código, descripción, precio, estado y fecha de la última
 *       modificación, en Excel o CSV.</li>
 *   <li>Crit. 2: las primeras columnas son las de la plantilla de carga de HU-03.05 ({@link ArchivoCatalogo}).</li>
 *   <li>Crit. 3: la exportación queda en la bitácora con el formato y la cantidad de productos.</li>
 * </ul>
 */
@Service
public class ExportacionCatalogo {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private final RepositorioProductos productos;
    private final ArchivoCatalogo.Escritor escritor;
    private final Bitacora bitacora;
    private final Clock reloj;

    @Autowired
    public ExportacionCatalogo(RepositorioProductos productos, ArchivoCatalogo.Escritor escritor, Bitacora bitacora) {
        this(productos, escritor, bitacora, Clock.system(GUATEMALA));
    }

    ExportacionCatalogo(RepositorioProductos productos, ArchivoCatalogo.Escritor escritor, Bitacora bitacora,
                        Clock reloj) {
        this.productos = productos;
        this.escritor = escritor;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** El archivo listo para descargar. */
    public record ArchivoExportado(String nombre, String tipoContenido, byte[] contenido, int productos) {}

    /** Lee el catálogo y registra la exportación en la bitácora (por eso no es de solo lectura). */
    @Transactional
    public ArchivoExportado exportar(Formato formato, SesionActiva sesion) {
        List<Producto> catalogo = productos.todos();
        byte[] contenido = escritor.exportar(catalogo, formato);
        long activos = catalogo.stream().filter(Producto::activo).count();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("formato", formato.name());
        detalle.put("productos", catalogo.size());
        detalle.put("activos", activos);
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.CATALOGO_EXPORTADO, true, "Catálogo exportado a %s: %d productos (%d activos)"
                        .formatted(formato.name(), catalogo.size(), activos), "producto", null, detalle));
        return new ArchivoExportado("catalogo_%s.%s".formatted(LocalDate.now(reloj.withZone(GUATEMALA)),
                formato.extension()), formato.tipoContenido(), contenido, catalogo.size());
    }
}
