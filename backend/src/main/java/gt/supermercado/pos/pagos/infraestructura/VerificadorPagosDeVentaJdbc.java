package gt.supermercado.pos.pagos.infraestructura;

import gt.supermercado.pos.pagos.dominio.RepositorioPagos;
import gt.supermercado.pos.ventas.dominio.VerificadorPagosDeVenta;
import org.springframework.stereotype.Component;

/** HU-07.03: avisa a ventas si la venta ya tiene un pago, para no modificarla después de cobrarla. */
@Component
class VerificadorPagosDeVentaJdbc implements VerificadorPagosDeVenta {

    private final RepositorioPagos pagos;

    VerificadorPagosDeVentaJdbc(RepositorioPagos pagos) {
        this.pagos = pagos;
    }

    @Override
    public boolean tienePagos(long ventaId) {
        return pagos.tienePagos(ventaId);
    }
}
