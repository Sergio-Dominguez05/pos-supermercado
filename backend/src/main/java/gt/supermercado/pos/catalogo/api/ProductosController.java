package gt.supermercado.pos.catalogo.api;

import gt.supermercado.pos.catalogo.aplicacion.GestionProductos;
import gt.supermercado.pos.catalogo.dominio.CodigoNoEncontrado;
import gt.supermercado.pos.catalogo.dominio.FiltroProductos;
import gt.supermercado.pos.catalogo.dominio.FiltroProductos.OrdenProductos;
import gt.supermercado.pos.catalogo.dominio.PaginaProductos;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * EP-03 Catálogo. HU-03.01 registrar y HU-03.02 editar un producto, con el permiso de crear productos y cambiar
 * precios (el Administrador).
 */
@RestController
@RequestMapping("/api/productos")
public class ProductosController {

    private final GestionProductos gestionProductos;

    public ProductosController(GestionProductos gestionProductos) {
        this.gestionProductos = gestionProductos;
    }

    /**
     * Las reglas de formato se validan en {@link GestionProductos} para que los rechazos queden en la bitácora; aquí
     * solo se limita el tamaño de lo que se acepta.
     */
    public record SolicitudProducto(@Size(max = 200) String codigoBarras, @Size(max = 400) String descripcion,
                                    BigDecimal precio) {}

    /**
     * HU-03.04: lista del catálogo. Cualquiera con permiso de consulta la ve; crear y editar siguen exigiendo
     * {@code PRODUCTO_EDITAR} en sus propios endpoints (crit. 6).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTO_CONSULTAR')")
    public PaginaProductos listar(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Boolean activo,
                                  @RequestParam(defaultValue = "DESCRIPCION") OrdenProductos orden,
                                  @RequestParam(defaultValue = "false") boolean descendente,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(defaultValue = "" + FiltroProductos.TAMANO_POR_DEFECTO) int tamano) {
        return gestionProductos.listar(new FiltroProductos(q, activo, orden, descendente, pagina, tamano));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTO_CONSULTAR')")
    public Producto obtener(@PathVariable long id) {
        return gestionProductos.obtener(id);
    }

    /** HU-03.06: búsqueda exacta por código, escaneado o escrito a mano; 404 si no existe. */
    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("hasAuthority('PRODUCTO_CONSULTAR')")
    public Producto buscarPorCodigo(@PathVariable String codigo, @AuthenticationPrincipal SesionActiva sesion) {
        return gestionProductos.buscarPorCodigo(codigo, sesion);
    }

    /** HU-03.07: productos activos cuya descripción contiene el texto (mínimo 3 caracteres). */
    @GetMapping("/busqueda")
    @PreAuthorize("hasAuthority('PRODUCTO_CONSULTAR')")
    public List<Producto> buscarPorDescripcion(@RequestParam String q) {
        return gestionProductos.buscarPorDescripcion(q);
    }

    /** HU-03.08 crit. 4: códigos buscados que no existen, para que quien registra productos los dé de alta. */
    @GetMapping("/codigos-no-encontrados")
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public List<CodigoNoEncontrado> codigosNoEncontrados() {
        return gestionProductos.codigosNoEncontrados();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Producto crear(@Valid @RequestBody SolicitudProducto solicitud, @AuthenticationPrincipal SesionActiva actor) {
        return gestionProductos.crear(solicitud.codigoBarras(), solicitud.descripcion(), solicitud.precio(), actor);
    }

    /** HU-03.02: solo la descripción y el precio; el código no se puede modificar y por eso no se recibe. */
    public record SolicitudEdicionProducto(@Size(max = 400) String descripcion, BigDecimal precio) {}

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Producto editar(@PathVariable long id, @Valid @RequestBody SolicitudEdicionProducto solicitud,
                           @AuthenticationPrincipal SesionActiva actor) {
        return gestionProductos.editar(id, solicitud.descripcion(), solicitud.precio(), actor);
    }

    /** HU-03.03: los productos no se borran; se desactivan y se pueden volver a activar. */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Producto desactivar(@PathVariable long id, @AuthenticationPrincipal SesionActiva actor) {
        return gestionProductos.desactivar(id, actor);
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Producto activar(@PathVariable long id, @AuthenticationPrincipal SesionActiva actor) {
        return gestionProductos.activar(id, actor);
    }
}
