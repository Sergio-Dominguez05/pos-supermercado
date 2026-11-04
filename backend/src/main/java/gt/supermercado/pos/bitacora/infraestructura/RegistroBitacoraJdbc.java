package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroBitacora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RegistroBitacoraJdbc implements RegistroBitacora {

    private final JdbcTemplate jdbc;

    RegistroBitacoraJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void agregar(EventoBitacora e) {
        jdbc.update("""
                INSERT INTO bitacora (usuario_id, rol, modulo, accion, exitoso, descripcion, entidad, entidad_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                e.usuarioId(), e.rol(), e.modulo(), e.accion(), e.exitoso(), e.descripcion(), e.entidad(), e.entidadId());
    }
}
