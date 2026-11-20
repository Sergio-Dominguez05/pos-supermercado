package gt.supermercado.pos.caja.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.ComprobanteDeCierre;
import gt.supermercado.pos.caja.dominio.ImpresionDeCierre;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.caja.dominio.ResumenesDeTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.caja.dominio.VentasDelTurno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-09.08 Cerrar la caja, HU-09.09 Justificar diferencias y HU-09.10 Imprimir el comprobante de cierre.
 * <ul>
 *   <li>09.08 crit. 1: con ventas suspendidas o una venta en curso con productos no se cierra; la venta en curso vacía
 *       se cancela sola.</li>
 *   <li>09.08 crit. 2 y 3: el cajero ingresa el total del cierre de lote de la terminal y se compara con lo cobrado con
 *       tarjeta en el POS (sin las facturas anuladas, decisión de Sergio); la diferencia es lote − registrado.</li>
 *   <li>09.08 crit. 4: confirmado, el turno queda cerrado y la base de datos impide modificarlo (V32).</li>
 *   <li>09.08 crit. 5 y 6: guarda caja, usuario, apertura, cierre, totales y diferencia; queda en la bitácora, también
 *       el intento rechazado.</li>
 *   <li>09.09 crit. 1: una diferencia distinta de cero exige una justificación (de 5 a 500 caracteres).</li>
 *   <li>09.10: imprime el comprobante con las copias de cierre de la impresora de la caja, y se reimprime desde los
 *       cierres anteriores.</li>
 * </ul>
 * El cierre de la caja de otro por un supervisor es de HU-09.12.
 */
@Service
public class CierreDeCaja {

    /** Quien tiene este permiso ve y reimprime los cierres de los demás (HU-09.10 crit. 3). */
    static final String VER_CIERRES = "CAJA_VER_CIERRES";
    static final int CIERRES_MAXIMOS = 50;

    private final GestionTurnos turnos;
    private final RepositorioTurnos repositorio;
    private final ResumenesDeTurno resumenes;
    private final VentasDelTurno ventas;
    private final ImpresionDeCierre impresion;
    private final Bitacora bitacora;
    private final TransactionOperations transacciones;

    public CierreDeCaja(GestionTurnos turnos, RepositorioTurnos repositorio, ResumenesDeTurno resumenes,
                        VentasDelTurno ventas, ImpresionDeCierre impresion, Bitacora bitacora,
                        TransactionOperations transacciones) {
        this.turnos = turnos;
        this.repositorio = repositorio;
        this.resumenes = resumenes;
        this.ventas = ventas;
        this.impresion = impresion;
        this.bitacora = bitacora;
        this.transacciones = transacciones;
    }

    /** El cierre ya guardado, su comprobante y si se imprimió. */
    public record CierreHecho(ComprobanteDeCierre comprobante, ImpresionDeCierre.Resultado impresion) {}

    /** Crit. 3: lo cobrado en el turno abierto del cajero, antes de cerrarlo. */
    @Transactional(readOnly = true)
    public ResumenDeTurno resumen(SesionActiva sesion) {
        return resumenes.de(turnos.exigirTurnoAbierto(sesion).id());
    }

    public CierreHecho cerrar(BigDecimal totalLote, String justificacion, SesionActiva sesion) {
        long turnoId = transacciones.execute(transaccion -> registrar(totalLote, justificacion, sesion));
        ComprobanteDeCierre comprobante = comprobante(turnoId);
        return new CierreHecho(comprobante, imprimir(comprobante, comprobante.cierre().cajaId(), false));
    }

