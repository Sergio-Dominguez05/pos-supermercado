package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.ConfiguracionCorreo;
import gt.supermercado.pos.facturacion.dominio.RepositorioConfiguracionCorreo;
import gt.supermercado.pos.facturacion.dominio.SeguridadCorreo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** HU-08.15: la fila única de {@code config_correo}. */
@Repository
class RepositorioConfiguracionCorreoJdbc implements RepositorioConfiguracionCorreo {

    private final JdbcTemplate jdbc;

    RepositorioConfiguracionCorreoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConfiguracionCorreo> configuracion() {
        return jdbc.query("SELECT * FROM config_correo WHERE id = 1", (rs, fila) -> new ConfiguracionCorreo(
                rs.getString("servidor"), rs.getInt("puerto"), SeguridadCorreo.valueOf(rs.getString("seguridad")),
                rs.getString("usuario"), rs.getString("remitente"), rs.getString("nombre_remitente"),
                rs.getInt("timeout_ms"), rs.getObject("modificado_en", OffsetDateTime.class),
                rs.getBytes("secreto_cifrado"))).stream().findFirst();
    }

    @Override
    public void guardar(String servidor, int puerto, SeguridadCorreo seguridad, String usuario, byte[] secretoCifrado,
                        String remitente, String nombreRemitente, int timeoutMs, long usuarioId) {
        jdbc.update("""
                INSERT INTO config_correo (id, servidor, puerto, seguridad, usuario, secreto_cifrado, remitente,
                                           nombre_remitente, timeout_ms, modificado_por)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET servidor = EXCLUDED.servidor, puerto = EXCLUDED.puerto,
                    seguridad = EXCLUDED.seguridad, usuario = EXCLUDED.usuario,
                    secreto_cifrado = EXCLUDED.secreto_cifrado, remitente = EXCLUDED.remitente,
                    nombre_remitente = EXCLUDED.nombre_remitente, timeout_ms = EXCLUDED.timeout_ms,
                    modificado_por = EXCLUDED.modificado_por, modificado_en = now()""",
                servidor, puerto, seguridad.name(), usuario, secretoCifrado, remitente, nombreRemitente, timeoutMs,
                usuarioId);
    }
}
