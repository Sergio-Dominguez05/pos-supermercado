package gt.supermercado.pos.facturacion.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * HU-08.13: una página del historial de facturas, de la más reciente a la más antigua.
 *
 * @param total cuántas facturas cumplen los filtros, para paginar
 */
public record HistorialFacturas(List<Fila> facturas, int pagina, int tamanoPagina, long total) {

    /**
     * Crit. 1: fecha, serie, número, autorización, cliente, total, estado y caja; también el cajero (para filtrar) y,
     * en contingencia, el número de acceso que hace las veces de número mientras no se certifica (HU-08.07).
     */
    public record Fila(long facturaId, long ventaNumero, OffsetDateTime emitidaEn, String serie, String numero,
                       UUID autorizacion, Long numeroAcceso, String receptorId, String receptorNombre,
                       BigDecimal total, EstadoFactura estado, String caja, String cajero) {}
}
