package gt.supermercado.pos.ventas.dominio;

import java.math.BigDecimal;
import java.util.Optional;

public interface RepositorioVentas {

    /** La venta en curso del turno, con sus líneas. */
    Optional<Venta> enCursoDelTurno(long turnoId);

    Optional<Venta> buscarPorId(long id);

    /**
     * Crea la venta en curso del turno si todavía no tiene una y devuelve su id. Si otra petición la creó al mismo
     * tiempo, devuelve esa: un turno tiene como máximo una venta en curso (índice {@code venta_en_curso_por_turno}).
     */
    long iniciar(long turnoId, long cajaId, long usuarioId, BigDecimal tasaIva);

    /**
     * Pasa la venta de {@code EN_CURSO} a {@code CANCELADA} (HU-06.14). Devuelve {@code false} si ya no estaba en
     * curso, por ejemplo porque otra petición la canceló al mismo tiempo.
     */
    boolean cancelar(long ventaId);

    /** Si el usuario tiene una venta en curso con al menos un producto (HU-01.16 crit. 2). */
    boolean tieneVentaConProductos(long usuarioId);}
