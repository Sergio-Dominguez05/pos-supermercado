package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.Cliente;
import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class RepositorioClientesJdbc implements RepositorioClientes {

    private static final String SELECT_CLIENTE = """
            SELECT c.id, c.identificacion, c.nombre, c.direccion, c.correo
            FROM cliente c
            """;

    private static final RowMapper<Cliente> CLIENTE = (rs, fila) -> new Cliente(
            rs.getLong("id"),
            rs.getString("identificacion"),
            rs.getString("nombre"),
            rs.getString("direccion"),
            rs.getString("correo"));

    private final JdbcTemplate jdbc;

    RepositorioClientesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cliente> porNit(String nit) {
        return jdbc.query(SELECT_CLIENTE + "WHERE c.tipo_id = 'NIT' AND c.identificacion = ?", CLIENTE, nit).stream()
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
    public long registrar(String nit, String nombre, String direccion, String correo, long usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO cliente (tipo_id, identificacion, nombre, direccion, correo, registrado_por)
                VALUES ('NIT', ?, ?, ?, ?, ?)
                RETURNING id""", Long.class, nit, nombre, direccion, correo, usuarioId);
    }
}
