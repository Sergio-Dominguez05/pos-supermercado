package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.CambioRegistrado;
import gt.supermercado.pos.bitacora.dominio.LecturaBitacora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
class LecturaBitacoraJdbc implements LecturaBitacora {

    private final JdbcTemplate jdbc;

    LecturaBitacoraJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo) {
        return jdbc.query("""
                SELECT b.ocurrido_en, b.cambios -> ? ->> 'antes' AS antes, b.cambios -> ? ->> 'despues' AS despues,
                       u.nombre_completo || ' (' || u.username || ')' AS usuario
                FROM bitacora b LEFT JOIN usuario u ON u.id = b.usuario_id
                WHERE b.accion = ? AND b.exitoso AND b.entidad = ? AND b.entidad_id = ? AND b.cambios -> ? IS NOT NULL
                ORDER BY b.ocurrido_en DESC, b.secuencia DESC""",
                (rs, fila) -> new CambioRegistrado(rs.getObject("ocurrido_en", OffsetDateTime.class),
                        rs.getString("antes"), rs.getString("despues"), rs.getString("usuario")),
                campo, campo, accion.name(), entidad, entidadId, campo);
    }
}
