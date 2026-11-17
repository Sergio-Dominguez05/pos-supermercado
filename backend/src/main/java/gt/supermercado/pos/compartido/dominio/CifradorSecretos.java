package gt.supermercado.pos.compartido.dominio;

/**
 * HU-12.15 crit. 1: cifra las credenciales que se guardan en la base de datos (las del certificador FEL y, cuando se
 * integre, las del sistema de inventario). La llave maestra vive fuera de la base de datos (Architecture.md §7.5).
 */
public interface CifradorSecretos {

    /** El secreto cifrado, listo para guardarse. Cada llamada produce un resultado distinto. */
    byte[] cifrar(String secreto);

    /** El secreto original. Falla si el contenido se alteró o se cifró con otra llave. */
    String descifrar(byte[] cifrado);
}
