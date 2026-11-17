package gt.supermercado.pos.pagos.infraestructura;

import gt.supermercado.pos.pagos.dominio.EmisionDeFactura;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Component;

/**
 * Implementación provisional de HU-07.08 crit. 1 mientras no exista la facturación (EP-08): la venta se finaliza sin
 * factura. HU-08.03 (#66) implementa {@link EmisionDeFactura} en el módulo {@code facturacion}; al hacerlo, borrar
 * esta clase.
 */
@Component
class SinFacturaTodavia implements EmisionDeFactura {

    @Override
    public boolean emitir(Venta venta) {
        return false;
    }
}
