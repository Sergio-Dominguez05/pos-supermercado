package gt.supermercado.pos.ventas.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.catalogo.aplicacion.GestionProductos;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.aplicacion.Parametros;
import gt.supermercado.pos.impuestos.dominio.CalculadoraIva;
import gt.supermercado.pos.impuestos.dominio.TasaIva;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import gt.supermercado.pos.ventas.dominio.ValidadorClienteDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import gt.supermercado.pos.ventas.dominio.VerificadorPagosDeVenta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Venta en curso del cajero (EP-06). Toda venta pertenece al turno propio abierto en la caja de la sesión
 * (HU-09.02 crit. 2): sin turno, se rechaza con {@code CAJA_CERRADA} (HU-06.01 crit. 2).
 */
@Service
public class GestionVentas {

    /**
     * Tope técnico de unidades por línea (HU-06.04 crit. 3). Por encima de la cantidad máxima configurada ya se pide
     * confirmación (crit. 4); este tope evita, además, cantidades absurdas aunque se confirmen.
     */
    public static final int CANTIDAD_MAXIMA = 9999;

    private final RepositorioVentas ventas;
    private final GestionTurnos turnos;
    private final GestionProductos productos;
    private final Parametros parametros;
    private final ValidadorClienteDeVenta clientes;
    private final VerificadorPagosDeVenta pagos;
    private final Bitacora bitacora;

    public GestionVentas(RepositorioVentas ventas, GestionTurnos turnos, GestionProductos productos,
                         Parametros parametros, ValidadorClienteDeVenta clientes,
                         VerificadorPagosDeVenta pagos, Bitacora bitacora) {
        this.ventas = ventas;
        this.turnos = turnos;
        this.productos = productos;
        this.parametros = parametros;
        this.clientes = clientes;
        this.pagos = pagos;
        this.bitacora = bitacora;
    }

    /** La venta en curso del turno del cajero, si tiene una. */
    @Transactional(readOnly = true)
    public Optional<Venta> actual(SesionActiva sesion) {
        return ventas.enCursoDelTurno(turnos.exigirTurnoAbierto(sesion).id());
    }

