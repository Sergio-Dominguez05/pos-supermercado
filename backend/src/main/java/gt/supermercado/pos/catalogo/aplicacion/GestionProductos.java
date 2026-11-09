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

/**
 * Catálogo de productos. HU-03.01: registrar un producto. Editar (HU-03.02), activar y desactivar (HU-03.03) y
 * consultar (HU-03.04 a 03.07) se agregan aquí.
 */
@Service
public class GestionProductos {

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
     * Registra un producto activo y disponible para la venta de inmediato (crit. 6). El alta y cada intento rechazado
     * quedan en la bitácora con el usuario que lo hizo (crit. 7, HU-11.01 crit. 3).
     */
    @Transactional
    public Producto crear(String codigoBarras, String descripcion, BigDecimal precio, SesionActiva actor) {
        String codigo = ReglasProducto.normalizarCodigo(codigoBarras);
        String descripcionNormal = ReglasProducto.normalizarDescripcion(descripcion);

        if (!ReglasProducto.esCodigoValido(codigo)) {
            throw rechazar(actor, "CODIGO_INVALIDO",
                    "El código de barras es obligatorio: de 1 a %d letras, números o guiones."
                            .formatted(ReglasProducto.LARGO_MAXIMO_CODIGO));
        }
        if (!ReglasProducto.esDescripcionValida(descripcionNormal)) {
            throw rechazar(actor, "DESCRIPCION_INVALIDA",
                    "La descripción es obligatoria y tiene como máximo %d caracteres."
                            .formatted(ReglasProducto.LARGO_MAXIMO_DESCRIPCION));
        }
        if (!ReglasProducto.esPrecioValido(precio)) {
            throw rechazar(actor, "PRECIO_INVALIDO", "El precio debe ser mayor que cero y tener como máximo 2 decimales.");
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
        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), AccionSensible.PRODUCTO_CREADO,
                true, "Producto '%s' (%s) creado con precio Q%s".formatted(descripcionNormal, codigo, precioNormal),
                "producto", id, null));
        return obtener(id);
    }

    /** Crit. 2: el mensaje dice a qué producto está asignado el código. */
    private NegocioException codigoDuplicado(SesionActiva actor, String descripcionDelOtro) {
        return rechazar(actor, "CODIGO_DUPLICADO", descripcionDelOtro == null
                ? "El código de barras ya está asignado a otro producto."
                : "El código de barras ya está asignado al producto '%s'.".formatted(descripcionDelOtro));
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva actor, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(),
                AccionSensible.PRODUCTO_CREADO, false, "Registro de producto rechazado: " + mensaje, null, null, null));
        return new NegocioException(codigo, mensaje);
    }
}
