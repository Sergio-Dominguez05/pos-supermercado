package gt.supermercado.pos.bitacora.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;

import java.time.LocalDate;

/**
 * HU-11.07 crit. 1 a 3: los filtros de la consulta de la bitácora; todos opcionales y combinables. Las fechas son días
 * en hora de Guatemala (la pantalla abre con hoy, decisión de Sergio).
 *
 * @param accion    el código de la acción ({@link AccionSensible}), o {@code null}
 * @param exitoso   el resultado: {@code true} exitoso, {@code false} fallido, {@code null} los dos
 * @param documento crit. 2: número de venta, serie y número de factura (A1-77) o número de cierre
 * @param pagina    desde 0
 */
public record FiltrosBitacora(LocalDate desde, LocalDate hasta, Long usuarioId, Long cajaId, String modulo,
                              String accion, Boolean exitoso, String documento, int pagina) {

    public static final int TAMANO_PAGINA = 50;

    public FiltrosBitacora {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new NegocioException("FILTRO_FECHAS_INVERTIDAS",
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }
        modulo = limpio(modulo);
        accion = limpio(accion);
        documento = limpio(documento);
        if (documento != null && documento.length() > 40) {
            throw new NegocioException("DOCUMENTO_INVALIDO", "El número de documento puede tener hasta 40 caracteres.");
        }
        if (pagina < 0) {
            pagina = 0;
        }
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
