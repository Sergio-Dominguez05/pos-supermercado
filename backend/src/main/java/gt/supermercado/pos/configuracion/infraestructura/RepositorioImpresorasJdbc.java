package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.configuracion.dominio.RepositorioImpresoras;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
class RepositorioImpresorasJdbc implements RepositorioImpresoras {

    private static final String SELECT_IMPRESORA = """
            SELECT i.id, i.nombre, i.host, i.puerto, i.ancho_mm, i.copias_factura, i.copias_cierre, i.modificado_en
            FROM impresora i
            """;

    private static final RowMapper<Impresora> IMPRESORA = (rs, fila) -> new Impresora(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("host"),
            rs.getInt("puerto"),
            rs.getInt("ancho_mm"),
            rs.getInt("copias_factura"),
            rs.getInt("copias_cierre"),
            rs.getObject("modificado_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    RepositorioImpresorasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Impresora> listar() {
        return jdbc.query(SELECT_IMPRESORA + "ORDER BY lower(i.nombre)", IMPRESORA);
    }

    @Override
    public Optional<Impresora> buscarPorId(long id) {
        return jdbc.query(SELECT_IMPRESORA + "WHERE i.id = ?", IMPRESORA, id).stream().findFirst();
    }

    @Override
    public Optional<Impresora> deLaCaja(long cajaId) {
        return jdbc.query(SELECT_IMPRESORA + "JOIN caja c ON c.impresora_id = i.id WHERE c.id = ?", IMPRESORA, cajaId)
                .stream().findFirst();
    }

    @Override
    public long crear(String nombre, String host, int puerto, int anchoMm, int copiasFactura, int copiasCierre,
                      long usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO impresora (nombre, host, puerto, ancho_mm, copias_factura, copias_cierre, modificado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id""", Long.class, nombre, host, puerto, anchoMm, copiasFactura, copiasCierre, usuarioId);
    }

    @Override
    public void actualizar(long id, String nombre, String host, int puerto, int anchoMm, int copiasFactura,
                           int copiasCierre, long usuarioId) {
        jdbc.update("""
                UPDATE impresora SET nombre = ?, host = ?, puerto = ?, ancho_mm = ?, copias_factura = ?,
                                     copias_cierre = ?, modificado_en = now(), modificado_por = ?
                WHERE id = ?""", nombre, host, puerto, anchoMm, copiasFactura, copiasCierre, usuarioId, id);
    }
}
