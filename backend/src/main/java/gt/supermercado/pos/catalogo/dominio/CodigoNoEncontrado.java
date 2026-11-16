package gt.supermercado.pos.catalogo.dominio;

import java.time.OffsetDateTime;

/**
 * Código que se buscó y no existe en el catálogo, pendiente de que un administrador registre el producto
 * (HU-03.08 crit. 4).
 *
 * @param veces      cuántas veces se buscó desde la última vez que se resolvió
 * @param ultimaVez  la búsqueda más reciente
 * @param ultimaCaja caja de la búsqueda más reciente, o {@code null} si se buscó desde un equipo sin caja
 */
public record CodigoNoEncontrado(String codigo, long veces, OffsetDateTime ultimaVez, String ultimaCaja) {
}
