package gt.supermercado.pos.pagos.aplicacion;

import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.RepositorioPagos;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

    public GestionPagos(RepositorioPagos pagos, GestionTurnos turnos, GestionVentas ventas) {
        this.pagos = pagos;
        this.turnos = turnos;
        this.ventas = ventas;
    }

    /** Lo que pide el cajero al registrar el pago. {@code monto} vacío es el total pendiente (crit. 2). */
    public record SolicitudPago(TipoTarjeta tipoTarjeta, String autorizacion, String ultimos4, BigDecimal monto) {}

    /** La venta en curso, sus pagos, lo pagado y lo pendiente (crit. 1). */
    public record EstadoCobro(Venta venta, List<Pago> pagos, BigDecimal pagado, BigDecimal pendiente) {}

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
