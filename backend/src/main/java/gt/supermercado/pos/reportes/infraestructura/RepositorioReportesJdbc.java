package gt.supermercado.pos.reportes.infraestructura;

import gt.supermercado.pos.reportes.dominio.FiltrosReporte;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro.Opcion;
import gt.supermercado.pos.reportes.dominio.RepositorioReportes;
import gt.supermercado.pos.reportes.dominio.VentasDelDia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consultas de solo lectura sobre las tablas de los demás módulos (Architecture.md §4: {@code reportes} lee de
 * todos). Las fechas de los filtros son días en hora de Guatemala.
 */
@Repository
class RepositorioReportesJdbc implements RepositorioReportes {

    private final JdbcTemplate jdbc;

    RepositorioReportesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VentasDelDia> ventasPorDia(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT (v.finalizada_en AT TIME ZONE 'America/Guatemala')::date AS fecha, count(*) AS cantidad,
                       sum(v.subtotal) AS subtotal, sum(v.iva) AS iva, sum(v.total) AS total
                FROM venta v
                LEFT JOIN factura f ON f.venta_id = v.id
                WHERE v.estado = 'FINALIZADA'
                  AND (f.estado IS NULL OR f.estado <> 'ANULADA')
                  AND v.finalizada_en >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala'
                  AND v.finalizada_en < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'
                  AND (?::bigint IS NULL OR v.caja_id = ?::bigint)
                  AND (?::bigint IS NULL OR v.usuario_id = ?::bigint)
                GROUP BY 1
                ORDER BY 1""",
                (fila, i) -> new VentasDelDia(fila.getObject("fecha", java.time.LocalDate.class),
                        fila.getLong("cantidad"), fila.getBigDecimal("subtotal"), fila.getBigDecimal("iva"),
                        fila.getBigDecimal("total")),
                filtros.desde(), filtros.hasta(), filtros.cajaId(), filtros.cajaId(), filtros.cajeroId(),
                filtros.cajeroId());
    }

    @Override
    public List<Opcion> cajas() {
        return jdbc.query("""
                SELECT id, nombre, activo FROM caja ORDER BY lower(nombre)""",
                (fila, i) -> new Opcion(String.valueOf(fila.getLong("id")),
                        fila.getString("nombre") + (fila.getBoolean("activo") ? "" : " (inactiva)")));
    }

    @Override
    public List<Opcion> cajeros() {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.username FROM usuario u
                WHERE EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = u.rol_id AND rp.permiso = 'VENTA_REGISTRAR')
                   OR EXISTS (SELECT 1 FROM venta v WHERE v.usuario_id = u.id)
                ORDER BY lower(u.nombre_completo), u.username""",
                (fila, i) -> new Opcion(String.valueOf(fila.getLong("id")),
                        fila.getString("nombre_completo") + " (" + fila.getString("username") + ")"));
    }

    @Override
    public Optional<String> nombreCaja(long cajaId) {
        return jdbc.query("SELECT nombre FROM caja WHERE id = ?", (fila, i) -> fila.getString("nombre"), cajaId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> nombreCajero(long usuarioId) {
        return jdbc.query("SELECT nombre_completo, username FROM usuario WHERE id = ?",
                (fila, i) -> fila.getString("nombre_completo") + " (" + fila.getString("username") + ")", usuarioId)
                .stream().findFirst();
    }
}
