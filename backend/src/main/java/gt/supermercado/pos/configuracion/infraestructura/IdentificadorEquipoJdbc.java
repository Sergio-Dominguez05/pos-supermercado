package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.seguridad.dominio.IdentificadorEquipo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Caja activa vinculada al equipo, para asociarla a la sesión al iniciarla (HU-12.05 crit. 4). */
@Component
class IdentificadorEquipoJdbc implements IdentificadorEquipo {

    private final JdbcTemplate jdbc;

    IdentificadorEquipoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CajaDelEquipo> cajaActiva(byte[] tokenHash) {
        return jdbc.query("SELECT id, nombre FROM caja WHERE token_equipo_hash = ? AND activo",
                (rs, fila) -> new CajaDelEquipo(rs.getLong("id"), rs.getString("nombre")),
                (Object) tokenHash).stream().findFirst();
    }
}
