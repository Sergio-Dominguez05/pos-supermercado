package gt.supermercado.pos.clientes.infraestructura;

import gt.supermercado.pos.clientes.dominio.RepositorioClientes;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.aplicacion.Parametros;
import gt.supermercado.pos.ventas.dominio.ValidadorClienteDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * HU-05.07 Validar el límite para Consumidor Final.
 * <ul>
 *   <li>Crit. 1: desde el límite (Q2,500.00, configurable en HU-12.07) una venta a Consumidor Final no se cobra: se
 *       exige el NIT o el CUI. Se revisa al pasar al cobro y otra vez al finalizar (decisión de Sergio), porque el
 *       cliente se puede cambiar con un pago ya registrado.</li>
 *   <li>Crit. 2: el aviso explica por qué se piden los datos.</li>
 *   <li>Crit. 3: si el total baja del límite, vuelve a poder facturarse a CF.</li>
 * </ul>
 */
@Component
class LimiteConsumidorFinal implements ValidadorClienteDeVenta {

    private final RepositorioClientes clientes;
    private final Parametros parametros;

    LimiteConsumidorFinal(RepositorioClientes clientes, Parametros parametros) {
        this.clientes = clientes;
        this.parametros = parametros;
    }

    @Override
    public void validar(Venta venta) {
        BigDecimal limite = parametros.limiteConsumidorFinal();
        if (venta.totales().total().compareTo(limite) >= 0 && clientes.deLaVenta(venta.id()).isEmpty()) {
            throw new NegocioException("LIMITE_CONSUMIDOR_FINAL", aviso(limite));
        }
    }

    static String aviso(BigDecimal limite) {
        return ("Las ventas de Q%,.2f o más no se pueden facturar a Consumidor Final: la SAT exige el NIT o el CUI del "
                + "comprador. Asigne el cliente (F6) antes de cobrar.").formatted(limite);
    }
}
