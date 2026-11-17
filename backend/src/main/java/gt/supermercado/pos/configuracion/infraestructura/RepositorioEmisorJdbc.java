package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.RepositorioEmisor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
class RepositorioEmisorJdbc implements RepositorioEmisor {

    private final JdbcTemplate jdbc;

    RepositorioEmisorJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Emisor> emisor() {
        return jdbc.query("SELECT * FROM emisor WHERE id = 1", (rs, fila) -> new Emisor(rs.getString("nit"),
                rs.getString("razon_social"), rs.getString("nombre_comercial"), rs.getString("direccion_fiscal"),
                rs.getString("telefono"), rs.getString("correo"),
                rs.getString("afiliacion_iva") == null ? null : AfiliacionIva.valueOf(rs.getString("afiliacion_iva")),
                rs.getObject("modificado_en", OffsetDateTime.class)))
                .stream().findFirst();
    }

    @Override
    public void guardar(Emisor emisor, long usuarioId) {
        jdbc.update("""
                INSERT INTO emisor (id, nit, razon_social, nombre_comercial, direccion_fiscal, telefono, correo,
                                    afiliacion_iva, modificado_por)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET nit = EXCLUDED.nit, razon_social = EXCLUDED.razon_social,
                    nombre_comercial = EXCLUDED.nombre_comercial, direccion_fiscal = EXCLUDED.direccion_fiscal,
                    telefono = EXCLUDED.telefono, correo = EXCLUDED.correo, afiliacion_iva = EXCLUDED.afiliacion_iva,
                    modificado_por = EXCLUDED.modificado_por,
                    modificado_en = now()""",
                emisor.nit(), emisor.razonSocial(), emisor.nombreComercial(), emisor.direccionFiscal(),
                emisor.telefono(), emisor.correo(), emisor.afiliacionIva().name(), usuarioId);
    }
}
