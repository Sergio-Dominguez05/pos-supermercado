package gt.supermercado.pos.facturacion.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta del certificador al certificar un DTE (HU-08.03, 08.06).
 *
 * @param respuesta la respuesta completa del certificador, tal como llegó; se guarda con cada intento (HU-08.06 crit. 4)
 */
public record ResultadoCertificacion(
        boolean certificada,
        UUID autorizacion,
        String serie,
        String numero,
        OffsetDateTime fechaCertificacion,
        String codigoError,
        String mensajeError,
        String respuesta) {

    public static ResultadoCertificacion aceptada(UUID autorizacion, String serie, String numero,
                                                  OffsetDateTime fechaCertificacion) {
        return new ResultadoCertificacion(true, autorizacion, serie, numero, fechaCertificacion, null, null, null);
    }

    public static ResultadoCertificacion rechazada(String codigoError, String mensajeError, String respuesta) {
        return new ResultadoCertificacion(false, null, null, null, null, codigoError, mensajeError, respuesta);
    }
}
