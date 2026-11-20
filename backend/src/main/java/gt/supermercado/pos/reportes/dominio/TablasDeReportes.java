package gt.supermercado.pos.reportes.dominio;

import gt.supermercado.pos.reportes.dominio.TablaReporte.Columna;
import gt.supermercado.pos.reportes.dominio.TablaReporte.Dato;
import gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo.DINERO;
import static gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo.ENTERO;
import static gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo.FECHA;
import static gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo.FECHA_HORA;
import static gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo.TEXTO;

/**
 * Convierte cada reporte en la {@link TablaReporte} que se exporta, con las mismas columnas, el mismo orden y los
 * mismos totales que la pantalla (HU-10.13 crit. 3). Donde la pantalla junta dos datos en una celda (por ejemplo,
 * cantidad y monto anulados), el archivo los separa en dos columnas para que sigan siendo números (HU-10.14 crit. 2).
 */
public final class TablasDeReportes {

    private static final Map<String, String> ESTADOS_FACTURA = Map.of("PENDIENTE", "Pendiente", "CERTIFICADA",
            "Certificada", "CONTINGENCIA", "En contingencia", "RECHAZADA", "Rechazada", "ANULADA", "Anulada");

    private TablasDeReportes() {
    }

    public static TablaReporte de(CodigoReporte codigo, Object reporte) {
        return switch (codigo) {
            case VENTAS_PERIODO -> ventasPeriodo((ReporteVentasPeriodo) reporte);
            case VENTAS_CAJERO -> ventasPorCajero((ReporteVentasPorCajero) reporte);
            case DETALLE_CAJERO -> detalleCajero((DetalleVentasCajero) reporte);
            case PRODUCTOS -> productos((ReporteProductosMasVendidos) reporte);
            case TARJETAS -> tarjetas((ReporteTarjetas) reporte);
            case DETALLE_TARJETA -> detalleTarjeta((DetallePagosTarjeta) reporte);
            case IVA -> iva((ReporteIva) reporte);
            case DETALLE_IVA -> detalleIva((DetalleFacturasIva) reporte);
            case CIERRES -> cierres((ReporteCierres) reporte);
            case ANULADAS -> anuladas((ReporteAnuladas) reporte);
        };
    }

    private static TablaReporte ventasPeriodo(ReporteVentasPeriodo reporte) {
        return tabla(CodigoReporte.VENTAS_PERIODO, reporte.encabezado(), List.of(),
                columnas("Período", TEXTO, "Ventas", ENTERO, "Subtotal", DINERO, "IVA", DINERO, "Total vendido", DINERO,
                        "Ticket promedio", DINERO),
                reporte.filas().stream().map(TablasDeReportes::filaPeriodo).toList(),
                List.of(filaPeriodo(reporte.totales())),
                List.of("Las facturas anuladas no se suman a las ventas."));
    }

    private static List<Object> filaPeriodo(ReporteVentasPeriodo.Fila fila) {
        return fila(fila.periodo(), fila.cantidadVentas(), fila.subtotal(), fila.iva(), fila.total(),
                fila.ticketPromedio());
    }

    private static TablaReporte ventasPorCajero(ReporteVentasPorCajero reporte) {
        return tabla(CodigoReporte.VENTAS_CAJERO, reporte.encabezado(), List.of(),
                columnas("Cajero", TEXTO, "Ventas", ENTERO, "Total vendido", DINERO, "Canceladas", ENTERO,
                        "Líneas eliminadas", ENTERO, "Facturas anuladas", ENTERO, "Monto anulado", DINERO),
                reporte.filas().stream().map(TablasDeReportes::filaCajero).toList(),
                List.of(filaCajero(reporte.totales())),
                List.of("Ventas y total sin las facturas anuladas. Las canceladas cuentan por la fecha en que se "
                        + "iniciaron; las líneas eliminadas, por la fecha en que se eliminaron."));
    }

    private static List<Object> filaCajero(ReporteVentasPorCajero.Fila fila) {
        return fila(fila.cajero(), fila.cantidadVentas(), fila.totalVendido(), fila.ventasCanceladas(),
                fila.lineasEliminadas(), fila.facturasAnuladas(), fila.montoAnulado());
    }

