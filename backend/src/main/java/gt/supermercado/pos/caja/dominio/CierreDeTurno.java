package gt.supermercado.pos.caja.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * HU-09.08 crit. 5: el cierre guardado: caja, usuario, apertura y cierre, total registrado, total del cierre de lote y
 * diferencia; HU-09.09: la justificación de la diferencia.
 */
public record CierreDeTurno(long turnoId, long cajaId, String cajaNombre, long usuarioId, String usuarioNombre,
                            OffsetDateTime abiertoEn, OffsetDateTime cerradoEn, String cerradoPor,
                            BigDecimal totalRegistrado, BigDecimal totalLote, BigDecimal diferencia,
                            String justificacion) {
}
