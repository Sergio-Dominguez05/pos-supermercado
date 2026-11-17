package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.configuracion.dominio.Impresora;

import java.time.OffsetDateTime;

/**
 * HU-12.08 crit. 2: la página de prueba. Muestra el ancho configurado (una regla de lado a lado), las tildes y la ñ,
 * para comprobar que el papel y la página de códigos son los correctos.
 */
public final class PaginaDePrueba {

    private PaginaDePrueba() {
    }

    public static Ticket armar(Impresora impresora, OffsetDateTime ahora) {
        Ticket ticket = Ticket.paraPapel(impresora.anchoMm());
        StringBuilder regla = new StringBuilder();
        for (int columna = 1; columna <= ticket.columnas(); columna++) {
            regla.append(columna % 10);
        }
        return ticket.titulo("PÁGINA DE PRUEBA")
                .centrado(impresora.nombre())
                .separador()
                .dato("Dirección", impresora.host() + ":" + impresora.puerto())
                .dato("Papel", impresora.anchoMm() + " mm (" + ticket.columnas() + " columnas)")
                .dato("Copias de factura", String.valueOf(impresora.copiasFactura()))
                .dato("Copias de cierre", String.valueOf(impresora.copiasCierre()))
                .dato("Fecha", TicketFactura.fecha(ahora))
                .separador()
                .texto(regla.toString())
                .texto("Tildes y eñes: áéíóú ÁÉÍÓÚ ñÑ ü ¿? ¡! N.º")
                .separador()
                .centrado("Si esta página se ve completa, la impresora está lista.");
    }
}
