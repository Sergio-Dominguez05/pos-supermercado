package gt.supermercado.pos.facturacion.dominio;

/**
 * Los datos con los que el POS se conecta al certificador (HU-08.01 crit. 1), con la llave o token en claro. Solo
 * existe en memoria: en la base de datos el secreto está cifrado (HU-12.15) y la API nunca lo devuelve.
 */
public record ConexionFel(String certificador, String urlServicio, String usuario, String secreto,
                          AmbienteFel ambiente, int timeoutMs) {

    /** Sin el secreto, para que nunca termine en un registro o en un mensaje. */
    @Override
    public String toString() {
        return "ConexionFel[certificador=%s, urlServicio=%s, usuario=%s, ambiente=%s, timeoutMs=%d]"
                .formatted(certificador, urlServicio, usuario, ambiente, timeoutMs);
    }
}
