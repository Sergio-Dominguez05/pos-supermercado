package gt.supermercado.pos.ventas.infraestructura;

import gt.supermercado.pos.ventas.dominio.ValidadorClienteDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Component;

/**
 * Implementación provisional de HU-06.15 crit. 3 mientras no exista el módulo de clientes (EP-05): toda venta pasa
 * al cobro. HU-05.07 (#43) la reemplaza con la regla del límite de Consumidor Final; al hacerlo, borrar esta clase.
 */
@Component
class SinReglasDeCliente implements ValidadorClienteDeVenta {

    @Override
    public void validar(Venta venta) {
        // Sin módulo de clientes no hay reglas que aplicar.
    }
}
