package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Override
    public Optional<Boolean> booleano(String clave) {
        return jdbc.query("""
                SELECT (valor #>> '{}')::boolean FROM parametro
                WHERE clave = ? AND jsonb_typeof(valor) = 'boolean'""",
                (rs, fila) -> rs.getBoolean(1), clave).stream().findFirst();
    }

    @Override
    public Optional<BigDecimal> decimal(String clave) {
        return jdbc.query("""
                SELECT (valor #>> '{}')::numeric FROM parametro
                WHERE clave = ? AND jsonb_typeof(valor) = 'number'""",
                (rs, fila) -> rs.getBigDecimal(1), clave).stream().findFirst();
    }

    @Override
    public void guardarDecimal(String clave, BigDecimal valor) {
        jdbc.update("""
                INSERT INTO parametro (clave, valor, modificado_en)
                VALUES (?, to_jsonb(CAST(? AS numeric)), now())
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, modificado_en = EXCLUDED.modificado_en""",
                clave, valor);
    }

    @Override
    public void guardarEntero(String clave, int valor) {
        jdbc.update("""
                INSERT INTO parametro (clave, valor, modificado_en)
                VALUES (?, to_jsonb(CAST(? AS integer)), now())
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, modificado_en = EXCLUDED.modificado_en""",
                clave, valor);
    }

    @Override
    public void guardarBooleano(String clave, boolean valor) {
        jdbc.update("""
                INSERT INTO parametro (clave, valor, modificado_en)
                VALUES (?, to_jsonb(CAST(? AS boolean)), now())
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, modificado_en = EXCLUDED.modificado_en""",
                clave, valor);
    }
}
