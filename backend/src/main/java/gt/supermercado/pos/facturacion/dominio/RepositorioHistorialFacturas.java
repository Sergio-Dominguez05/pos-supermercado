package gt.supermercado.pos.facturacion.dominio;

import java.util.List;
import java.util.Optional;

/** Las consultas del historial (HU-08.13) y del detalle (HU-08.14) de las facturas. Solo leen. */
public interface RepositorioHistorialFacturas {

    HistorialFacturas buscar(FiltrosHistorial filtros);

    /** Sin las líneas ni los eventos: los arma el caso de uso con {@link #lineas}, {@link #pagos} y {@link #eventos}. */
    Optional<DetalleFactura> detalle(long facturaId);

    List<DetalleFactura.Linea> lineas(long ventaId);

    List<DetalleFactura.Pago> pagos(long ventaId);

    /** Los intentos ante el certificador, las reimpresiones y la anulación, en orden; sin la emisión. */
    List<EventoRegistrado> eventos(long facturaId);

    /** Un hecho tal como quedó guardado; el caso de uso lo describe en palabras. */
    record EventoRegistrado(java.time.OffsetDateTime momento, String origen, String operacion, Boolean exitoso,
                            String codigoError, String mensaje, String usuario, String caja) {}

    /** Las cajas y los cajeros con facturas, para los filtros del historial (crit. 2). */
    List<Opcion> cajas();

    List<Opcion> cajeros();

    record Opcion(long id, String etiqueta) {}
}
