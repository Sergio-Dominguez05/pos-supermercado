package gt.supermercado.pos.configuracion.dominio;

/**
 * Caja registrada (HU-12.05). Nunca expone el token del equipo, solo si hay uno vinculado.
 *
 * @param serieFel serie de facturación de la caja, o {@code null} si no aplica
 */
public record Caja(long id, String nombre, String serieFel, boolean activo, boolean equipoVinculado) {
}
