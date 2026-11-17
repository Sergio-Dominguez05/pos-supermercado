package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.RepositorioConexionFel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
class RepositorioConexionFelJdbc implements RepositorioConexionFel {

    private final JdbcTemplate jdbc;

    RepositorioConexionFelJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConfiguracionFel> configuracion() {
        return jdbc.query("SELECT * FROM config_fel WHERE id = 1", (rs, fila) -> new ConfiguracionFel(
                rs.getString("certificador"), rs.getString("url_servicio"), rs.getString("usuario"),
                AmbienteFel.valueOf(rs.getString("ambiente")), rs.getInt("timeout_ms"),
                rs.getObject("modificado_en", OffsetDateTime.class), rs.getBytes("secreto_cifrado"),
                rs.getString("nit_certificador")))
                .stream().findFirst();
    }

    @Override
    public void guardar(String certificador, String nitCertificador, String urlServicio, String usuario,
                        byte[] secretoCifrado, AmbienteFel ambiente, int timeoutMs, long usuarioId) {
        jdbc.update("""
                INSERT INTO config_fel (id, certificador, nit_certificador, url_servicio, usuario, secreto_cifrado,
                                        ambiente, timeout_ms, modificado_por)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET certificador = EXCLUDED.certificador,
                    nit_certificador = EXCLUDED.nit_certificador,
                    url_servicio = EXCLUDED.url_servicio, usuario = EXCLUDED.usuario,
                    secreto_cifrado = EXCLUDED.secreto_cifrado, ambiente = EXCLUDED.ambiente,
                    timeout_ms = EXCLUDED.timeout_ms, modificado_por = EXCLUDED.modificado_por, modificado_en = now()""",
                certificador, nitCertificador, urlServicio, usuario, secretoCifrado, ambiente.name(), timeoutMs,
                usuarioId);
    }
}
