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
     * Bloquea la venta hasta el final de la transacción, para que dos productos escaneados al mismo tiempo no
     * calculen la misma línea a la vez. Devuelve {@code false} si ya no está en curso.
     */
    boolean bloquearEnCurso(long ventaId);

    /**
     * La línea de ese producto con ese precio unitario, si ya se agregó (HU-06.02 crit. 2). Con otro precio es otra
     * línea (HU-06.09 crit. 3).
     */
    Optional<LineaVenta> lineaDelProducto(long ventaId, long productoId, BigDecimal precioUnitario);

    /** Agrega una línea nueva con la copia de la descripción y del precio vigente (HU-06.09). */
    void agregarLinea(long ventaId, long productoId, String descripcion, LineaCalculada linea);

    /** Cambia la cantidad de una línea y su desglose, conservando su precio unitario (HU-06.09). */
    void actualizarLinea(long lineaId, LineaCalculada linea);

    /** Si el usuario tiene una venta en curso con al menos un producto (HU-01.16 crit. 2). */
    boolean tieneVentaConProductos(long usuarioId);}
