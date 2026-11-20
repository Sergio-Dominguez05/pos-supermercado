package gt.supermercado.pos.bitacora.dominio;

import java.util.List;
import java.util.Optional;

/** Consultas de solo lectura sobre la bitácora para otros módulos (por ejemplo, el historial de precios, HU-03.11). */
public interface LecturaBitacora {

    /**
     * Los cambios exitosos de {@code campo} registrados con esa acción para la entidad, del más reciente al más
     * antiguo.
     */
    List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo);

    /** HU-11.06 y 11.07: los registros que cumplen los filtros, del más reciente al más antiguo. */
    PaginaBitacora buscar(FiltrosBitacora filtros);

    /** HU-11.08: el registro completo, con el documento relacionado si lo hay. */
    Optional<DetalleEvento> detalle(long id);

    /** HU-11.07: los usuarios que aparecen en la bitácora. */
    List<OpcionBitacora> usuarios();

    /** HU-11.07: las cajas que aparecen en la bitácora. */
    List<OpcionBitacora> cajas();
}
