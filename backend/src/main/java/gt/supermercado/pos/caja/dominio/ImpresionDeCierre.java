package gt.supermercado.pos.caja.dominio;

/**
 * HU-09.10: imprime el comprobante del cierre en la impresora de la caja, con las copias de cierre configuradas
 * (HU-12.08), o una sola copia marcada si es una reimpresión. Lo implementa el módulo {@code impresion}. Nunca lanza:
 * si la impresora falla, el cierre ya está guardado y se puede reimprimir.
 */
public interface ImpresionDeCierre {

    /** Cómo terminó la impresión: {@code mensaje} dice por qué no se imprimió, si no se imprimió. */
    record Resultado(boolean impresa, String mensaje) {}

    Resultado imprimir(ComprobanteDeCierre comprobante, Long cajaId, boolean reimpresion);
}
