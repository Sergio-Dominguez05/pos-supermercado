package gt.supermercado.pos.catalogo.infraestructura;

import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
class RepositorioProductosJdbc implements RepositorioProductos {

    private static final String SELECT_PRODUCTO = "SELECT id, codigo_barras, descripcion, precio, activo FROM producto ";

    private static final RowMapper<Producto> PRODUCTO = (rs, fila) -> new Producto(
            rs.getLong("id"),
            rs.getString("codigo_barras"),
            rs.getString("descripcion"),
            rs.getBigDecimal("precio"),
            rs.getBoolean("activo"));

    private final JdbcTemplate jdbc;

    RepositorioProductosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Producto> buscarPorId(long id) {
        return jdbc.query(SELECT_PRODUCTO + "WHERE id = ?", PRODUCTO, id).stream().findFirst();
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigoBarras) {
        return jdbc.query(SELECT_PRODUCTO + "WHERE codigo_barras = ?", PRODUCTO, codigoBarras).stream().findFirst();
    }

    @Override
    public long crear(String codigoBarras, String descripcion, BigDecimal precio) {
        return jdbc.queryForObject(
                "INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, ?, ?) RETURNING id",
                Long.class, codigoBarras, descripcion, precio);
    }
}
