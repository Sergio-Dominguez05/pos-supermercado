package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class RepositorioParametrosJdbc implements RepositorioParametros {

    private final JdbcTemplate jdbc;

    RepositorioParametrosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> entero(String clave) {
        return jdbc.query("""
                SELECT (valor #>> '{}')::int FROM parametro
                WHERE clave = ? AND jsonb_typeof(valor) = 'number' AND (valor #>> '{}') ~ '^[0-9]+$'""",
                (rs, fila) -> rs.getInt(1), clave).stream().findFirst();
    }
}
