package gt.supermercado.pos.pagos.infraestructura;

import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.RepositorioPagos;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
class RepositorioPagosJdbc implements RepositorioPagos {

    private static final RowMapper<Pago> PAGO = (rs, fila) -> new Pago(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getLong("turno_id"),
            rs.getLong("usuario_id"),
            TipoTarjeta.valueOf(rs.getString("tipo_tarjeta")),
            rs.getString("autorizacion"),
            rs.getString("ultimos4"),
            rs.getBigDecimal("monto"),
            rs.getObject("registrado_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    RepositorioPagosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long registrar(long ventaId, long turnoId, long usuarioId, TipoTarjeta tipo, String autorizacion,
                          String ultimos4, BigDecimal monto) {
        return jdbc.queryForObject("""
                INSERT INTO pago (venta_id, turno_id, usuario_id, tipo_tarjeta, autorizacion, ultimos4, monto)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                ventaId, turnoId, usuarioId, tipo.name(), autorizacion, ultimos4, monto);
    }

    @Override
    public List<Pago> deLaVenta(long ventaId) {
        return jdbc.query("SELECT * FROM pago WHERE venta_id = ? ORDER BY id", PAGO, ventaId);
    }

    @Override
    public boolean tienePagos(long ventaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pago WHERE venta_id = ?)", Boolean.class, ventaId));
    }
}