    private static TablaReporte productos(ReporteProductosMasVendidos reporte) {
        return tabla(CodigoReporte.PRODUCTOS, reporte.encabezado(),
                List.of(new Dato("Productos distintos vendidos en el período", reporte.productosVendidos(), ENTERO)),
                columnas("N.º", ENTERO, "Código", TEXTO, "Descripción", TEXTO, "Cantidad vendida", ENTERO,
                        "Monto vendido", DINERO),
                reporte.filas().stream().map(TablasDeReportes::filaProducto).toList(),
                List.of(filaProducto(reporte.totales())),
                List.of("Líneas vigentes de las ventas finalizadas en el período, sin las de facturas anuladas. El "
                        + "monto incluye IVA; el total suma los productos mostrados."));
    }

    private static List<Object> filaProducto(ReporteProductosMasVendidos.Fila fila) {
        return fila(fila.posicion() == null ? null : fila.posicion().longValue(), fila.codigo(),
                fila.descripcion(), fila.cantidad(), fila.monto());
    }

    private static TablaReporte detalleCajero(DetalleVentasCajero detalle) {
        return tabla(CodigoReporte.DETALLE_CAJERO, detalle.encabezado(), List.of(),
                columnas("Venta N.º", ENTERO, "Fecha", FECHA_HORA, "Caja", TEXTO, "Estado", TEXTO, "Total", DINERO,
                        "Factura", TEXTO, "Líneas eliminadas", ENTERO),
                detalle.ventas().stream().map(venta -> fila(venta.numero(), venta.fecha(), venta.caja(),
                        venta.estado().equals("CANCELADA") ? "Cancelada" : "Finalizada", venta.total(),
                        factura(venta.factura(), venta.estadoFactura()), venta.lineasEliminadas())).toList(),
                List.of(), List.of());
    }

    private static String factura(String numero, String estado) {
        if (estado == null) {
            return null;
        }
        String texto = ESTADOS_FACTURA.getOrDefault(estado, estado);
        return numero == null ? texto : numero + " · " + texto;
    }

