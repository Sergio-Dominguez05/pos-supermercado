package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroBitacora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class RegistroBitacoraJdbc implements RegistroBitacora {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    RegistroBitacoraJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void agregar(EventoBitacora e) {
        String cambios = e.cambios() == null ? null : json.writeValueAsString(e.cambios());
        jdbc.update("""
                INSERT INTO bitacora (usuario_id, rol, caja_id, modulo, accion, exitoso, descripcion, entidad, entidad_id,
                                      cambios)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                e.usuarioId(), e.rol(), e.cajaId(), e.modulo(), e.accion(), e.exitoso(), e.descripcion(), e.entidad(),
                e.entidadId(), cambios);
    }
}
