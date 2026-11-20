package gt.supermercado.pos.reportes.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-10.01: lo que la pantalla de reportes necesita para armar los filtros: las cajas, los cajeros, los tipos de
 * tarjeta, las agrupaciones y los rangos rápidos ya calculados con la fecha del servidor (crit. 3).
 */
public record OpcionesDeFiltro(LocalDate hoy, List<Rango> rangos, List<Opcion> cajas, List<Opcion> cajeros,
                               List<Opcion> tiposTarjeta, List<Opcion> agrupaciones) {

    public record Rango(String codigo, String etiqueta, LocalDate desde, LocalDate hasta) {}

    /** Una opción de una lista: el valor que se envía y el texto que se muestra. */
    public record Opcion(String valor, String etiqueta) {}
}
