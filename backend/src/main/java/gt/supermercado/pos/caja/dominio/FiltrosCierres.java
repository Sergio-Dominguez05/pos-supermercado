package gt.supermercado.pos.caja.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;

import java.time.LocalDate;

/**
 * HU-09.13 crit. 2: los filtros de la consulta de cierres. Todos son opcionales; las fechas son días de cierre en hora
 * de Guatemala. La pantalla propone los últimos 7 días (decisión de Sergio).
 *
 * @param soloConDiferencias solo los cierres cuyo cierre de lote no coincidió con lo registrado
 * @param pagina             desde 0
 */
public record FiltrosCierres(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                             boolean soloConDiferencias, int pagina) {

    public static final int TAMANO_PAGINA = 50;

    public FiltrosCierres {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new NegocioException("FILTRO_FECHAS_INVERTIDAS",
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (pagina < 0) {
            pagina = 0;
        }
    }
}
