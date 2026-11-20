package gt.supermercado.pos.catalogo.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.ConsultaBitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.CambioRegistrado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-03.11 Consultar el historial de precios.
 * <ul>
 *   <li>Crit. 1: los cambios de precio del producto, con la fecha y hora, el precio anterior, el nuevo y quién lo
 *       cambió.</li>
 *   <li>Crit. 2: del más reciente al más antiguo.</li>
 *   <li>Crit. 3: salen de la bitácora ({@code PRECIO_CAMBIADO}, HU-03.02 crit. 6), que no se puede modificar.</li>
 * </ul>
 */
@Service
public class HistorialPrecios {

    private final GestionProductos productos;
    private final ConsultaBitacora bitacora;

    public HistorialPrecios(GestionProductos productos, ConsultaBitacora bitacora) {
        this.productos = productos;
        this.bitacora = bitacora;
    }

    /** Un cambio de precio. {@code usuario} es el nombre y el usuario de quien lo hizo. */
    public record CambioDePrecio(OffsetDateTime cambiadoEn, BigDecimal anterior, BigDecimal nuevo, String usuario) {}

    /** 404 {@code PRODUCTO_NO_ENCONTRADO} si el producto no existe. */
    @Transactional(readOnly = true)
    public List<CambioDePrecio> de(long productoId) {
        productos.obtener(productoId);
        return bitacora.cambios(AccionSensible.PRECIO_CAMBIADO, "producto", productoId, "precio").stream()
                .map(HistorialPrecios::cambio).toList();
    }

    private static CambioDePrecio cambio(CambioRegistrado registro) {
        return new CambioDePrecio(registro.momento(), monto(registro.antes()), monto(registro.despues()),
                registro.usuario());
    }

    private static BigDecimal monto(String valor) {
        return valor == null ? null : new BigDecimal(valor).setScale(2);
    }
}
