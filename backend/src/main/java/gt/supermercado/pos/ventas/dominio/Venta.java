package gt.supermercado.pos.ventas.dominio;

import gt.supermercado.pos.impuestos.dominio.CalculadoraIva;
import gt.supermercado.pos.impuestos.dominio.LineaCalculada;
import gt.supermercado.pos.impuestos.dominio.Totales;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Una venta y sus líneas (HU-06.01). Los totales los calcula siempre el servidor con las reglas de EP-04; la pantalla
 * solo los muestra (Architecture.md §6.1).
 *
 * @param numero  número interno único (HU-06.01 crit. 4)
 * @param tasaIva tasa con la que se calculó, guardada en la venta (HU-04.01 crit. 3)
 */
public record Venta(
        long id,
        long numero,
        long turnoId,
        long cajaId,
        String cajaNombre,
        long usuarioId,
        EstadoVenta estado,
        BigDecimal tasaIva,
        OffsetDateTime creadaEn,
        List<LineaVenta> lineas) {

    public Venta {
        lineas = List.copyOf(lineas);
    }

    public boolean enCurso() {
        return estado == EstadoVenta.EN_CURSO;
    }

    /** Cantidad de artículos: la suma de las cantidades de las líneas (HU-06.08 crit. 1). */
    public int articulos() {
        return lineas.stream().mapToInt(LineaVenta::cantidad).sum();
    }

    /** Subtotal, IVA y TOTAL según HU-04.04 y HU-04.05. */
    public Totales totales() {
        if (lineas.isEmpty()) {
            return new Totales(BigDecimal.ZERO.setScale(CalculadoraIva.DECIMALES_TOTALES),
                    BigDecimal.ZERO.setScale(CalculadoraIva.DECIMALES_TOTALES),
                    BigDecimal.ZERO.setScale(CalculadoraIva.DECIMALES_TOTALES));
        }
        return CalculadoraIva.totales(lineas.stream()
                .map(l -> new LineaCalculada(l.cantidad(), l.precioUnitario(), l.subtotalConIva(), l.baseImponible(), l.iva()))
                .toList());
    }
}
