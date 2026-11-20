package gt.supermercado.pos.impresion.infraestructura;

import gt.supermercado.pos.impresion.dominio.RepositorioReimpresiones;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioReimpresionesJdbc implements RepositorioReimpresiones {

    private final JdbcTemplate jdbc;

    RepositorioReimpresionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(long facturaId, long usuarioId, Long cajaId) {
        jdbc.update("INSERT INTO reimpresion (factura_id, usuario_id, caja_id) VALUES (?, ?, ?)", facturaId, usuarioId,
                cajaId);
    }
}
