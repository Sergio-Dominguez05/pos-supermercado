package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.Caja;
import gt.supermercado.pos.configuracion.dominio.RepositorioCajas;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class RepositorioCajasJdbc implements RepositorioCajas {

    // Nunca se selecciona el hash del token: solo si hay un equipo vinculado.
    private static final String SELECT_CAJA = """
            SELECT id, nombre, serie_fel, activo, token_equipo_hash IS NOT NULL AS equipo_vinculado
            FROM caja
            """;

    private static final RowMapper<Caja> CAJA = (rs, fila) -> new Caja(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("serie_fel"),
            rs.getBoolean("activo"),
            rs.getBoolean("equipo_vinculado"));

    /** V1 tiene una sola sucursal (HU-13 queda fuera); todas las cajas son de la Matriz. */
    private static final String SUCURSAL_MATRIZ = "(SELECT id FROM sucursal WHERE codigo = 'MATRIZ')";

    private final JdbcTemplate jdbc;

    RepositorioCajasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Caja> listar() {
        return jdbc.query(SELECT_CAJA + "ORDER BY lower(nombre)", CAJA);
    }

    @Override
    public Optional<Caja> buscarPorId(long id) {
        return jdbc.query(SELECT_CAJA + "WHERE id = ?", CAJA, id).stream().findFirst();
    }

    @Override
    public Optional<Caja> buscarPorEquipo(byte[] tokenHash) {
        return jdbc.query(SELECT_CAJA + "WHERE token_equipo_hash = ?", CAJA, (Object) tokenHash).stream().findFirst();
    }

    @Override
    public boolean existeNombre(String nombre, Long excluirId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM caja
                                       WHERE sucursal_id = %s AND caja_clave_nombre(nombre) = caja_clave_nombre(?)
                                         AND id IS DISTINCT FROM ?)"""
                        .formatted(SUCURSAL_MATRIZ),
                Boolean.class, nombre, excluirId));
    }

    @Override
    public long crear(String nombre, String serieFel) {
        return jdbc.queryForObject("""
                        INSERT INTO caja (sucursal_id, nombre, serie_fel) VALUES (%s, ?, ?) RETURNING id"""
                        .formatted(SUCURSAL_MATRIZ),
                Long.class, nombre, serieFel);
    }

    @Override
    public void actualizar(long id, String nombre, String serieFel) {
        jdbc.update("UPDATE caja SET nombre = ?, serie_fel = ? WHERE id = ?", nombre, serieFel, id);
    }

    @Override
    public void cambiarActivo(long id, boolean activo) {
        jdbc.update("UPDATE caja SET activo = ? WHERE id = ?", activo, id);
    }

    @Override
    public void vincularEquipo(long id, byte[] tokenHash) {
        jdbc.update("UPDATE caja SET token_equipo_hash = ? WHERE id = ?", tokenHash, id);
    }
}
