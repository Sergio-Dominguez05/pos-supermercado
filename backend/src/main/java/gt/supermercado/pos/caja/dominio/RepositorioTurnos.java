package gt.supermercado.pos.caja.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Turnos de caja. */
public interface RepositorioTurnos {

    Optional<Turno> buscarPorId(long id);

    Optional<Turno> abiertoEnCaja(long cajaId);

    Optional<Turno> abiertoDeUsuario(long usuarioId);

    /** HU-09.06: turnos abiertos que puede consultar un supervisor. */
    List<Turno> abiertos();

    /** Abre el turno con la hora del servidor y devuelve su id. */
    long abrir(long cajaId, long usuarioId);

    /** HU-09.08 crit. 4 y 5: cierra el turno con sus totales. Falso si ya no estaba abierto. */
    boolean cerrar(long turnoId, long cerradoPor, BigDecimal totalRegistrado, BigDecimal totalLote,
                   BigDecimal diferencia, String justificacion);

    Optional<CierreDeTurno> cierre(long turnoId);

    /** HU-09.10 crit. 3: los cierres más recientes; solo los del usuario si {@code usuarioId} no es nulo. */
    List<CierreDeTurno> cierres(Long usuarioId, int limite);

    /** HU-09.13 crit. 1 y 2: los cierres que cumplen los filtros, del más reciente al más antiguo. */
    PaginaCierres buscarCierres(FiltrosCierres filtros);

    /** HU-09.13 crit. 2: las cajas con algún cierre. */
    List<OpcionFiltro> cajasConCierres();

    /** HU-09.13 crit. 2: los cajeros con algún cierre. */
    List<OpcionFiltro> cajerosConCierres();
}
