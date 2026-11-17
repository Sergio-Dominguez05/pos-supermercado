package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-12.02: el establecimiento de una sucursal tal como está inscrito en la SAT. {@code codigoSat} es el número de
 * establecimiento que asigna la SAT (1, 2, 3...).
 */
public record Establecimiento(long sucursalId, String codigoSat, String nombre, String direccion,
                              OffsetDateTime modificadoEn) {
}
