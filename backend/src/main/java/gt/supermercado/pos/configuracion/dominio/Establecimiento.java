package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-12.02: el establecimiento de una sucursal tal como está inscrito en la SAT. {@code codigoSat} es el número de
 * establecimiento que asigna la SAT (1, 2, 3...). HU-08.02: el DTE usa su dirección desglosada como dirección del
 * emisor; puede faltar en datos guardados antes de esa historia.
 */
public record Establecimiento(long sucursalId, String codigoSat, String nombre, String direccion, String codigoPostal,
                              String municipio, String departamento, OffsetDateTime modificadoEn) {
}
