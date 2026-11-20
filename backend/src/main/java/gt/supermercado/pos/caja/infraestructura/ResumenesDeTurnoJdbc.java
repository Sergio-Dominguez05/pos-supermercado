package gt.supermercado.pos.caja.infraestructura;

import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.caja.dominio.ResumenesDeTurno;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lo cobrado en el turno, con una sola consulta sobre las ventas finalizadas, sus pagos vigentes y sus facturas. Las
 * facturas anuladas (HU-08.10) se descuentan de lo registrado y se cuentan aparte.
 */
@Repository
class ResumenesDeTurnoJdbc implements ResumenesDeTurno {

    private final JdbcTemplate jdbc;

    ResumenesDeTurnoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ResumenDeTurno de(long turnoId) {
        return jdbc.queryForObject("""
                WITH ventas AS (
                    SELECT v.id, coalesce(f.estado, 'SIN_FACTURA') AS estado_factura
                    FROM venta v LEFT JOIN factura f ON f.venta_id = v.id
                    WHERE v.turno_id = ? AND v.estado = 'FINALIZADA'),
                pagos AS (
                    SELECT ve.estado_factura, p.tipo_tarjeta, p.monto
                    FROM ventas ve JOIN pago p ON p.venta_id = ve.id AND p.eliminado_en IS NULL)
                SELECT (SELECT count(*) FROM ventas) AS ventas,
                       coalesce(sum(monto) FILTER (WHERE tipo_tarjeta = 'DEBITO' AND estado_factura <> 'ANULADA'), 0)
                           AS debito,
                       coalesce(sum(monto) FILTER (WHERE tipo_tarjeta = 'CREDITO' AND estado_factura <> 'ANULADA'), 0)
                           AS credito,
                       (SELECT count(*) FROM ventas WHERE estado_factura = 'ANULADA') AS anuladas,
                       coalesce(sum(monto) FILTER (WHERE estado_factura = 'ANULADA'), 0) AS total_anulado,
                       coalesce(sum(monto) FILTER (WHERE estado_factura <> 'ANULADA'), 0) AS total_registrado,
                       (SELECT count(*) FROM ventas WHERE estado_factura IN ('PENDIENTE', 'CONTINGENCIA'))
                           AS contingencias
                FROM pagos""", (rs, fila) -> new ResumenDeTurno(
                rs.getInt("ventas"),
                rs.getBigDecimal("debito"),
                rs.getBigDecimal("credito"),
                rs.getInt("anuladas"),
                rs.getBigDecimal("total_anulado"),
                rs.getBigDecimal("total_registrado"),
                rs.getInt("contingencias")), turnoId);
    }
}
