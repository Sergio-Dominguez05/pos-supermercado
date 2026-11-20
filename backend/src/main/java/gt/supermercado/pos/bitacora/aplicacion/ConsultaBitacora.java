package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.CambioRegistrado;
import gt.supermercado.pos.bitacora.dominio.DetalleEvento;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.bitacora.dominio.ExportadorBitacora;
import gt.supermercado.pos.bitacora.dominio.FormatoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroExportado;
import gt.supermercado.pos.bitacora.dominio.FiltrosBitacora;
import gt.supermercado.pos.bitacora.dominio.LecturaBitacora;
import gt.supermercado.pos.bitacora.dominio.OpcionBitacora;
import gt.supermercado.pos.bitacora.dominio.PaginaBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura de la bitácora. Solo consulta: no existe forma de modificarla (HU-11.05).
 * <ul>
 *   <li>HU-11.06: los registros con fecha y hora, usuario, caja, módulo, acción y resultado, del más reciente, por
 *       páginas. Crit. 4: cada búsqueda (con sus filtros) y cada detalle abierto quedan en la bitácora como
 *       {@code BITACORA_CONSULTADA}; cambiar de página no (decisión de Sergio).</li>
 *   <li>HU-11.07: filtros combinables por fechas, usuario, caja, módulo, acción, resultado y número de documento.</li>
 *   <li>HU-11.08: el detalle completo, con los valores anteriores y nuevos y el documento relacionado.</li>
 * </ul>
 */
@Service
public class ConsultaBitacora {

    private final LecturaBitacora lectura;
    private final Bitacora bitacora;
    private final ExportadorBitacora exportador;

    public ConsultaBitacora(LecturaBitacora lectura, Bitacora bitacora, ExportadorBitacora exportador) {
        this.lectura = lectura;
        this.bitacora = bitacora;
        this.exportador = exportador;
    }

    /** Los filtros tal como llegan de la pantalla, antes de validarlos. */
    public record SolicitudBusqueda(LocalDate desde, LocalDate hasta, Long usuarioId, Long cajaId, String modulo,
                                    String accion, Boolean exitoso, String documento, int pagina) {}

    /** Lo que se puede elegir en los filtros. */
    public record OpcionesBitacora(List<OpcionBitacora> usuarios, List<OpcionBitacora> cajas, List<String> modulos,
                                   List<String> acciones) {}

