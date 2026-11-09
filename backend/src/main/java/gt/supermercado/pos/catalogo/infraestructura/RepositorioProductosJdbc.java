package gt.supermercado.pos.catalogo.infraestructura;

import gt.supermercado.pos.catalogo.dominio.FiltroProductos;
import gt.supermercado.pos.catalogo.dominio.PaginaProductos;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class RepositorioProductosJdbc implements RepositorioProductos {

    private static final String COLUMNAS = "id, codigo_barras, descripcion, precio, activo, modificado_en";
    private static final String SELECT_PRODUCTO = "SELECT " + COLUMNAS + " FROM producto ";

    private static final RowMapper<Producto> PRODUCTO = (rs, fila) -> new Producto(
            rs.getLong("id"),
            rs.getString("codigo_barras"),
            rs.getString("descripcion"),
            rs.getBigDecimal("precio"),
            rs.getBoolean("activo"),
            rs.getObject("modificado_en", OffsetDateTime.class));

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

    /**
     * HU-03.04. La descripción se compara sin tildes ni mayúsculas con el índice trigram {@code producto_desc_trgm};
     * el código, sin mayúsculas. El total sale en la misma consulta ({@code count(*) OVER ()}). El orden lo elige el
     * servidor de una lista cerrada, nunca se arma con texto del usuario, y el id lo desempata para que la
     * paginación sea estable.
     */
    @Override
    public PaginaProductos buscar(FiltroProductos filtro) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNAS)
                .append(", count(*) OVER () AS total FROM producto WHERE true");
        List<Object> argumentos = new ArrayList<>();
        if (filtro.texto() != null) {
            String patron = escaparLike(filtro.texto());
            sql.append(" AND (f_unaccent(lower(descripcion)) LIKE '%' || f_unaccent(lower(?)) || '%'")
                    .append(" OR codigo_barras LIKE '%' || upper(?) || '%')");
            argumentos.add(patron);
            argumentos.add(patron);
        }
        if (filtro.activo() != null) {
            sql.append(" AND activo = ?");
            argumentos.add(filtro.activo());
        }
        String direccion = filtro.descendente() ? " DESC" : " ASC";
        String columna = switch (filtro.orden()) {
            case DESCRIPCION -> "lower(descripcion)";
            case PRECIO -> "precio";
            case MODIFICADO -> "modificado_en";
        };
        sql.append(" ORDER BY ").append(columna).append(direccion).append(", id").append(direccion)
                .append(" LIMIT ? OFFSET ?");
        argumentos.add(filtro.tamano());
        argumentos.add((long) filtro.pagina() * filtro.tamano());

        long[] total = {0};
        List<Producto> productos = jdbc.query(sql.toString(), (rs, fila) -> {
            total[0] = rs.getLong("total");
            return PRODUCTO.mapRow(rs, fila);
        }, argumentos.toArray());
        if (productos.isEmpty() && filtro.pagina() > 0) {
            // Página fuera de rango: el total se consulta aparte para que la pantalla pueda volver a una válida.
            total[0] = contar(filtro);
        }
        return new PaginaProductos(productos, filtro.pagina(), filtro.tamano(), total[0]);
    }

    private long contar(FiltroProductos filtro) {
        return buscar(new FiltroProductos(filtro.texto(), filtro.activo(), filtro.orden(), filtro.descendente(), 0,
                filtro.tamano())).total();
    }

    @Override
    public List<Producto> buscarActivosPorDescripcion(String texto, int limite) {
        String patron = escaparLike(texto);
        // El LIKE usa el índice trigram producto_desc_trgm; el CASE pone primero lo que empieza con el texto.
        return jdbc.query(SELECT_PRODUCTO + """
                WHERE activo AND f_unaccent(lower(descripcion)) LIKE '%' || f_unaccent(lower(?)) || '%'
                ORDER BY CASE WHEN f_unaccent(lower(descripcion)) LIKE f_unaccent(lower(?)) || '%' THEN 0 ELSE 1 END,
                         lower(descripcion), id
                LIMIT ?""", PRODUCTO, patron, patron, limite);
    }

    @Override
    public long crear(String codigoBarras, String descripcion, BigDecimal precio) {
        return jdbc.queryForObject(
                "INSERT INTO producto (codigo_barras, descripcion, precio) VALUES (?, ?, ?) RETURNING id",
                Long.class, codigoBarras, descripcion, precio);
    }

    @Override
    public void actualizar(long id, String descripcion, BigDecimal precio) {
        jdbc.update("UPDATE producto SET descripcion = ?, precio = ?, modificado_en = now() WHERE id = ?",
                descripcion, precio, id);
    }

    @Override
    public void cambiarActivo(long id, boolean activo) {
        jdbc.update("UPDATE producto SET activo = ?, modificado_en = now() WHERE id = ?", activo, id);
    }

    /** Lo que escribe el usuario se busca literal: '%' y '_' no son comodines. */
    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
