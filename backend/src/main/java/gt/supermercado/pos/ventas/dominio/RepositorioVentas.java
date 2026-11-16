package gt.supermercado.pos.ventas.dominio;

import gt.supermercado.pos.impuestos.dominio.LineaCalculada;

import java.math.BigDecimal;
import java.util.Optional;

public interface RepositorioVentas {

    /** La venta en curso del turno, con sus líneas. */
    Optional<Venta> enCursoDelTurno(long turnoId);

    Optional<Venta> buscarPorId(long id);

    /**
     * Crea la venta en curso del turno si todavía no tiene una y devuelve su id. Si otra petición la creó al mismo
     * tiempo, devuelve esa: un turno tiene como máximo una venta en curso (índice {@code venta_en_curso_por_turno}).
     */
    long iniciar(long turnoId, long cajaId, long usuarioId, BigDecimal tasaIva);

    /**
     * Pasa la venta de {@code EN_CURSO} a {@code CANCELADA} (HU-06.14). Devuelve {@code false} si ya no estaba en
     * curso, por ejemplo porque otra petición la canceló al mismo tiempo.
     */
    boolean cancelar(long ventaId);

    /**
     * Pasa la venta de {@code EN_CURSO} a {@code SUSPENDIDA} con la hora del servidor (HU-06.12), con todas sus líneas.
     * Devuelve {@code false} si ya no estaba en curso.
     */
    boolean suspender(long ventaId);

    /** Cuántas ventas suspendidas tiene el turno (HU-06.12 crit. 4). */
    int suspendidasDelTurno(long turnoId);

    /**
     * Bloquea la venta hasta el final de la transacción, para que dos productos escaneados al mismo tiempo no
     * calculen la misma línea a la vez. Devuelve {@code false} si ya no está en curso.
     */
    boolean bloquearEnCurso(long ventaId);

    /**
     * La línea de ese producto con ese precio unitario, si ya se agregó (HU-06.02 crit. 2). Con otro precio es otra
     * línea (HU-06.09 crit. 3).
     */
    Optional<LineaVenta> lineaDelProducto(long ventaId, long productoId, BigDecimal precioUnitario);

    /** La línea, solo si pertenece a esa venta: nadie cambia líneas de otra venta con su id (HU-06.04 crit. 2). */
    Optional<LineaVenta> lineaDeVenta(long ventaId, long lineaId);

    /** Agrega una línea nueva con la copia de la descripción y del precio vigente (HU-06.09). */
    void agregarLinea(long ventaId, long productoId, String descripcion, LineaCalculada linea);

    /** Cambia la cantidad de una línea y su desglose, conservando su precio unitario (HU-06.09). */
    void actualizarLinea(long lineaId, LineaCalculada linea);

    /**
     * HU-06.07: marca la línea como eliminada por el usuario; deja de contar en la venta y en sus totales, pero no se
     * borra. Devuelve {@code false} si ya estaba eliminada.
     */
    boolean eliminarLinea(long lineaId, long usuarioId);

    /** Si el usuario tiene una venta en curso con al menos un producto (HU-01.16 crit. 2). */
    boolean tieneVentaConProductos(long usuarioId);}