    /** HU-03.11 y otros módulos: los cambios de un campo de una entidad, sin registrar la consulta. */
    @Transactional(readOnly = true)
    public List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo) {
        return lectura.cambios(accion, entidad, entidadId, campo);
    }

    /** HU-11.06 y 11.07. 422 si los filtros no son válidos (el intento también queda en la bitácora). */
    @Transactional
    public PaginaBitacora buscar(SolicitudBusqueda solicitud, SesionActiva sesion) {
        FiltrosBitacora filtros;
        try {
            filtros = validar(solicitud);
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false,
                    "Consulta de la bitácora rechazada: " + error.getMessage(), null, null));
            throw error;
        }
        PaginaBitacora pagina = lectura.buscar(filtros);
        if (filtros.pagina() == 0) {
            bitacora.registrar(evento(sesion, true, "Consulta de la bitácora: %d registros".formatted(pagina.total()),
                    null, detalle(filtros)));
        }
        return pagina;
    }

    /** HU-11.09: un tope para que una exportación sin filtros no agote la memoria; con más, se pide acotar. */
    public static final int MAXIMO_EXPORTACION = 100_000;

    /** Un archivo exportado de la bitácora, listo para descargar. */
    public record ArchivoBitacora(String nombre, String tipoContenido, byte[] contenido, long registros) {}

    /**
     * HU-11.09 Exportar la bitácora.
     * <ul>
     *   <li>Crit. 1: los registros que cumplen los mismos filtros de la consulta (HU-11.07), en Excel o CSV, en orden
     *       de secuencia y con sus cambios (los datos sensibles ya están como "modificado", HU-11.03).</li>
     *   <li>Crit. 2: la exportación queda en la bitácora como {@code BITACORA_EXPORTADA}, con el formato, los filtros
     *       y la cantidad de registros; el intento rechazado también.</li>
     *   <li>Crit. 3: solo el Administrador; lo exige el controlador.</li>
     * </ul>
     */
    @Transactional
    public ArchivoBitacora exportar(SolicitudBusqueda solicitud, FormatoBitacora formato, SesionActiva sesion) {
        FiltrosBitacora filtros;
        long total;
        try {
            filtros = validar(solicitud);
            total = lectura.contar(filtros);
            if (total > MAXIMO_EXPORTACION) {
                throw new NegocioException("EXPORTACION_DEMASIADO_GRANDE", ("La exportación tendría %,d registros; el "
                        + "máximo es %,d. Acote las fechas u otros filtros.").formatted(total, MAXIMO_EXPORTACION));
            }
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.BITACORA_EXPORTADA, false, "Exportación de la bitácora a %s rechazada: %s"
                            .formatted(formato.name(), error.getMessage()), "bitacora", null, null));
            throw error;
        }
        List<RegistroExportado> registros = lectura.exportar(filtros);
        byte[] contenido = exportador.exportar(registros, formato);
        Map<String, Object> detalle = new LinkedHashMap<>(detalle(filtros));
        detalle.put("formato", formato.name());
        detalle.put("registros", registros.size());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.BITACORA_EXPORTADA, true, "Bitácora exportada a %s: %d registros".formatted(
                        formato.name(), registros.size()), "bitacora", null, detalle));
        String nombre = "bitacora_%s_%s.%s".formatted(filtros.desde() == null ? "inicio" : filtros.desde(),
                filtros.hasta() == null ? "hoy" : filtros.hasta(), formato.extension());
        return new ArchivoBitacora(nombre, formato.tipoContenido(), contenido, registros.size());
    }

    /** HU-11.07: los filtros validados; la acción debe existir en el catálogo. */
    private static FiltrosBitacora validar(SolicitudBusqueda solicitud) {
        FiltrosBitacora filtros = new FiltrosBitacora(solicitud.desde(), solicitud.hasta(), solicitud.usuarioId(),
                solicitud.cajaId(), solicitud.modulo(), solicitud.accion(), solicitud.exitoso(), solicitud.documento(),
                solicitud.pagina());
        String accion = filtros.accion();
        if (accion != null && Arrays.stream(AccionSensible.values()).noneMatch(a -> a.name().equals(accion))) {
            throw new NegocioException("ACCION_INEXISTENTE", "La acción \"%s\" no existe.".formatted(accion));
        }
        return filtros;
    }

    /** HU-11.08: 404 si el registro no existe. Abrir el detalle también queda en la bitácora. */
    @Transactional
    public DetalleEvento detalle(long id, SesionActiva sesion) {
        DetalleEvento detalle = lectura.detalle(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "REGISTRO_NO_ENCONTRADO", "El registro de la bitácora no existe."));
        bitacora.registrar(evento(sesion, true, "Detalle del registro N.º %d de la bitácora (%s)".formatted(
                detalle.registro().secuencia(), detalle.registro().accion()), id, null));
        return detalle;
    }

    @Transactional(readOnly = true)
    public OpcionesBitacora opciones() {
        List<String> modulos = Arrays.stream(AccionSensible.values()).map(AccionSensible::modulo).distinct().sorted()
                .toList();
        List<String> acciones = Arrays.stream(AccionSensible.values()).map(AccionSensible::name).sorted().toList();
        return new OpcionesBitacora(lectura.usuarios(), lectura.cajas(), modulos, acciones);
    }

    /** Los filtros usados, para saber qué se consultó. */
    private static Map<String, Object> detalle(FiltrosBitacora filtros) {
        Map<String, Object> usados = new LinkedHashMap<>();
        agregar(usados, "desde", filtros.desde());
        agregar(usados, "hasta", filtros.hasta());
        agregar(usados, "usuarioId", filtros.usuarioId());
        agregar(usados, "cajaId", filtros.cajaId());
        agregar(usados, "modulo", filtros.modulo());
        agregar(usados, "accion", filtros.accion());
        agregar(usados, "exitoso", filtros.exitoso());
        agregar(usados, "documento", filtros.documento());
        return Map.of("filtros", usados);
    }

    private static void agregar(Map<String, Object> usados, String nombre, Object valor) {
        if (valor != null) {
            usados.put(nombre, valor.toString());
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion, Long registroId,
                                         Map<String, Object> detalle) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.BITACORA_CONSULTADA,
                exitoso, descripcion, "bitacora", registroId, detalle);
    }
}
