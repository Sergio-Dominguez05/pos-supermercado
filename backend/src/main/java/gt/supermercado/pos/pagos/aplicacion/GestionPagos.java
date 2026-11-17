package gt.supermercado.pos.pagos.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.RepositorioPagos;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HU-07.03 Cobrar con tarjeta: el cajero procesa el pago en la terminal bancaria y lo registra en la venta en curso
 * antes de finalizar el cobro (HU-07.08). En V1 se paga con una sola tarjeta por venta (Architecture.md §5.2); la
 * tabla admite varias para el pago dividido futuro (HU-07.05).
 */
@Service
public class GestionPagos {

    private static final Pattern ULTIMOS4 = Pattern.compile("\\d{4}");
    /** Un número de tarjeta tiene de 13 a 19 dígitos: no se acepta como autorización (crit. 4). */
    private static final Pattern NUMERO_DE_TARJETA = Pattern.compile("\\d{13,19}");
    private static final int AUTORIZACION_MAXIMA = 30;

    private final RepositorioPagos pagos;
    private final GestionTurnos turnos;
    private final GestionVentas ventas;
    private final Bitacora bitacora;

    public GestionPagos(RepositorioPagos pagos, GestionTurnos turnos, GestionVentas ventas, Bitacora bitacora) {
        this.pagos = pagos;
        this.turnos = turnos;
        this.ventas = ventas;
        this.bitacora = bitacora;
    }

    /** Lo que pide el cajero al registrar el pago. {@code monto} vacío es el total pendiente (crit. 2). */
    public record SolicitudPago(TipoTarjeta tipoTarjeta, String autorizacion, String ultimos4, BigDecimal monto) {}

    /** La venta en curso, sus pagos, lo pagado y lo pendiente (crit. 1). */
    public record EstadoCobro(Venta venta, List<Pago> pagos, BigDecimal pagado, BigDecimal pendiente) {

        /**
         * HU-07.07 crit. 2: la suma de los pagos cubre el total, así que se puede finalizar. Sin pagos no está
         * completo: una venta se cobra siempre (una venta vacía no llega al cobro).
         */
        public boolean completo() {
            return !pagos.isEmpty() && pendiente.signum() == 0;
        }

        /** HU-07.07 crit. 1: con pagos que suman menos que el total, la venta no se finaliza. */
        public EstadoCobro exigirCompleto() {
            if (!completo()) {
                throw new NegocioException("PAGO_INCOMPLETO",
                        "Falta cobrar Q%s: la venta no se puede finalizar con el pago incompleto."
                                .formatted(pendiente.setScale(2)));
            }
            return this;
        }
    }

    /** Crit. 1 y 2: el total a cobrar y lo pendiente de la venta en curso del turno. */
    @Transactional(readOnly = true)
    public EstadoCobro estado(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.actual(sesion)
                .orElseThrow(() -> new NegocioException("SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso."));
        return estado(venta, turno);
    }

    /**
     * Registra el pago con tarjeta de la venta en curso.
     * <ul>
     *   <li>HU-09.02 crit. 2: con el turno abierto del cajero, que queda guardado en el pago.</li>
     *   <li>Crit. 3: el tipo de tarjeta y el número de autorización de la terminal son obligatorios.</li>
     *   <li>Crit. 4: los últimos 4 dígitos son opcionales; nunca se acepta el número completo de la tarjeta.</li>
     *   <li>Crit. 5: el monto no puede ser mayor al total pendiente. Sin monto, se cobra el pendiente (crit. 2).</li>
     *   <li>Crit. 6: si la terminal rechaza la tarjeta, el cajero no registra nada: aquí solo llegan pagos aprobados.</li>
     *   <li>Crit. 7: montos en quetzales, con dos decimales.</li>
     * </ul>
     * La venta se bloquea mientras tanto, y con el pago ya no se modifica hasta eliminarlo (HU-07.06).
     */
    @Transactional
    public EstadoCobro registrar(SolicitudPago solicitud, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.paraRegistrarPago(turno);
        if (pagos.tienePagos(venta.id())) {
            throw new NegocioException("PAGO_YA_REGISTRADO",
                    "La venta ya tiene un pago con tarjeta: en esta versión se cobra con una sola tarjeta.");
        }
        if (solicitud.tipoTarjeta() == null) {
            throw new NegocioException("TIPO_TARJETA_REQUERIDO", "Indique si la tarjeta es de débito o de crédito.");
        }
        String autorizacion = autorizacion(solicitud.autorizacion());
        String ultimos4 = ultimos4(solicitud.ultimos4());
        BigDecimal pendiente = estado(venta, turno).pendiente();
        BigDecimal monto = solicitud.monto() == null ? pendiente : solicitud.monto();
        if (monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2) {
            throw new NegocioException("MONTO_INVALIDO", "El monto debe ser mayor que cero, en quetzales y centavos.");
        }
        if (monto.compareTo(pendiente) > 0) {
            throw new NegocioException("MONTO_MAYOR_AL_PENDIENTE",
                    "El monto (Q%s) no puede ser mayor al total pendiente (Q%s).".formatted(
                            monto.setScale(2), pendiente.setScale(2)));
        }
        pagos.registrar(venta.id(), turno.id(), sesion.usuarioId(), solicitud.tipoTarjeta(), autorizacion, ultimos4,
                monto.setScale(2));
        return estado(venta, turno);
    }