    private static TablaReporte tarjetas(ReporteTarjetas reporte) {
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 0; i < reporte.filas().size(); i++) {
            filas.add(filaTarjeta(reporte.filas().get(i), reporte.anulados().get(i)));
        }
        return tabla(CodigoReporte.TARJETAS, reporte.encabezado(), List.of(),
                columnas("Tipo de tarjeta", TEXTO, "Transacciones", ENTERO, "Total recibido", DINERO,
                        "Anuladas (transacciones)", ENTERO, "Anuladas (monto)", DINERO),
                filas, List.of(filaTarjeta(reporte.total(), reporte.totalAnulado())),
                List.of("Pagos de las ventas finalizadas en el período. Los de facturas anuladas se muestran aparte y "
                        + "no se suman al total."));
    }

    private static List<Object> filaTarjeta(ReporteTarjetas.Fila fila, ReporteTarjetas.Fila anulada) {
        return fila(fila.etiqueta(), fila.transacciones(), fila.monto(), anulada.transacciones(), anulada.monto());
    }

    private static TablaReporte detalleTarjeta(DetallePagosTarjeta detalle) {
        return tabla(CodigoReporte.DETALLE_TARJETA, detalle.encabezado(), List.of(),
                columnas("Fecha", FECHA_HORA, "Venta N.º", ENTERO, "Caja", TEXTO, "Cajero", TEXTO, "Autorización", TEXTO,
                        "Tarjeta", TEXTO, "Monto", DINERO, "Factura anulada", TEXTO),
                detalle.pagos().stream().map(pago -> fila(pago.fecha(), pago.ventaNumero(), pago.caja(), pago.cajero(),
                        pago.autorizacion(), pago.ultimos4() == null ? null : "**** " + pago.ultimos4(), pago.monto(),
                        pago.anulada() ? "Sí" : "No")).toList(),
                List.of(), List.of());
    }

    private static TablaReporte iva(ReporteIva reporte) {
        List<Dato> resumen = reporte.sinCertificar().facturas() == 0 ? List.of() : List.of(
                new Dato("Facturas del período sin certificar (no incluidas)", reporte.sinCertificar().facturas(),
                        ENTERO),
                new Dato("Monto sin certificar", reporte.sinCertificar().total(), DINERO));
        return tabla(CodigoReporte.IVA, reporte.encabezado(), resumen,
                columnas("Día", FECHA, "Facturas", ENTERO, "Base imponible", DINERO, "IVA", DINERO, "Total vendido",
                        DINERO),
                reporte.dias().stream().map(dia -> fila(dia.fecha(), dia.facturas(), dia.baseImponible(), dia.iva(),
                        dia.total())).toList(),
                List.of(fila("Total", reporte.total().facturas(), reporte.total().baseImponible(),
                        reporte.total().iva(), reporte.total().total())),
                List.of("Solo facturas certificadas, por su fecha de emisión; las anuladas no se incluyen."));
    }

    private static TablaReporte detalleIva(DetalleFacturasIva detalle) {
        return tabla(CodigoReporte.DETALLE_IVA, detalle.encabezado(), List.of(),
                columnas("Emitida", FECHA_HORA, "Serie y número", TEXTO, "Autorización", TEXTO, "NIT o CUI", TEXTO,
                        "Cliente", TEXTO, "Caja", TEXTO, "Base imponible", DINERO, "IVA", DINERO, "Total", DINERO),
                detalle.facturas().stream().map(factura -> fila(factura.emitidaEn(),
                        factura.serie() + "-" + factura.numero(), factura.autorizacion(), factura.receptorId(),
                        factura.receptorNombre(), factura.caja(), factura.baseImponible(), factura.iva(),
                        factura.total())).toList(),
                List.of(), List.of());
    }

    private static TablaReporte cierres(ReporteCierres reporte) {
        return tabla(CodigoReporte.CIERRES, reporte.encabezado(),
                List.of(new Dato("Cierres", (long) reporte.cierres().size(), ENTERO),
                        new Dato("Con diferencia", reporte.cierresConDiferencia(), ENTERO),
                        new Dato("Total de faltantes", reporte.totalFaltantes(), DINERO),
                        new Dato("Total de sobrantes", reporte.totalSobrantes(), DINERO)),
                columnas("Cerrado", FECHA_HORA, "Caja", TEXTO, "Cajero", TEXTO, "Registrado con tarjeta", DINERO,
                        "Cierre de lote", DINERO, "Diferencia", DINERO, "Justificación", TEXTO),
                reporte.cierres().stream().map(cierre -> fila(cierre.cerradoEn(), cierre.caja(), cierre.cajero(),
                        cierre.totalRegistrado(), cierre.totalLote(), cierre.diferencia(), cierre.justificacion()))
                        .toList(),
                List.of(),
                List.of("La diferencia es el cierre de lote menos lo registrado en el POS: negativa es un faltante."));
    }

    private static TablaReporte anuladas(ReporteAnuladas reporte) {
        return tabla(CodigoReporte.ANULADAS, reporte.encabezado(), List.of(),
                columnas("Emitida", FECHA_HORA, "Anulada", FECHA_HORA, "Serie y número", TEXTO, "Venta N.º", ENTERO,
                        "Monto", DINERO, "Motivo", TEXTO, "Cajero que emitió", TEXTO, "Anulada por", TEXTO),
                reporte.facturas().stream().map(factura -> fila(factura.emitidaEn(), factura.anuladaEn(),
                        factura.serie() + "-" + factura.numero(), factura.ventaNumero(), factura.monto(),
                        factura.motivo(), factura.cajero(), factura.anuladaPor())).toList(),
                List.of(fila(reporte.cantidad() == 1 ? "1 factura anulada" : reporte.cantidad() + " facturas anuladas",
                        null, null, null, reporte.monto(), null, null, null)),
                List.of());
    }

    private static TablaReporte tabla(CodigoReporte codigo, EncabezadoReporte encabezado, List<Dato> resumen,
                                      List<Columna> columnas, List<List<Object>> filas, List<List<Object>> pie,
                                      List<String> notas) {
        return new TablaReporte(codigo, encabezado, DatosEmpresa.SIN_CONFIGURAR, resumen, columnas, filas, pie, notas);
    }

    /** Pares de título y tipo: {@code columnas("Ventas", ENTERO, "Total", DINERO)}. */
    private static List<Columna> columnas(Object... pares) {
        List<Columna> columnas = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            columnas.add(new Columna((String) pares[i], (Tipo) pares[i + 1]));
        }
        return List.copyOf(columnas);
    }

    /** Una fila; admite valores nulos (celda vacía). */
    private static List<Object> fila(Object... valores) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(Arrays.asList(valores)));
    }
}
