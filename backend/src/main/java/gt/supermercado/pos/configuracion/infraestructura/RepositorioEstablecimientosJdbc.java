package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.configuracion.dominio.RepositorioEstablecimientos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
class RepositorioEstablecimientosJdbc implements RepositorioEstablecimientos {

    private final JdbcTemplate jdbc;

    RepositorioEstablecimientosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long sucursalPrincipal() {
        return jdbc.queryForObject("SELECT id FROM sucursal WHERE codigo = 'MATRIZ'", Long.class);
    }

    @Override
    public Optional<Establecimiento> deLaSucursal(long sucursalId) {
        return jdbc.query("SELECT * FROM establecimiento WHERE sucursal_id = ?", (rs, fila) -> new Establecimiento(
                rs.getLong("sucursal_id"), rs.getString("codigo_sat"), rs.getString("nombre"),
                rs.getString("direccion"), rs.getObject("modificado_en", OffsetDateTime.class)), sucursalId)
                .stream().findFirst();
    }

    @Override
    public void guardar(long sucursalId, String codigoSat, String nombre, String direccion, long usuarioId) {
        jdbc.update("""
                INSERT INTO establecimiento (sucursal_id, codigo_sat, nombre, direccion, modificado_por)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (sucursal_id) DO UPDATE SET codigo_sat = EXCLUDED.codigo_sat, nombre = EXCLUDED.nombre,
                    direccion = EXCLUDED.direccion, modificado_por = EXCLUDED.modificado_por, modificado_en = now()""",
                sucursalId, codigoSat, nombre, direccion, usuarioId);
    }
}
