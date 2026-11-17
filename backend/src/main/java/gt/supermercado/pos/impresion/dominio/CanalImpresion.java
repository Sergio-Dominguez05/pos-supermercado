package gt.supermercado.pos.impresion.dominio;

/**
 * Puerto hacia la impresora térmica de la red (Architecture.md §3: ESC/POS por TCP). Hay un adaptador por red y uno
 * simulado para desarrollo y pruebas. Lanza {@link ImpresoraNoDisponibleException} si la impresora no responde.
 */
public interface CanalImpresion {

    void enviar(String host, int puerto, byte[] datos);
}
