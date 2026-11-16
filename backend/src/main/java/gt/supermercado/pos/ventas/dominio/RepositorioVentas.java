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
    long iniciar(long turnoId, long cajaId, long usuarioId, BigDecimal tasaIva);}
