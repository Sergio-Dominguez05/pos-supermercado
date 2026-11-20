package gt.supermercado.pos.ventas.infraestructura;

import gt.supermercado.pos.caja.dominio.VentasDelTurno;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** HU-09.08 crit. 1: la venta en curso del turno, para el cierre de caja. */
@Component
class VentasDelTurnoJdbc implements VentasDelTurno {

    private final JdbcTemplate jdbc;

    VentasDelTurnoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tieneVentaEnCursoConProductos(long turnoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM venta v
                               WHERE v.turno_id = ? AND v.estado = 'EN_CURSO'
                                 AND EXISTS (SELECT 1 FROM venta_linea l
                                             WHERE l.venta_id = v.id AND l.eliminada_en IS NULL))""",
                Boolean.class, turnoId));
    }

    @Override
    public void cancelarVentaVaciaEnCurso(long turnoId) {
        jdbc.update("""
                UPDATE venta v SET estado = 'CANCELADA'
                WHERE v.turno_id = ? AND v.estado = 'EN_CURSO'
                  AND NOT EXISTS (SELECT 1 FROM venta_linea l WHERE l.venta_id = v.id AND l.eliminada_en IS NULL)""",
                turnoId);
    }
}
