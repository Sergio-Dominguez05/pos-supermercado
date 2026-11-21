package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.ArchivoBitacora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Usa la tabla bitacora_conservacion y la función archivar_bitacora() de la migración V37. */
@Repository
class ArchivoBitacoraJdbc implements ArchivoBitacora {

    private final JdbcTemplate jdbc;

    ArchivoBitacoraJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Estado estado() {
        return jdbc.queryForObject("""
                SELECT (SELECT plazo_anios FROM bitacora_conservacion WHERE id = 1) AS plazo,
                       (SELECT count(*) FROM bitacora) AS activos,
                       (SELECT count(*) FROM bitacora_archivo) AS archivados,
                       (SELECT min(ocurrido_en) FROM bitacora_completa) AS mas_antiguo,
                       (SELECT max(ocurrido_en) FROM bitacora_archivo) AS archivado_hasta""",
                (rs, fila) -> new Estado(rs.getInt("plazo"), rs.getLong("activos"), rs.getLong("archivados"),
                        rs.getObject("mas_antiguo", OffsetDateTime.class),
                        rs.getObject("archivado_hasta", OffsetDateTime.class)));
    }

    @Override
    public void cambiarPlazo(int anios) {
        jdbc.update("UPDATE bitacora_conservacion SET plazo_anios = ?, modificado_en = now() WHERE id = 1", anios);
    }

    @Override
    public long archivar(LocalDate hasta) {
        return jdbc.queryForObject(
                "SELECT archivar_bitacora((?::date)::timestamp AT TIME ZONE 'America/Guatemala')", Long.class, hasta);
    }
}