    private long registrar(BigDecimal totalLote, String justificacion, SesionActiva sesion) {
        Turno turno;
        BigDecimal totalRegistrado;
        BigDecimal diferencia;
        String motivo = justificacion == null || justificacion.isBlank() ? null : justificacion.strip();
        try {
            turno = turnos.exigirTurnoAbierto(sesion);
            turnos.exigirSinVentasSuspendidas(turno);
            if (ventas.tieneVentaEnCursoConProductos(turno.id())) {
                throw new NegocioException("VENTA_EN_CURSO",
                        "Hay una venta en curso con productos: cóbrela o cancélela antes de cerrar la caja.");
            }
            if (totalLote == null || totalLote.signum() < 0 || totalLote.stripTrailingZeros().scale() > 2) {
                throw new NegocioException("TOTAL_LOTE_INVALIDO",
                        "Ingrese el total del cierre de lote de la terminal, en quetzales con hasta 2 decimales.");
            }
            totalRegistrado = resumenes.de(turno.id()).totalRegistrado();
            diferencia = totalLote.subtract(totalRegistrado).setScale(2);
            if (diferencia.signum() != 0 && (motivo == null || motivo.length() < 5)) {
                throw new NegocioException("JUSTIFICACION_REQUERIDA",
                        "Hay una diferencia de Q%s entre el cierre de lote y lo registrado: explique el motivo."
                                .formatted(diferencia.toPlainString()));
            }
            if (motivo != null && motivo.length() > 500) {
                throw new NegocioException("JUSTIFICACION_INVALIDA", "La justificación admite hasta 500 caracteres.");
            }
        } catch (NegocioException error) {
            throw rechazar(sesion, error);
        }
        ventas.cancelarVentaVaciaEnCurso(turno.id());
        if (!repositorio.cerrar(turno.id(), sesion.usuarioId(), totalRegistrado, totalLote.setScale(2), diferencia,
                motivo)) {
            throw rechazar(sesion, new NegocioException("TURNO_YA_CERRADO", "El turno ya se cerró."));
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("caja", turno.cajaNombre());
        detalle.put("totalRegistrado", totalRegistrado);
        detalle.put("totalLote", totalLote.setScale(2));
        detalle.put("diferencia", diferencia);
        if (motivo != null) {
            detalle.put("justificacion", motivo);
        }
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.TURNO_CERRADO,
                true, "Turno cerrado en la caja '%s': registrado Q%s, cierre de lote Q%s, diferencia Q%s".formatted(
                        turno.cajaNombre(), totalRegistrado.toPlainString(), totalLote.setScale(2).toPlainString(),
                        diferencia.toPlainString()), "turno", turno.id(), detalle).enCaja(turno.cajaId()));
        return turno.id();
    }

    /** HU-09.10 crit. 3: los cierres anteriores; el cajero ve los suyos y el supervisor, todos. */
    @Transactional(readOnly = true)
    public List<CierreDeTurno> cierresAnteriores(SesionActiva sesion) {
        return repositorio.cierres(sesion.permisos().contains(VER_CIERRES) ? null : sesion.usuarioId(),
                CIERRES_MAXIMOS);
    }

    /** HU-09.10 crit. 3: reimprime un cierre anterior en la impresora de la caja desde donde se pide. */
    public ImpresionDeCierre.Resultado reimprimir(long turnoId, SesionActiva sesion) {
        ComprobanteDeCierre comprobante = comprobante(turnoId);
        if (!sesion.permisos().contains(VER_CIERRES) && comprobante.cierre().usuarioId() != sesion.usuarioId()) {
            throw new NegocioException("CIERRE_DE_OTRO_USUARIO", "Solo puede reimprimir sus propios cierres.");
        }
        return imprimir(comprobante, sesion.cajaId(), true);
    }

    @Transactional(readOnly = true)
    public ComprobanteDeCierre comprobante(long turnoId) {
        CierreDeTurno cierre = repositorio.cierre(turnoId).orElseThrow(() -> new RecursoNoEncontradoException(
                "CIERRE_NO_ENCONTRADO", "El cierre no existe."));
        return new ComprobanteDeCierre(cierre, resumenes.de(turnoId));
    }

    private ImpresionDeCierre.Resultado imprimir(ComprobanteDeCierre comprobante, Long cajaId, boolean reimpresion) {
        try {
            return impresion.imprimir(comprobante, cajaId, reimpresion);
        } catch (RuntimeException e) {
            return new ImpresionDeCierre.Resultado(false, "No se pudo imprimir el comprobante; se puede reimprimir.");
        }
    }

    private NegocioException rechazar(SesionActiva sesion, NegocioException error) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.TURNO_CERRADO, false, "Cierre de caja rechazado: " + error.getMessage(), "turno", null,
                null).enCaja(sesion.cajaId()));
        return error;
    }
}
