package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.facturacion.dominio.ReceptorDeVenta;
import org.springframework.stereotype.Component;

/**
 * La factura va al cliente asignado a la venta, con su NIT y su nombre (HU-05.02), o a Consumidor Final si no tiene
 * (HU-05.01 crit. 3).
 */
@Component
class ReceptorDeVentaClientes implements ReceptorDeVenta {

    private final RepositorioClientes clientes;

    ReceptorDeVentaClientes(RepositorioClientes clientes) {
        this.clientes = clientes;
    }

    @Override
    public Receptor de(long ventaId) {
        return clientes.deLaVenta(ventaId)
                .map(cliente -> new Receptor(Receptor.TipoReceptor.NIT, cliente.nit(), cliente.nombre()))
                .orElseGet(Receptor::consumidorFinal);
    }
}
