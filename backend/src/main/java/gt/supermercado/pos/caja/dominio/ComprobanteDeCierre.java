package gt.supermercado.pos.caja.dominio;

/** HU-09.10 crit. 1: el comprobante del cierre: el cierre guardado y lo que se cobró en el turno. */
public record ComprobanteDeCierre(CierreDeTurno cierre, ResumenDeTurno resumen) {
}
