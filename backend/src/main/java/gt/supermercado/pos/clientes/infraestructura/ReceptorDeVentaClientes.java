package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.facturacion.dominio.ReceptorDeVenta;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * La factura va al cliente asignado a la venta, con su NIT y su nombre (HU-05.02), o a Consumidor Final si no tiene
 * (HU-05.01 crit. 3).
 */
@Component
class ReceptorDeVentaClientes implements ReceptorDeVenta {

    private final RepositorioClientes clientes;
    private final GestionVentas ventas;

    ReceptorDeVentaClientes(RepositorioClientes clientes, GestionVentas ventas) {
        this.clientes = clientes;
        this.ventas = ventas;
    }

    @Override
    public Optional<String> correoDeFactura(long ventaId) {
        return ventas.correoFactura(ventaId);
    }

    @Override
    public Receptor de(long ventaId) {
        return clientes.deLaVenta(ventaId)
                .map(cliente -> new Receptor(Receptor.TipoReceptor.NIT, cliente.nit(), cliente.nombre()))
                .orElseGet(Receptor::consumidorFinal);
    }
}
