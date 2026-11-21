package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.FormatoComprobante;
import gt.supermercado.pos.configuracion.dominio.LogoTicket;
import gt.supermercado.pos.configuracion.dominio.RepositorioFormatoComprobante;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
class RepositorioFormatoComprobanteJdbc implements RepositorioFormatoComprobante {

    private final JdbcTemplate jdbc;

    RepositorioFormatoComprobanteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FormatoComprobante> obtener() {
        return jdbc.query("""
                SELECT encabezado, pie, logo, logo_ancho, logo_alto, modificado_en FROM formato_comprobante WHERE id = 1""",
                (rs, fila) -> {
                    byte[] logo = rs.getBytes("logo");
                    return new FormatoComprobante(rs.getString("encabezado"), rs.getString("pie"),
                            logo == null ? null : new LogoTicket(rs.getInt("logo_ancho"), rs.getInt("logo_alto"), logo),
                            rs.getObject("modificado_en", OffsetDateTime.class));
                }).stream().findFirst();
    }

    @Override
    public void guardar(String encabezado, String pie, LogoTicket logo, long usuarioId) {
        jdbc.update("""
                INSERT INTO formato_comprobante (id, encabezado, pie, logo, logo_ancho, logo_alto, modificado_por)
                VALUES (1, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET encabezado = EXCLUDED.encabezado, pie = EXCLUDED.pie,
                    logo = EXCLUDED.logo, logo_ancho = EXCLUDED.logo_ancho, logo_alto = EXCLUDED.logo_alto,
                    modificado_por = EXCLUDED.modificado_por, modificado_en = now()""",
                encabezado, pie, logo == null ? null : logo.puntos(), logo == null ? null : logo.ancho(),
                logo == null ? null : logo.alto(), usuarioId);
    }
}
