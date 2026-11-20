package gt.supermercado.pos.reportes.dominio;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * El encabezado común de los reportes: qué reporte es, con qué filtros se generó (HU-10.01 crit. 4), cuándo y quién.
 * Los archivos exportados (HU-10.13 y HU-10.14) llevan el mismo encabezado.
 */
public record EncabezadoReporte(String titulo, List<FiltroAplicado> filtros, OffsetDateTime generadoEn,
                                String generadoPor) {}
