package gt.supermercado.pos.configuracion.dominio;

/**
 * Caja registrada (HU-12.05). Nunca expone el token del equipo, solo si hay uno vinculado.
 *
 * @param serieFel        serie de facturación de la caja, o {@code null} si no aplica
 * @param impresoraId     la impresora de red de la caja (HU-12.08), o {@code null} si todavía no tiene
 * @param impresoraNombre el nombre de esa impresora, para mostrarlo en la lista de cajas
 */
public record Caja(long id, String nombre, String serieFel, boolean activo, boolean equipoVinculado, Long impresoraId,
                   String impresoraNombre) {
}
