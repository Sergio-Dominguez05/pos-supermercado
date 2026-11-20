package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.Cliente;
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

    /** HU-05.06 crit. 4: el cliente con CUI va a la factura con su CUI ({@code TipoEspecial="CUI"} en el DTE). */
    static Receptor receptor(Cliente cliente) {
        return new Receptor(Receptor.TipoReceptor.valueOf(cliente.tipoIdentificacion()), cliente.nit(),
                cliente.nombre());
    }

    @Override
    public Optional<String> correoDeFactura(long ventaId) {
        return ventas.correoFactura(ventaId);
    }

    @Override
    public Receptor de(long ventaId) {
        return clientes.deLaVenta(ventaId)
                .map(ReceptorDeVentaClientes::receptor)
                .orElseGet(Receptor::consumidorFinal);
    }
}
