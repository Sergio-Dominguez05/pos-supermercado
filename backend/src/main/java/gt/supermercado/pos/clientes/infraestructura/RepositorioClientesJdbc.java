package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.Cliente;
import gt.supermercado.pos.clientes.dominio.FiltroClientes;
import gt.supermercado.pos.clientes.dominio.PaginaClientes;
import gt.supermercado.pos.clientes.dominio.PaginaClientes.ClienteListado;
import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
class RepositorioClientesJdbc implements RepositorioClientes {

    private static final String SELECT_CLIENTE = """
            SELECT c.id, c.identificacion, c.nombre, c.direccion, c.correo, c.tipo_id
            FROM cliente c
            """;

    private static final RowMapper<Cliente> CLIENTE = (rs, fila) -> new Cliente(
            rs.getLong("id"),
            rs.getString("identificacion"),
            rs.getString("nombre"),
            rs.getString("direccion"),
            rs.getString("correo"),
            rs.getString("tipo_id"));

    private final JdbcTemplate jdbc;

    RepositorioClientesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaClientes listar(FiltroClientes filtro) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.tipo_id, c.identificacion, c.nombre, c.direccion, c.correo,
                       (SELECT max(v.finalizada_en) FROM venta v
                        WHERE v.cliente_id = c.id AND v.estado = 'FINALIZADA') AS ultima_compra,
                       count(*) OVER () AS total
                FROM cliente c
                WHERE true""");
        sql.append(' ');
        List<Object> argumentos = new ArrayList<>();
        if (filtro.texto() != null) {
            String nombre = escaparLike(filtro.texto());
            String identificacion = escaparLike(filtro.texto().replace("-", "").replace(" ", "")
                    .toUpperCase(Locale.ROOT));
            sql.append("""
                     AND (f_unaccent(lower(c.nombre)) LIKE '%' || f_unaccent(lower(?)) || '%'
                          OR c.identificacion LIKE '%' || ? || '%')""");
            argumentos.add(nombre);
            argumentos.add(identificacion);
        }
        sql.append(" ORDER BY f_unaccent(lower(c.nombre)), c.id LIMIT ? OFFSET ?");
        argumentos.add(filtro.tamano());
        argumentos.add((long) filtro.pagina() * filtro.tamano());

        long[] total = {0};
        List<ClienteListado> clientes = jdbc.query(sql.toString(), (rs, fila) -> {
            total[0] = rs.getLong("total");
            return new ClienteListado(rs.getLong("id"), rs.getString("tipo_id"), rs.getString("identificacion"),
                    rs.getString("nombre"), rs.getString("direccion"), rs.getString("correo"),
                    rs.getObject("ultima_compra", OffsetDateTime.class));
        }, argumentos.toArray());
        if (clientes.isEmpty() && filtro.pagina() > 0) {
            total[0] = listar(new FiltroClientes(filtro.texto(), 0, filtro.tamano())).total();
        }
        return new PaginaClientes(clientes, filtro.pagina(), filtro.tamano(), total[0]);
    }

    @Override
    public Optional<Cliente> porNit(String nit) {
        return jdbc.query(SELECT_CLIENTE + "WHERE c.tipo_id = 'NIT' AND c.identificacion = ?", CLIENTE, nit).stream()
                .findFirst();
    }

    @Override
    public Optional<Cliente> porCui(String cui) {
        return jdbc.query(SELECT_CLIENTE + "WHERE c.tipo_id = 'CUI' AND c.identificacion = ?", CLIENTE, cui).stream()
                .findFirst();
    }

    @Override
    public Optional<Cliente> porId(long id) {
        return jdbc.query(SELECT_CLIENTE + "WHERE c.id = ?", CLIENTE, id).stream().findFirst();
    }

    @Override
    public Optional<Cliente> deLaVenta(long ventaId) {
        return jdbc.query(SELECT_CLIENTE + "JOIN venta v ON v.cliente_id = c.id WHERE v.id = ?", CLIENTE, ventaId)
                .stream().findFirst();
    }

    @Override
    public void cambiarCorreo(long clienteId, String correo) {
        jdbc.update("UPDATE cliente SET correo = ? WHERE id = ?", correo, clienteId);
    }

    @Override
    public long registrar(String tipo, String identificacion, String nombre, String direccion, String correo,
                          long usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO cliente (tipo_id, identificacion, nombre, direccion, correo, registrado_por)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id""", Long.class, tipo, identificacion, nombre, direccion, correo, usuarioId);
    }

    /** Lo escrito se busca literalmente: '%' y '_' no actúan como comodines. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
