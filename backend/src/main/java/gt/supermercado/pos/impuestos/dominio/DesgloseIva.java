package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado de separar un precio con IVA en base imponible e IVA (HU-04.02).
 *
 * <p>Siempre se cumple {@code base + iva = precioConIva}: el IVA se obtiene por diferencia, así que el desglose
 * nunca cambia lo que paga el cliente (crit. 3).
 *
 * @param precioConIva el precio tal como está en el catálogo, sin modificar
 * @param base         base imponible con {@link CalculadoraIva#DECIMALES} decimales
 * @param iva          IVA con {@link CalculadoraIva#DECIMALES} decimales
 */
public record DesgloseIva(BigDecimal precioConIva, BigDecimal base, BigDecimal iva) {

    public DesgloseIva {
        Objects.requireNonNull(precioConIva, "El precio con IVA es obligatorio.");
        Objects.requireNonNull(base, "La base imponible es obligatoria.");
        Objects.requireNonNull(iva, "El IVA es obligatorio.");
    }
}
