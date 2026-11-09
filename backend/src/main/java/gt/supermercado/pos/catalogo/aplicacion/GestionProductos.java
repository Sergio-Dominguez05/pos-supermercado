package gt.supermercado.pos.catalogo.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.ReglasProducto;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Catálogo de productos. HU-03.01: registrar un producto. HU-03.02: editar su descripción y precio. Activar y
 * desactivar (HU-03.03) y consultar (HU-03.04 a 03.07) se agregan aquí.
 */
@Service
public class GestionProductos {

    private static final String MENSAJE_DESCRIPCION = "La descripción es obligatoria y tiene como máximo %d caracteres."
            .formatted(ReglasProducto.LARGO_MAXIMO_DESCRIPCION);
    private static final String MENSAJE_PRECIO = "El precio debe ser mayor que cero y tener como máximo 2 decimales.";

    private final RepositorioProductos productos;
    private final Bitacora bitacora;

    public GestionProductos(RepositorioProductos productos, Bitacora bitacora) {
        this.productos = productos;
        this.bitacora = bitacora;
    }

    @Transactional(readOnly = true)
    public Producto obtener(long id) {
        return productos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PRODUCTO_NO_ENCONTRADO", "El producto no existe."));
    }

    /**
     * Registra un producto activo y disponible para la venta de inmediato (HU-03.01 crit. 6). El alta y cada intento
     * rechazado quedan en la bitácora con el usuario que lo hizo (crit. 7, HU-11.01 crit. 3).
     */
    @Transactional
    public Producto crear(String codigoBarras, String descripcion, BigDecimal precio, SesionActiva actor) {
        String codigo = ReglasProducto.normalizarCodigo(codigoBarras);
        String descripcionNormal = ReglasProducto.normalizarDescripcion(descripcion);

        if (!ReglasProducto.esCodigoValido(codigo)) {
            throw rechazarAlta(actor, "CODIGO_INVALIDO",
                    "El código de barras es obligatorio: de 1 a %d letras, números o guiones."
                            .formatted(ReglasProducto.LARGO_MAXIMO_CODIGO));
        }
        if (!ReglasProducto.esDescripcionValida(descripcionNormal)) {
            throw rechazarAlta(actor, "DESCRIPCION_INVALIDA", MENSAJE_DESCRIPCION);
        }
        if (!ReglasProducto.esPrecioValido(precio)) {
            throw rechazarAlta(actor, "PRECIO_INVALIDO", MENSAJE_PRECIO);
        }
        productos.buscarPorCodigo(codigo).ifPresent(otro -> {
            throw codigoDuplicado(actor, otro.descripcion());
        });

        BigDecimal precioNormal = ReglasProducto.normalizarPrecio(precio);
        long id;
        try {
            id = productos.crear(codigo, descripcionNormal, precioNormal);
        } catch (DuplicateKeyException e) {
            // Otro usuario registró el mismo código al mismo tiempo.
            throw codigoDuplicado(actor, null);
        }
        bitacora.registrar(evento(actor, AccionSensible.PRODUCTO_CREADO, true,
                "Producto '%s' (%s) creado con precio Q%s".formatted(descripcionNormal, codigo, precioNormal), id, null));
        return obtener(id);
    }

    /**
     * Cambia la descripción y el precio (HU-03.02 crit. 1). El código no se recibe: no se puede modificar
     * (crit. 2, y la base de datos también lo impide). Cada cambio queda en la bitácora con el valor anterior y el
     * nuevo (crit. 6): {@code PRODUCTO_EDITADO} para la descripción y {@code PRECIO_CAMBIADO} para el precio, que es
     * la base del historial de precios (HU-03.11). Si no cambia nada, no se registra nada.
     *
     * <p>Las ventas guardan una copia del precio en cada línea (HU-06.09), así que el precio nuevo solo se aplica a lo
     * que se agregue desde ahora (crit. 3) y no altera ventas en curso ni facturas emitidas (crit. 4 y 5).
     */
    @Transactional
    public Producto editar(long id, String descripcion, BigDecimal precio, SesionActiva actor) {
        Producto actual = obtener(id);
        String descripcionNormal = ReglasProducto.normalizarDescripcion(descripcion);

        if (!ReglasProducto.esDescripcionValida(descripcionNormal)) {
            throw rechazarEdicion(actor, AccionSensible.PRODUCTO_EDITADO, actual, "DESCRIPCION_INVALIDA",
                    MENSAJE_DESCRIPCION);
        }
        if (!ReglasProducto.esPrecioValido(precio)) {
            throw rechazarEdicion(actor, AccionSensible.PRECIO_CAMBIADO, actual, "PRECIO_INVALIDO", MENSAJE_PRECIO);
        }
        BigDecimal precioNormal = ReglasProducto.normalizarPrecio(precio);
        boolean cambiaDescripcion = !descripcionNormal.equals(actual.descripcion());
        boolean cambiaPrecio = precioNormal.compareTo(actual.precio()) != 0;
        if (!cambiaDescripcion && !cambiaPrecio) {
            return actual;
        }

        productos.actualizar(id, descripcionNormal, precioNormal);
        if (cambiaDescripcion) {
            bitacora.registrar(evento(actor, AccionSensible.PRODUCTO_EDITADO, true,
                    "Producto %s: descripción cambiada de '%s' a '%s'"
                            .formatted(actual.codigoBarras(), actual.descripcion(), descripcionNormal),
                    id, Map.of("descripcion", EventoBitacora.cambio(actual.descripcion(), descripcionNormal))));
        }
        if (cambiaPrecio) {
            bitacora.registrar(evento(actor, AccionSensible.PRECIO_CAMBIADO, true,
                    "Producto '%s' (%s): precio cambiado de Q%s a Q%s"
                            .formatted(descripcionNormal, actual.codigoBarras(), actual.precio(), precioNormal),
                    id, Map.of("precio", EventoBitacora.cambio(actual.precio(), precioNormal))));
        }
        return obtener(id);
    }

    /** HU-03.01 crit. 2: el mensaje dice a qué producto está asignado el código. */
    private NegocioException codigoDuplicado(SesionActiva actor, String descripcionDelOtro) {
        return rechazarAlta(actor, "CODIGO_DUPLICADO", descripcionDelOtro == null
                ? "El código de barras ya está asignado a otro producto."
                : "El código de barras ya está asignado al producto '%s'.".formatted(descripcionDelOtro));
    }

    private static EventoBitacora evento(SesionActiva actor, AccionSensible accion, boolean exitoso, String descripcion,
                                         Long productoId, Map<String, Object> cambios) {
        return new EventoBitacora(actor.usuarioId(), actor.rol().name(), accion, exitoso, descripcion,
                productoId == null ? null : "producto", productoId, cambios);
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazarAlta(SesionActiva actor, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(evento(actor, AccionSensible.PRODUCTO_CREADO, false,
                "Registro de producto rechazado: " + mensaje, null, null));
        return new NegocioException(codigo, mensaje);
    }

    private NegocioException rechazarEdicion(SesionActiva actor, AccionSensible accion, Producto producto, String codigo,
                                             String mensaje) {
        bitacora.registrarIntentoFallido(evento(actor, accion, false,
                "Edición del producto %s rechazada: %s".formatted(producto.codigoBarras(), mensaje), producto.id(), null));
        return new NegocioException(codigo, mensaje);
    }
}
