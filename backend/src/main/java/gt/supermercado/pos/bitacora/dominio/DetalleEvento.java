package gt.supermercado.pos.bitacora.dominio;

import java.util.Map;

/**
 * HU-11.08 crit. 1: todo lo que se guardó del evento, con los valores anteriores y nuevos ({@code cambios}). Crit. 2:
 * {@code documento} es el documento relacionado que se puede abrir (venta, factura, cierre...), si lo hay.
 *
 * @param cambios el detalle guardado tal como está (por campo, {@code antes} y {@code despues}), o {@code null}
 */
public record DetalleEvento(RegistroConsultado registro, String entidad, Long entidadId, Map<String, Object> cambios,
                            Documento documento) {

    /**
     * El documento relacionado. {@code tipo}: {@code FACTURA}, {@code CIERRE}, {@code PRODUCTO} o {@code USUARIO};
     * el de una venta es su factura.
     */
    public record Documento(String tipo, long id, String etiqueta) {}
}
