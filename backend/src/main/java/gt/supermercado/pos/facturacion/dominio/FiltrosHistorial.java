package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;

import java.time.LocalDate;

/**
 * HU-08.13 crit. 2 y 3: los filtros del historial de facturas. Todos son opcionales: sin ninguno se ven las más
 * recientes. Las fechas son días de emisión en hora de Guatemala.
 *
 * @param cliente texto que se busca en el NIT, CUI o nombre del cliente
 * @param buscar  número o serie de la factura, número de autorización, número de acceso o número de venta
 * @param pagina  desde 0
 */
public record FiltrosHistorial(LocalDate desde, LocalDate hasta, EstadoFactura estado, Long cajaId, Long cajeroId,
                               String cliente, String buscar, int pagina) {

    public static final int TAMANO_PAGINA = 50;

    public FiltrosHistorial {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new NegocioException("FILTRO_FECHAS_INVERTIDAS",
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (pagina < 0) {
            pagina = 0;
        }
        cliente = limpio(cliente);
        buscar = limpio(buscar);
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
