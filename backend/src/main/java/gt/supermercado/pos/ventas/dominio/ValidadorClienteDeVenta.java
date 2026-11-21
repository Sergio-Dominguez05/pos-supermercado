package gt.supermercado.pos.ventas.dominio;

/**
 * HU-06.15 crit. 3: antes de pasar al cobro, el cliente asignado a la venta debe cumplir las reglas de EP-05, por
 * ejemplo que Consumidor Final no supere el límite de la SAT (HU-05.07). Si no las cumple, lanza una
 * {@link gt.supermercado.pos.compartido.dominio.NegocioException} con el motivo listo para mostrarse.
 *
 * <p>Lo implementa el módulo de clientes ({@code LimiteConsumidorFinal}, HU-05.07); se revisa al pasar al cobro y al
 * finalizar la venta.
 */
public interface ValidadorClienteDeVenta {

    void validar(Venta venta);
}
