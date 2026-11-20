package gt.supermercado.pos.reportes.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.reportes.dominio.Agrupacion;
import gt.supermercado.pos.reportes.dominio.EncabezadoReporte;
import gt.supermercado.pos.reportes.dominio.FiltroAplicado;
import gt.supermercado.pos.reportes.dominio.FiltrosReporte;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro.Opcion;
import gt.supermercado.pos.reportes.dominio.RangoRapido;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPeriodo;
import gt.supermercado.pos.reportes.dominio.RepositorioReportes;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Genera los reportes de EP-10 con los filtros comunes (HU-10.01) y deja cada generación en la bitácora
 * (HU-10.15 crit. 3), también la que se rechaza por filtros inválidos. El permiso lo exige el controlador: todos los
 * reportes usan {@code REPORTE_CONSULTAR} (Administrador y Supervisor; los cajeros no, crit. 1 y 2; decisión de
 * Sergio: un permiso común).
 */
@Service
public class GeneracionReportes {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RepositorioReportes repositorio;
    private final Bitacora bitacora;
    private final Clock reloj;

    @Autowired
    public GeneracionReportes(RepositorioReportes repositorio, Bitacora bitacora) {
        this(repositorio, bitacora, Clock.system(GUATEMALA));
    }

    GeneracionReportes(RepositorioReportes repositorio, Bitacora bitacora, Clock reloj) {
        this.repositorio = repositorio;
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
                Arrays.stream(Agrupacion.values()).map(a -> new Opcion(a.name(), a.etiqueta())).toList());
    }

    /** HU-10.02: ventas por período, filtradas por fechas, caja y cajero, agrupadas por día (por omisión), semana o mes. */
    public ReporteVentasPeriodo ventasPorPeriodo(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                                 Agrupacion agrupacion, SesionActiva sesion) {
        Agrupacion grupo = agrupacion == null ? Agrupacion.DIA : agrupacion;
        return generar(ReporteVentasPeriodo.TITULO, sesion, desde, hasta, cajaId, cajeroId, () -> {
            FiltrosReporte filtros = new FiltrosReporte(desde, hasta, cajaId, cajeroId, null);
            List<FiltroAplicado> aplicados = new ArrayList<>(aplicados(filtros));
            aplicados.add(new FiltroAplicado("Agrupación", grupo.etiqueta()));
            EncabezadoReporte encabezado = encabezado(ReporteVentasPeriodo.TITULO, aplicados, sesion);
            return new Generado<>(ReporteVentasPeriodo.de(encabezado, filtros, grupo, repositorio.ventasPorDia(filtros)),
                    encabezado);
        });
    }

    private record Generado<T>(T reporte, EncabezadoReporte encabezado) {}

    /**
     * Valida los filtros, arma el reporte y lo registra en la bitácora con los filtros aplicados (HU-10.15 crit. 3).
     * Si los filtros no son válidos, el intento también queda registrado.
     */
    private <T> T generar(String titulo, SesionActiva sesion, LocalDate desde, LocalDate hasta, Long cajaId,
                          Long cajeroId, Supplier<Generado<T>> armar) {
        Generado<T> generado;
        try {
            generado = armar.get();
        } catch (NegocioException error) {
            Map<String, Object> pedidos = new LinkedHashMap<>();
            pedidos.put("reporte", titulo);
            pedidos.put("desde", desde == null ? null : desde.toString());
            pedidos.put("hasta", hasta == null ? null : hasta.toString());
            pedidos.put("cajaId", cajaId);
            pedidos.put("cajeroId", cajeroId);
            pedidos.values().removeIf(java.util.Objects::isNull);
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.REPORTE_GENERADO, false,
                    "Reporte \"%s\" rechazado: %s".formatted(titulo, error.getMessage()), "reporte", null, pedidos));
            throw error;
        }
        List<FiltroAplicado> filtros = generado.encabezado().filtros();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("reporte", titulo);
        filtros.forEach(filtro -> detalle.put(filtro.etiqueta(), filtro.valor()));
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.REPORTE_GENERADO, true, "Reporte \"%s\" generado (%s)".formatted(titulo,
                        filtros.stream().map(f -> f.etiqueta() + ": " + f.valor()).collect(Collectors.joining("; "))),
                "reporte", null, detalle));
        return generado.reporte();
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

    private EncabezadoReporte encabezado(String titulo, List<FiltroAplicado> filtros, SesionActiva sesion) {
        return new EncabezadoReporte(titulo, List.copyOf(filtros), OffsetDateTime.now(reloj.withZone(GUATEMALA)),
                sesion.nombreCompleto() + " (" + sesion.username() + ")");
    }
}
