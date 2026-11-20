package gt.supermercado.pos.reportes.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.reportes.dominio.Agrupacion;
import gt.supermercado.pos.reportes.dominio.CodigoReporte;
import gt.supermercado.pos.reportes.dominio.DetalleFacturasIva;
import gt.supermercado.pos.reportes.dominio.DetallePagosTarjeta;
import gt.supermercado.pos.reportes.dominio.DetalleVentasCajero;
import gt.supermercado.pos.reportes.dominio.EncabezadoReporte;
import gt.supermercado.pos.reportes.dominio.ExportadorReportes;
import gt.supermercado.pos.reportes.dominio.FiltroAplicado;
import gt.supermercado.pos.reportes.dominio.FiltrosReporte;
import gt.supermercado.pos.reportes.dominio.FormatoExportacion;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro.Opcion;
import gt.supermercado.pos.reportes.dominio.ParametrosReporte;
import gt.supermercado.pos.reportes.dominio.RangoRapido;
import gt.supermercado.pos.reportes.dominio.ReporteAnuladas;
import gt.supermercado.pos.reportes.dominio.ReporteCierres;
import gt.supermercado.pos.reportes.dominio.ReporteIva;
import gt.supermercado.pos.reportes.dominio.ReporteTarjetas;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPeriodo;
import gt.supermercado.pos.reportes.dominio.OrdenProductos;
import gt.supermercado.pos.reportes.dominio.ReporteProductosMasVendidos;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCaja;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorHora;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCajero;
import gt.supermercado.pos.reportes.dominio.RepositorioReportes;
import gt.supermercado.pos.reportes.dominio.TablasDeReportes;
import gt.supermercado.pos.reportes.dominio.TipoTarjetaReporte;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Genera los reportes de EP-10 con los filtros comunes (HU-10.01) y los exporta a PDF o Excel (HU-10.13 y
 * HU-10.14). Cada generación y cada exportación quedan en la bitácora (HU-10.15 crit. 3), también las que se
 * rechazan por filtros inválidos. El permiso lo exige el controlador: todos los reportes usan
 * {@code REPORTE_CONSULTAR} (Administrador y Supervisor; los cajeros no, crit. 1 y 2; decisión de Sergio: un permiso
 * común).
 *
 * <p>La pantalla y el archivo se arman con el mismo {@link #armar} para que tengan los mismos datos, el mismo orden y
 * los mismos totales (HU-10.13 crit. 3).
 */
@Service
public class GeneracionReportes {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RepositorioReportes repositorio;
    private final ExportadorReportes exportador;
    private final Bitacora bitacora;
    private final Clock reloj;

    @Autowired
    public GeneracionReportes(RepositorioReportes repositorio, ExportadorReportes exportador, Bitacora bitacora) {
        this(repositorio, exportador, bitacora, Clock.system(GUATEMALA));
    }

    GeneracionReportes(RepositorioReportes repositorio, ExportadorReportes exportador, Bitacora bitacora,
                       Clock reloj) {
        this.repositorio = repositorio;
        this.exportador = exportador;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** HU-10.01: las opciones de los filtros, con los rangos rápidos calculados con la fecha del servidor (crit. 3). */
    public OpcionesDeFiltro opciones() {
        LocalDate hoy = LocalDate.now(reloj.withZone(GUATEMALA));
        List<OpcionesDeFiltro.Rango> rangos = Arrays.stream(RangoRapido.values()).map(rango -> {
            RangoRapido.RangoDeFechas fechas = rango.para(hoy);
            return new OpcionesDeFiltro.Rango(rango.name(), rango.etiqueta(), fechas.desde(), fechas.hasta());
        }).toList();
        return new OpcionesDeFiltro(hoy, rangos, repositorio.cajas(), repositorio.cajeros(),
                Arrays.stream(TipoTarjetaReporte.values()).map(t -> new Opcion(t.name(), t.etiqueta())).toList(),
                Arrays.stream(Agrupacion.values()).map(a -> new Opcion(a.name(), a.etiqueta())).toList(),
                repositorio.anuladores());
    }

    /** HU-10.02: ventas por período, filtradas por fechas, caja y cajero, agrupadas por día (por omisión), semana o mes. */
    public ReporteVentasPeriodo ventasPorPeriodo(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                                 Agrupacion agrupacion, SesionActiva sesion) {
        return (ReporteVentasPeriodo) generar(CodigoReporte.VENTAS_PERIODO,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conAgrupacion(agrupacion), sesion);
    }

    /** HU-10.03 crit. 1: el resumen por cajero, filtrado por fechas, caja y cajero. */
    public ReporteVentasPorCajero ventasPorCajero(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                                  SesionActiva sesion) {
        return (ReporteVentasPorCajero) generar(CodigoReporte.VENTAS_CAJERO,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId), sesion);
    }

    /** HU-10.04: ventas y turnos por caja, para comparar las cajas en el mismo período. */
    public ReporteVentasPorCaja ventasPorCaja(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                              SesionActiva sesion) {
        return (ReporteVentasPorCaja) generar(CodigoReporte.VENTAS_CAJA,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId), sesion);
    }

    /** HU-10.11: ventas por hora del día; con un período, también el promedio por día de cada hora. */
    public ReporteVentasPorHora ventasPorHora(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                              SesionActiva sesion) {
        return (ReporteVentasPorHora) generar(CodigoReporte.VENTAS_HORA,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId), sesion);
    }

    /** HU-10.05: los productos más vendidos, por cantidad (por omisión) o por monto, limitados a {@code limite}. */
    public ReporteProductosMasVendidos productosMasVendidos(LocalDate desde, LocalDate hasta, Long cajaId,
                                                            Long cajeroId, OrdenProductos orden, Integer limite,
                                                            SesionActiva sesion) {
        return (ReporteProductosMasVendidos) generar(CodigoReporte.PRODUCTOS,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conProductos(orden, limite), sesion);
    }

    /** HU-10.03 crit. 2: las ventas de un cajero; su generación también queda en la bitácora. */
    public DetalleVentasCajero detalleDeCajero(long cajeroId, LocalDate desde, LocalDate hasta, Long cajaId,
                                               SesionActiva sesion) {
        return (DetalleVentasCajero) generar(CodigoReporte.DETALLE_CAJERO,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId), sesion);
    }

    /** HU-10.06 crit. 1 y 2: ingresos por tipo de tarjeta, con los pagos de facturas anuladas aparte. */
    public ReporteTarjetas ingresosPorTarjeta(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                              TipoTarjetaReporte tipo, SesionActiva sesion) {
        return (ReporteTarjetas) generar(CodigoReporte.TARJETAS,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conTarjeta(tipo), sesion);
    }

    /** HU-10.06 crit. 3: las transacciones de un tipo de tarjeta, con su autorización. */
    public DetallePagosTarjeta detalleDeTarjeta(TipoTarjetaReporte tipo, LocalDate desde, LocalDate hasta,
                                                Long cajaId, Long cajeroId, SesionActiva sesion) {
        return (DetallePagosTarjeta) generar(CodigoReporte.DETALLE_TARJETA,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conTarjeta(tipo), sesion);
    }

    /** HU-10.07: el IVA de las facturas certificadas, por día, y las que faltan por certificar. */
    public ReporteIva ivaCobrado(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId, SesionActiva sesion) {
        return (ReporteIva) generar(CodigoReporte.IVA, ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId),
                sesion);
    }

    /** HU-10.07 crit. 3: el detalle por factura. */
    public DetalleFacturasIva facturasDelIva(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                             SesionActiva sesion) {
        return (DetalleFacturasIva) generar(CodigoReporte.DETALLE_IVA,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId), sesion);
    }

    /** HU-10.08: los cierres de caja del período, con el total de faltantes y sobrantes. */
    public ReporteCierres cierresDeCaja(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                        boolean soloConDiferencias, SesionActiva sesion) {
        return (ReporteCierres) generar(CodigoReporte.CIERRES,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conSoloDiferencias(soloConDiferencias),
                sesion);
    }

    /** HU-10.09: las facturas anuladas en el período, filtradas también por quien las anuló. */
    public ReporteAnuladas facturasAnuladas(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                            Long anuladaPorId, SesionActiva sesion) {
        return (ReporteAnuladas) generar(CodigoReporte.ANULADAS,
                ParametrosReporte.comunes(desde, hasta, cajaId, cajeroId).conAnuladaPor(anuladaPorId), sesion);
    }

    /** Un archivo exportado, listo para descargar. */
    public record ArchivoReporte(String nombre, String tipoContenido, byte[] contenido) {}

    /**
     * HU-10.13 y HU-10.14: el reporte con los filtros pedidos, como PDF o Excel. El archivo lleva el mismo encabezado
     * que la pantalla, más los datos de la empresa (HU-10.13 crit. 2), y queda en la bitácora como
     * {@code REPORTE_EXPORTADO} con su formato (HU-10.15 crit. 3).
     */
    public ArchivoReporte exportar(CodigoReporte codigo, ParametrosReporte parametros, FormatoExportacion formato,
                                   SesionActiva sesion) {
        Generado generado = armarRegistrando(codigo, parametros, sesion, AccionSensible.REPORTE_EXPORTADO, formato);
        byte[] contenido = exportador.exportar(
                TablasDeReportes.de(codigo, generado.reporte()).conEmpresa(repositorio.empresa()), formato);
        registrarExito(codigo, generado.encabezado(), sesion, AccionSensible.REPORTE_EXPORTADO, formato);
        String nombre = "%s_%s_%s.%s".formatted(codigo.archivo(), parametros.desde(), parametros.hasta(),
                formato.extension());
        return new ArchivoReporte(nombre, formato.tipoContenido(), contenido);
    }

    private record Generado(Object reporte, EncabezadoReporte encabezado) {}

    private Object generar(CodigoReporte codigo, ParametrosReporte parametros, SesionActiva sesion) {
        Generado generado = armarRegistrando(codigo, parametros, sesion, AccionSensible.REPORTE_GENERADO, null);
        registrarExito(codigo, generado.encabezado(), sesion, AccionSensible.REPORTE_GENERADO, null);
        return generado.reporte();
    }

    /** Arma el reporte; si los filtros no son válidos, el intento queda en la bitácora y se propaga el error. */
    private Generado armarRegistrando(CodigoReporte codigo, ParametrosReporte parametros, SesionActiva sesion,
                                      AccionSensible accion, FormatoExportacion formato) {
        try {
            return armar(codigo, parametros, sesion);
        } catch (NegocioException error) {
            Map<String, Object> pedidos = new LinkedHashMap<>();
            pedidos.put("reporte", codigo.titulo());
            pedidos.put("formato", formato == null ? null : formato.name());
            pedidos.put("desde", parametros.desde() == null ? null : parametros.desde().toString());
            pedidos.put("hasta", parametros.hasta() == null ? null : parametros.hasta().toString());
            pedidos.put("cajaId", parametros.cajaId());
            pedidos.put("cajeroId", parametros.cajeroId());
            pedidos.values().removeIf(Objects::isNull);
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), accion, false,
                    formato == null
                            ? "Reporte \"%s\" rechazado: %s".formatted(codigo.titulo(), error.getMessage())
                            : "Exportación a %s del reporte \"%s\" rechazada: %s".formatted(formato.name(),
                                    codigo.titulo(), error.getMessage()),
                    "reporte", null, pedidos));
            throw error;
        }
    }

    private void registrarExito(CodigoReporte codigo, EncabezadoReporte encabezado, SesionActiva sesion,
                                AccionSensible accion, FormatoExportacion formato) {
        List<FiltroAplicado> filtros = encabezado.filtros();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("reporte", codigo.titulo());
        if (formato != null) {
            detalle.put("formato", formato.name());
        }
        filtros.forEach(filtro -> detalle.put(filtro.etiqueta(), filtro.valor()));
        String aplicados = filtros.stream().map(f -> f.etiqueta() + ": " + f.valor()).collect(Collectors.joining("; "));
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), accion, true,
                formato == null ? "Reporte \"%s\" generado (%s)".formatted(codigo.titulo(), aplicados)
                        : "Reporte \"%s\" exportado a %s (%s)".formatted(codigo.titulo(), formato.name(), aplicados),
                "reporte", null, detalle));
    }

    /** Valida los filtros y arma el reporte pedido, con su encabezado (HU-10.01 crit. 4). */
    private Generado armar(CodigoReporte codigo, ParametrosReporte p, SesionActiva sesion) {
        FiltrosReporte filtros = new FiltrosReporte(p.desde(), p.hasta(), p.cajaId(), p.cajeroId(),
                codigo == CodigoReporte.TARJETAS || codigo == CodigoReporte.DETALLE_TARJETA ? p.tipoTarjeta() : null);
        List<FiltroAplicado> aplicados = new ArrayList<>(aplicados(filtros));
        switch (codigo) {
            case VENTAS_PERIODO -> aplicados.add(new FiltroAplicado("Agrupación", agrupacion(p).etiqueta()));
            case DETALLE_CAJERO -> exigir(p.cajeroId() != null, "Indique el cajero del detalle.");
            case PRODUCTOS -> {
                aplicados.add(new FiltroAplicado("Ordenado por", ordenProductos(p).etiqueta()));
                aplicados.add(new FiltroAplicado("Mostrar",
                        "Los " + ReporteProductosMasVendidos.limiteValido(p.limite()) + " primeros"));
            }
            case DETALLE_TARJETA -> exigir(p.tipoTarjeta() != null, "Indique el tipo de tarjeta del detalle.");
            case CIERRES -> {
                if (p.soloConDiferencias()) {
                    aplicados.add(new FiltroAplicado("Cierres", "Solo con diferencias"));
                }
            }
            case ANULADAS -> {
                aplicados.set(0, new FiltroAplicado("Anuladas", aplicados.get(0).valor()));
                aplicados.add(new FiltroAplicado("Anulada por", p.anuladaPorId() == null ? "Cualquiera"
                        : repositorio.nombreCajero(p.anuladaPorId()).orElseThrow(() -> new NegocioException(
                                "FILTRO_ANULADOR_INEXISTENTE", "El usuario que anuló no existe."))));
            }
            default -> {
            }
        }
        EncabezadoReporte encabezado = new EncabezadoReporte(codigo.titulo(), List.copyOf(aplicados),
                OffsetDateTime.now(reloj.withZone(GUATEMALA)), sesion.nombreCompleto() + " (" + sesion.username() + ")");
        Object reporte = switch (codigo) {
            case VENTAS_PERIODO -> ReporteVentasPeriodo.de(encabezado, filtros, agrupacion(p),
                    repositorio.ventasPorDia(filtros));
            case VENTAS_CAJERO -> ReporteVentasPorCajero.de(encabezado, repositorio.ventasPorCajero(filtros));
            case DETALLE_CAJERO -> new DetalleVentasCajero(encabezado, repositorio.ventasDeCajero(filtros));
            case VENTAS_CAJA -> ReporteVentasPorCaja.de(encabezado, repositorio.ventasPorCaja(filtros));
            case VENTAS_HORA -> ReporteVentasPorHora.de(encabezado, filtros, repositorio.ventasPorHora(filtros));
            case PRODUCTOS -> {
                int limite = ReporteProductosMasVendidos.limiteValido(p.limite());
                yield ReporteProductosMasVendidos.de(encabezado, ordenProductos(p), limite,
                        repositorio.productosMasVendidos(filtros, ordenProductos(p), limite));
            }
            case TARJETAS -> ReporteTarjetas.de(encabezado, filtros.tipoTarjeta(), repositorio.pagosPorTipo(filtros));
            case DETALLE_TARJETA -> new DetallePagosTarjeta(encabezado, repositorio.pagosDeTarjeta(filtros));
            case IVA -> ReporteIva.de(encabezado, repositorio.ivaPorDia(filtros), repositorio.sinCertificar(filtros));
            case DETALLE_IVA -> new DetalleFacturasIva(encabezado, repositorio.facturasIva(filtros));
            case CIERRES -> ReporteCierres.de(encabezado, repositorio.cierres(filtros, p.soloConDiferencias()));
            case ANULADAS -> ReporteAnuladas.de(encabezado, repositorio.anuladas(filtros, p.anuladaPorId()));
        };
        return new Generado(reporte, encabezado);
    }

    private static Agrupacion agrupacion(ParametrosReporte p) {
        return p.agrupacion() == null ? Agrupacion.DIA : p.agrupacion();
    }

    private static OrdenProductos ordenProductos(ParametrosReporte p) {
        return p.ordenProductos() == null ? OrdenProductos.CANTIDAD : p.ordenProductos();
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new NegocioException("FILTRO_DETALLE_INCOMPLETO", mensaje);
        }
    }

    /** HU-10.01 crit. 4: los filtros como se muestran en el encabezado. Una caja o un cajero inexistente se rechaza. */
    List<FiltroAplicado> aplicados(FiltrosReporte filtros) {
        List<FiltroAplicado> aplicados = new ArrayList<>();
        aplicados.add(new FiltroAplicado("Período", filtros.desde().equals(filtros.hasta())
                ? FECHA.format(filtros.desde())
                : "Del " + FECHA.format(filtros.desde()) + " al " + FECHA.format(filtros.hasta())));
        aplicados.add(new FiltroAplicado("Caja", filtros.cajaId() == null ? "Todas"
                : repositorio.nombreCaja(filtros.cajaId()).orElseThrow(() -> new NegocioException(
                        "FILTRO_CAJA_INEXISTENTE", "La caja seleccionada no existe."))));
        aplicados.add(new FiltroAplicado("Cajero", filtros.cajeroId() == null ? "Todos"
                : repositorio.nombreCajero(filtros.cajeroId()).orElseThrow(() -> new NegocioException(
                        "FILTRO_CAJERO_INEXISTENTE", "El cajero seleccionado no existe."))));
        if (filtros.tipoTarjeta() != null) {
            aplicados.add(new FiltroAplicado("Tipo de tarjeta", filtros.tipoTarjeta().etiqueta()));
        }
        return aplicados;
    }
}
