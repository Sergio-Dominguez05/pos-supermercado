package gt.supermercado.pos.bitacora.dominio;

import java.util.List;

/**
 * Resultado de verificar la cadena de la bitácora (HU-11.05 crit. 3).
 *
 * @param registros cantidad de registros encadenados
 * @param problemas registros alterados, eslabones rotos o registros faltantes; vacío si está íntegra
 */
public record ResultadoVerificacion(long registros, List<Problema> problemas) {

    public record Problema(long secuencia, String descripcion) {}

    public boolean integra() {
        return problemas.isEmpty();
    }
}