    /**
     * HU-06.01: deja la caja lista para recibir productos (crit. 1). Si ya hay una venta en curso en el turno, la
     * devuelve; si no, crea una con un número interno único (crit. 4) y la tasa de IVA vigente (HU-04.01 crit. 3).
     */
    @Transactional
    public Venta iniciar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        long id = ventas.iniciar(turno.id(), turno.cajaId(), sesion.usuarioId(), TasaIva.VIGENTE.valor());
        return ventas.buscarPorId(id).orElseThrow();
    }

    /**
     * HU-06.02: agrega a la venta en curso el producto escaneado (o escrito, HU-06.03 crit. 1). Si el turno todavía
     * no tiene venta, la inicia.
     * <ul>
     *   <li>Crit. 1: un producto activo se agrega con cantidad 1 y su precio vigente.</li>
     *   <li>Crit. 2: si ya está en la venta, se suma 1 a la cantidad de su línea, que conserva el precio con el que se
     *       agregó (HU-06.09); no se crea otra línea.</li>
     *   <li>Crit. 4: si el código no existe o el producto está inactivo, se aplica lo de EP-03 con
     *       {@link GestionProductos#paraVender}: 404 con el código anotado (HU-03.08) o 422 {@code PRODUCTO_INACTIVO}
     *       (HU-03.09), y no se agrega nada.</li>
     * </ul>
     * HU-06.04 crit. 1: con el prefijo {@code 6*} del lector se agregan (o suman) {@code cantidad} unidades; la
     * cantidad se valida antes de buscar el producto (crit. 3 y 4). El código anotado por HU-03.08 se guarda aunque
     * la respuesta sea 404 ({@code noRollbackFor}).
     */
    @Transactional(noRollbackFor = RecursoNoEncontradoException.class)
    public Venta agregarPorCodigo(String codigo, int cantidad, boolean confirmada, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        validarCantidad(cantidad, confirmada);
        Producto producto = productos.paraVender(codigo, sesion);
        return agregar(turno, producto, cantidad, sesion);
    }

    /**
     * HU-06.03 crit. 2: agrega el producto elegido en la búsqueda por descripción. Si ya está en la venta, suma a
     * su línea igual que al escanearlo (crit. 3). Un producto desactivado después de la búsqueda se rechaza
     * (HU-03.09). El prefijo de cantidad también vale aquí (HU-06.04 crit. 1).
     */
    @Transactional
    public Venta agregarProducto(long productoId, int cantidad, boolean confirmada, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        validarCantidad(cantidad, confirmada);
        return agregar(turno, productos.paraVender(productoId), cantidad, sesion);
    }

    /**
     * HU-06.04 crit. 2: cambia la cantidad de una línea ya agregada de la venta en curso. La línea se recalcula con
     * su propio precio unitario (HU-06.09) y la tasa guardada en la venta, y los totales se devuelven recalculados
     * (HU-04.04 crit. 6). Quitar la línea es HU-06.07: aquí la cantidad debe ser mayor que cero (crit. 3).
     */
    @Transactional
    public Venta cambiarCantidad(long lineaId, int cantidad, boolean confirmada, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        validarCantidad(cantidad, confirmada);
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso."));
        if (!ventas.bloquearEnCurso(venta.id())) {
            throw new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        exigirSinPagos(venta.id());
        LineaVenta linea = ventas.lineaDeVenta(venta.id(), lineaId).orElseThrow(
                () -> new RecursoNoEncontradoException("LINEA_NO_ENCONTRADA", "La línea no está en la venta en curso."));
        ventas.actualizarLinea(linea.id(),
                CalculadoraIva.linea(linea.precioUnitario(), cantidad, TasaIva.de(venta.tasaIva())));
        return ventas.buscarPorId(venta.id()).orElseThrow();
    }

    /**
     * HU-06.04 crit. 3: la cantidad es un entero mayor que cero, hasta {@link #CANTIDAD_MAXIMA}. Crit. 4: si supera
     * la cantidad máxima por línea configurada, el cajero debe confirmarla para evitar errores de digitación.
     */
    private void validarCantidad(int cantidad, boolean confirmada) {
        if (cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            throw cantidadInvalida();
        }
        int limite = parametros.cantidadMaximaPorLinea();
        if (cantidad > limite && !confirmada) {
            throw new NegocioException("CANTIDAD_POR_CONFIRMAR",
                    "La cantidad %d es mayor que %d. Confírmela para continuar.".formatted(cantidad, limite));
        }
    }

    private static NegocioException cantidadInvalida() {
        return new NegocioException("CANTIDAD_INVALIDA",
                "La cantidad debe ser un número entero entre 1 y %d.".formatted(CANTIDAD_MAXIMA));
    }

    /**
     * Agrega unidades del producto con su precio vigente. HU-06.09: cada línea guarda el precio con el que se agregó
     * (crit. 1) y nunca cambia (crit. 2); si el precio cambió desde que el producto entró a la venta, las unidades
     * nuevas van en otra línea con el precio nuevo (crit. 3). Si ya hay una línea del producto con el precio vigente,
     * se le suman (HU-06.02 crit. 2).
     */
    private Venta agregar(Turno turno, Producto producto, int cantidad, SesionActiva sesion) {
        long ventaId = ventas.iniciar(turno.id(), turno.cajaId(), sesion.usuarioId(), TasaIva.VIGENTE.valor());
        if (!ventas.bloquearEnCurso(ventaId)) {
            throw new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        exigirSinPagos(ventaId);
        Venta venta = ventas.buscarPorId(ventaId).orElseThrow();
        TasaIva tasa = TasaIva.de(venta.tasaIva());
        Optional<LineaVenta> existente = ventas.lineaDelProducto(ventaId, producto.id(), producto.precio());
        if (existente.isPresent()) {
            LineaVenta linea = existente.get();
            if (linea.cantidad() + cantidad > CANTIDAD_MAXIMA) {
                throw cantidadInvalida();
            }
            ventas.actualizarLinea(linea.id(),
                    CalculadoraIva.linea(linea.precioUnitario(), linea.cantidad() + cantidad, tasa));
        } else {
            ventas.agregarLinea(ventaId, producto.id(), producto.descripcion(),
                    CalculadoraIva.linea(producto.precio(), cantidad, tasa));
        }
        return ventas.buscarPorId(ventaId).orElseThrow();
    }

    /**
     * HU-06.07: quita una línea de la venta en curso del turno.
     * <ul>
     *   <li>Crit. 1: se elige la línea por su id; solo las de la venta en curso propia (404 si no).</li>
     *   <li>Crit. 2: no requiere autorización de un supervisor; basta el permiso del cajero.</li>
     *   <li>Crit. 3: la línea queda en la bitácora con el producto, la cantidad y el usuario; también el intento
     *       rechazado (HU-11.01 crit. 3). No se borra: se marca como eliminada (regla de V1, migración V17).</li>
     *   <li>Crit. 4: devuelve la venta con los totales recalculados (HU-04.04 crit. 6).</li>
     * </ul>
     */
    @Transactional
    public Venta eliminarLinea(long lineaId, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.enCursoDelTurno(turno.id()).orElseThrow(() -> rechazarEliminacion(sesion, lineaId,
                new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso.")));
        if (!ventas.bloquearEnCurso(venta.id())) {
            throw rechazarEliminacion(sesion, lineaId,
                    new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso."));
        }
        if (pagos.tienePagos(venta.id())) {
            throw rechazarEliminacion(sesion, lineaId, ventaConPago());
        }
        LineaVenta linea = ventas.lineaDeVenta(venta.id(), lineaId).orElseThrow(() -> rechazarEliminacion(sesion,
                lineaId, new RecursoNoEncontradoException("LINEA_NO_ENCONTRADA",
                        "La línea no está en la venta en curso.")));
        ventas.eliminarLinea(linea.id(), sesion.usuarioId());
        Map<String, Object> cambios = producto(linea);
        cambios.put("venta", venta.numero());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.LINEA_VENTA_ELIMINADA, true, "Línea eliminada de la venta N.º %d: %d x %s (Q%s)"
                .formatted(venta.numero(), linea.cantidad(), linea.descripcion(),
                        linea.subtotalConIva().setScale(2, RoundingMode.HALF_UP)),
                "venta_linea", linea.id(), cambios));
        return ventas.buscarPorId(venta.id()).orElseThrow();
    }

    /** El intento de eliminar una línea que se rechazó también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazarEliminacion(SesionActiva sesion, long lineaId, NegocioException error) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.LINEA_VENTA_ELIMINADA, false, "Eliminación de línea rechazada: " + error.getMessage(),
                "venta_linea", lineaId, null));
        return error;
    }

    /**
     * HU-06.12: deja la venta en curso en espera para atender a otro cliente.
     * <ul>
     *   <li>Crit. 1: la venta queda {@code SUSPENDIDA} con todas sus líneas, y la caja queda lista para una venta
     *       nueva: la siguiente que se inicie en el turno es otra.</li>
     *   <li>Crit. 2: una caja puede tener varias ventas suspendidas; solo la venta en curso es única por turno.</li>
     *   <li>Crit. 3: una venta suspendida no se factura ni cuenta para la caja: solo una {@code FINALIZADA} lo hace.</li>
     *   <li>Crit. 4: la caja no se cierra con ventas suspendidas ({@link GestionTurnos#exigirSinVentasSuspendidas}).</li>
     * </ul>
     * Una venta sin productos no tiene nada que dejar en espera.
     */
    @Transactional
    public Venta suspender(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso."));
        if (venta.lineas().isEmpty()) {
            throw new NegocioException("VENTA_VACIA", "La venta no tiene productos: no hay nada que dejar en espera.");
        }
        if (!ventas.bloquearEnCurso(venta.id())) {
            throw new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        exigirSinPagos(venta.id());
        if (!ventas.suspender(venta.id())) {
            throw new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        return ventas.buscarPorId(venta.id()).orElseThrow();
    }

    /** HU-06.13 crit. 1 y 3: las ventas suspendidas de la caja de la sesión, con su hora, artículos y total. */
    @Transactional(readOnly = true)
    public List<Venta> suspendidas(SesionActiva sesion) {
        return ventas.suspendidasDeLaCaja(turnos.exigirTurnoAbierto(sesion).cajaId());
    }

    /**
     * HU-06.13: retoma una venta suspendida como venta en curso.
     * <ul>
     *   <li>Crit. 2: se carga con todas sus líneas.</li>
     *   <li>Crit. 3: solo de la caja propia; de otra caja, 404 como si no existiera.</li>
     *   <li>Crit. 4: las líneas no se recalculan: conservan el precio con el que se agregaron (HU-06.09).</li>
     * </ul>
     * Un turno tiene una sola venta en curso. La pantalla siempre tiene una lista, normalmente vacía: si está vacía se
     * cancela (no tiene nada que perder); si tiene productos, el cajero debe suspenderla o cancelarla antes.
     */
    @Transactional
    public Venta retomar(long ventaId, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        ventas.bloquearTurno(turno.id());
        Optional<Venta> enCurso = ventas.enCursoDelTurno(turno.id());
        if (enCurso.isPresent()) {
            if (!enCurso.get().lineas().isEmpty()) {
                throw new NegocioException("VENTA_EN_CURSO_CON_PRODUCTOS",
                        "Hay una venta en curso con productos: suspéndala o cancélela antes de retomar otra.");
            }
            ventas.cancelar(enCurso.get().id());
        }
        if (!ventas.retomar(ventaId, turno.cajaId(), turno.id(), sesion.usuarioId())) {
            throw new RecursoNoEncontradoException("VENTA_SUSPENDIDA_NO_ENCONTRADA",
                    "La venta no está suspendida en esta caja.");
        }
        return ventas.buscarPorId(ventaId).orElseThrow();
    }

    /**
     * HU-06.15: el cajero terminó de registrar productos y pasa al cobro. El servidor valida antes de que la pantalla
     * abra el cobro con tarjeta (EP-07) y devuelve la venta con el total a cobrar.
     * <ul>
     *   <li>Crit. 2: una venta sin productos no pasa al cobro ({@code VENTA_VACIA}).</li>
     *   <li>Crit. 3: el cliente asignado debe cumplir las reglas de EP-05 ({@link ValidadorClienteDeVenta}).</li>
     *   <li>Crit. 4: la venta sigue en curso: desde el cobro se puede regresar a agregar o quitar productos.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Venta pasarAlCobro(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso."));
        return validarParaCobrar(venta);
    }

    /**
     * HU-07.03: la venta en curso del turno, bloqueada hasta el final de la transacción para registrarle un pago, con
     * las mismas validaciones que al pasar al cobro (HU-06.15). La usa el módulo {@code pagos}, que debe correr en
     * la misma transacción.
     */
    @Transactional
    public Venta paraRegistrarPago(Turno turno) {
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso."));
        if (!ventas.bloquearEnCurso(venta.id())) {
            throw new NegocioException("SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        return validarParaCobrar(ventas.buscarPorId(venta.id()).orElseThrow());
    }

    private Venta validarParaCobrar(Venta venta) {
        if (venta.lineas().isEmpty()) {
            throw new NegocioException("VENTA_VACIA", "La venta no tiene productos: agregue al menos uno para cobrar.");
        }
        clientes.validar(venta);
        return venta;
    }

    /**
     * HU-07.03: con un pago registrado la venta ya se cobró en la terminal bancaria: no se agregan ni quitan
     * productos, no cambian las cantidades y no se suspende ni se cancela hasta eliminar el pago (HU-07.06).
     */
    private void exigirSinPagos(long ventaId) {
        if (pagos.tienePagos(ventaId)) {
            throw ventaConPago();
        }
    }

    private static NegocioException ventaConPago() {
        return new NegocioException("VENTA_CON_PAGO",
                "La venta ya tiene un pago con tarjeta registrado: elimine el pago antes de modificarla.");
    }

    /**
     * HU-06.14: cancela la venta en curso del turno. No requiere autorización de un supervisor (crit. 2): basta el
     * permiso del cajero. La venta queda {@code CANCELADA}, así que no genera factura ni cuenta para la caja
     * (crit. 3), y la cancelación queda en la bitácora con el detalle de los productos (crit. 4). La confirmación la
     * pide la pantalla (crit. 1).
     */
    @Transactional
    public Venta cancelar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> rechazar(sesion, "SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso para cancelar."));
        if (!ventas.bloquearEnCurso(venta.id())) {
            throw rechazar(sesion, "SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        if (pagos.tienePagos(venta.id())) {
            NegocioException error = ventaConPago();
            throw rechazar(sesion, error.getCodigo(), error.getMessage());
        }
        if (!ventas.cancelar(venta.id())) {
            throw rechazar(sesion, "SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.VENTA_CANCELADA,
                true, "Venta N.º %d cancelada: %d artículos, total Q%s".formatted(venta.numero(), venta.articulos(),
                        venta.totales().total()), "venta", venta.id(), detalle(venta)));
        return ventas.buscarPorId(venta.id()).orElseThrow();
    }

    /** Los productos de la venta cancelada, tal como estaban: código, descripción, cantidad y subtotal. */
    private static Map<String, Object> detalle(Venta venta) {
        List<Map<String, Object>> productos = venta.lineas().stream().map(GestionVentas::producto).toList();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("productos", productos);
        detalle.put("total", venta.totales().total());
        return detalle;
    }

    private static Map<String, Object> producto(LineaVenta linea) {
        Map<String, Object> producto = new LinkedHashMap<>();
        producto.put("codigo", linea.codigoBarras());
        producto.put("descripcion", linea.descripcion());
        producto.put("cantidad", linea.cantidad());
        producto.put("precioUnitario", linea.precioUnitario());
        producto.put("subtotal", linea.subtotalConIva());
        return producto;
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva sesion, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.VENTA_CANCELADA, false, "Cancelación de venta rechazada: " + mensaje, null, null, null));
        return new NegocioException(codigo, mensaje);
    }
}
