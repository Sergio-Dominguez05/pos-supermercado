package gt.supermercado.pos.catalogo.infraestructura;

import gt.supermercado.pos.catalogo.dominio.CodigoNoEncontrado;
import gt.supermercado.pos.catalogo.dominio.RepositorioCodigosNoEncontrados;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
class RepositorioCodigosNoEncontradosJdbc implements RepositorioCodigosNoEncontrados {

    private final JdbcTemplate jdbc;

    RepositorioCodigosNoEncontradosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(String codigo, Long cajaId, long usuarioId) {
        jdbc.update("INSERT INTO codigo_no_encontrado (codigo, caja_id, usuario_id) VALUES (?, ?, ?)",
                codigo, cajaId, usuarioId);
    }

    @Override
    public List<CodigoNoEncontrado> pendientes() {
        return jdbc.query("""
                SELECT DISTINCT ON (n.codigo) n.codigo, count(*) OVER (PARTITION BY n.codigo) AS veces,
                       n.ocurrido_en AS ultima_vez, c.nombre AS ultima_caja
                FROM codigo_no_encontrado n LEFT JOIN caja c ON c.id = n.caja_id
                WHERE NOT n.resuelto
                ORDER BY n.codigo, n.ocurrido_en DESC, n.id DESC""",
                (rs, fila) -> new CodigoNoEncontrado(
                        rs.getString("codigo"),
                        rs.getLong("veces"),
                        rs.getObject("ultima_vez", OffsetDateTime.class),
                        rs.getString("ultima_caja")))
                .stream()
                .sorted((a, b) -> b.ultimaVez().compareTo(a.ultimaVez()))
                .toList();
    }

    @Override
    public void resolver(String codigo) {
        jdbc.update("UPDATE codigo_no_encontrado SET resuelto = true WHERE codigo = ? AND NOT resuelto", codigo);
    }
}
