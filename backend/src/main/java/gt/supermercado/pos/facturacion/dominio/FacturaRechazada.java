package gt.supermercado.pos.facturacion.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * HU-08.06 crit. 3 y 4: una venta cobrada cuya factura rechazó el certificador, pendiente de facturar, con la respuesta
 * completa del último rechazo.
 *
 * @param respuesta la respuesta del certificador tal como llegó
 */
public record FacturaRechazada(long facturaId, long ventaId, long ventaNumero, String cajaNombre, String cajero,
                               OffsetDateTime emitidaEn, BigDecimal total, String receptorId, String receptorNombre,
                               String codigo, String mensajeCertificador, String respuesta,
                               OffsetDateTime rechazadaEn) {

    /** Crit. 1: el motivo en lenguaje comprensible y si se corrige en caja. */
    public MotivoRechazo motivo() {
        return TraductorErrores.traducir(codigo, mensajeCertificador);
    }
}
