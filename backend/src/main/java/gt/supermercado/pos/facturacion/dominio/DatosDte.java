package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.ventas.dominio.Venta;

import java.time.OffsetDateTime;

/**
 * Todo lo que va en el DTE de una venta (HU-08.02 crit. 1): el emisor con su establecimiento, el receptor, la fecha de
 * emisión y la venta con sus líneas y totales tal como los guardó el POS (HU-04.09). {@code numeroAcceso} es el de un
 * comprobante emitido en contingencia (HU-08.07), o {@code null}.
 */
public record DatosDte(Emisor emisor, Establecimiento establecimiento, Receptor receptor, OffsetDateTime emitidaEn,
                       Venta venta, Long numeroAcceso) {

    /** Un DTE emitido con conexión con el certificador: sin número de acceso de contingencia. */
    public DatosDte(Emisor emisor, Establecimiento establecimiento, Receptor receptor, OffsetDateTime emitidaEn,
                    Venta venta) {
        this(emisor, establecimiento, receptor, emitidaEn, venta, null);
    }

    /** HU-08.07: el mismo DTE, emitido en contingencia con su número de acceso. */
    public DatosDte enContingencia(long numero) {
        return new DatosDte(emisor, establecimiento, receptor, emitidaEn, venta, numero);
    }
}
