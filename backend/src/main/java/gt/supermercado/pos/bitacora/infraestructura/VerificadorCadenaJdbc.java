package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.ResultadoVerificacion;
import gt.supermercado.pos.bitacora.dominio.ResultadoVerificacion.Problema;
import gt.supermercado.pos.bitacora.dominio.VerificadorCadena;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Usa verificar_bitacora() de la migración V8, que recalcula cada huella en la base de datos. */
@Repository
class VerificadorCadenaJdbc implements VerificadorCadena {

    private final JdbcTemplate jdbc;

    VerificadorCadenaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ResultadoVerificacion verificar() {
        List<Problema> problemas = jdbc.query("SELECT secuencia, problema FROM verificar_bitacora() ORDER BY secuencia",
                (rs, fila) -> new Problema(rs.getLong("secuencia"), rs.getString("problema")));
        long registros = jdbc.queryForObject("SELECT count(*) FROM bitacora", Long.class);
        return new ResultadoVerificacion(registros, problemas);
    }
}