    /**
     * HU-07.07: el cobro de la venta en curso, bloqueada, solo si los pagos cubren el total (crit. 1); si no, 422
     * {@code PAGO_INCOMPLETO}. Finalizar el cobro (HU-07.08) parte de aquí, en la misma transacción, para que la venta
     * y sus pagos no cambien mientras se emite la factura.
     */
    @Transactional
    public EstadoCobro paraFinalizar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        return estado(ventas.enCursoBloqueada(turno), turno).exigirCompleto();
    }

    /**
     * HU-07.06: quita un pago registrado por error.
     * <ul>
     *   <li>Crit. 1: cualquier pago de la venta en curso, mientras no se haya finalizado.</li>
     *   <li>Crit. 2: devuelve el cobro con el pendiente recalculado; la venta vuelve a poder modificarse.</li>
     *   <li>Crit. 3: los pagos de una venta finalizada no son de la venta en curso (404); la base de datos además
     *       impide modificarlos (trigger de V20). Para corregirla hay que anular la factura (EP-08).</li>
     *   <li>Crit. 4: la eliminación queda en la bitácora con la venta, el tipo, los últimos 4, la autorización y el
     *       monto; también el intento rechazado (HU-11.01 crit. 3).</li>
     * </ul>
     * El pago no se borra: se marca como eliminado (regla de V1).
     */
    @Transactional
    public EstadoCobro eliminar(long pagoId, SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta;
        try {
            venta = ventas.enCursoBloqueada(turno);
        } catch (NegocioException error) {
            throw rechazarEliminacion(sesion, pagoId, error);
        }
        Pago pago = pagos.deLaVenta(venta.id(), pagoId).orElseThrow(() -> rechazarEliminacion(sesion, pagoId,
                new RecursoNoEncontradoException("PAGO_NO_ENCONTRADO", "El pago no está en la venta en curso.")));
        pagos.eliminar(pago.id(), sesion.usuarioId());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.PAGO_TARJETA_ELIMINADO, true, "Pago con tarjeta eliminado de la venta N.º %d: %s, Q%s"
                .formatted(venta.numero(), tarjeta(pago), pago.monto().setScale(2)), "pago", pago.id(),
                detalle(venta, pago)));
        return estado(venta, turno);
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazarEliminacion(SesionActiva sesion, long pagoId, NegocioException error) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.PAGO_TARJETA_ELIMINADO, false, "Eliminación de pago rechazada: " + error.getMessage(),
                "pago", pagoId, null));
        return error;
    }

    private static String tarjeta(Pago pago) {
        return (pago.tipoTarjeta() == TipoTarjeta.DEBITO ? "débito" : "crédito")
                + (pago.ultimos4() == null ? "" : " ••••" + pago.ultimos4());
    }

    private static Map<String, Object> detalle(Venta venta, Pago pago) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("venta", venta.numero());
        detalle.put("tipoTarjeta", pago.tipoTarjeta().name());
        detalle.put("ultimos4", pago.ultimos4());
        detalle.put("autorizacion", pago.autorizacion());
        detalle.put("monto", pago.monto());
        return detalle;
    }

    private EstadoCobro estado(Venta venta, Turno turno) {
        List<Pago> deLaVenta = pagos.deLaVenta(venta.id());
        BigDecimal pagado = deLaVenta.stream().map(Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        BigDecimal pendiente = venta.totales().total().subtract(pagado).max(BigDecimal.ZERO).setScale(2);
        return new EstadoCobro(venta, deLaVenta, pagado, pendiente);
    }

    private static String autorizacion(String valor) {
        String limpia = valor == null ? "" : valor.strip();
        if (limpia.isEmpty()) {
            throw new NegocioException("AUTORIZACION_REQUERIDA",
                    "Ingrese el número de autorización que entregó la terminal bancaria.");
        }
        if (limpia.length() > AUTORIZACION_MAXIMA || NUMERO_DE_TARJETA.matcher(limpia.replace(" ", "")).matches()) {
            throw new NegocioException("AUTORIZACION_INVALIDA",
                    "La autorización no es válida. Nunca ingrese el número completo de la tarjeta.");
        }
        return limpia;
    }

    private static String ultimos4(String valor) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty()) {
            return null;
        }
        if (!ULTIMOS4.matcher(limpio).matches()) {
            throw new NegocioException("ULTIMOS4_INVALIDOS",
                    "Registre solo los últimos 4 dígitos de la tarjeta, nunca el número completo.");
        }
        return limpio;
    }
}
