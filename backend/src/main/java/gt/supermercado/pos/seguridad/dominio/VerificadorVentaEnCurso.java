package gt.supermercado.pos.seguridad.dominio;

/**
 * Indica si un usuario tiene una venta en curso, para pedirle que la finalice, suspenda o cancele antes de cerrar
 * sesión (HU-01.16 crit. 2). Lo implementará el módulo {@code ventas} cuando existan las ventas (HU-06.01),
 * que depende de {@code seguridad} y no al revés (Architecture.md §4).
 */
public interface VerificadorVentaEnCurso {

    boolean tieneVentaEnCurso(long usuarioId);
}
