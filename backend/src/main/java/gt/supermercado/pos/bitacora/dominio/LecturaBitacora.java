package gt.supermercado.pos.bitacora.dominio;

import java.util.List;

/** Consultas de solo lectura sobre la bitácora para otros módulos (por ejemplo, el historial de precios, HU-03.11). */
public interface LecturaBitacora {

    /**
     * Los cambios exitosos de {@code campo} registrados con esa acción para la entidad, del más reciente al más
     * antiguo.
     */
    List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo);
}
