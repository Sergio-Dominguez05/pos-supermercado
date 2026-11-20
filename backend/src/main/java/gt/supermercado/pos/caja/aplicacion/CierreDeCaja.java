package gt.supermercado.pos.caja.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.ComprobanteDeCierre;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.ImpresionDeCierre;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.caja.dominio.ResumenesDeTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.caja.dominio.VentasDelTurno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.aplicacion.Parametros;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
 *   <li>09.12: quien tiene el permiso de cerrar la caja de otro (Supervisor y Administrador) elige un turno abierto
 *       de otro usuario y lo cierra con las mismas reglas; el cierre guarda al cajero del turno y, en
 *       {@code cerrado_por}, al supervisor (crit. 2), y queda en la bitácora como
 *       {@code TURNO_CERRADO_POR_SUPERVISOR} (crit. 3). El comprobante sale en la impresora de la caja del turno.</li>
 * </ul>
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
    private final Parametros parametros;

    public CierreDeCaja(GestionTurnos turnos, RepositorioTurnos repositorio, ResumenesDeTurno resumenes,
                        VentasDelTurno ventas, ImpresionDeCierre impresion, Bitacora bitacora,
                        TransactionOperations transacciones, Parametros parametros) {
        this.turnos = turnos;
        this.repositorio = repositorio;
        this.resumenes = resumenes;
        this.ventas = ventas;
        this.impresion = impresion;
        this.bitacora = bitacora;
        this.transacciones = transacciones;
        this.parametros = parametros;
    }

    /** El cierre ya guardado, su comprobante y si se imprimió. */
    public record CierreHecho(ComprobanteDeCierre comprobante, ImpresionDeCierre.Resultado impresion) {}

    /** Datos iniciales del cierre; en modo ciego el resumen no se envía al cajero. */
    public record PreparacionCierre(boolean cierreCiego, ResumenDeTurno resumen) {}

    /** Resultado que se revela después de que el cajero confirma el monto contado. */
    public record ComparacionCierre(ResumenDeTurno resumen, BigDecimal diferencia) {}

    /** HU-09.11: oculta lo cobrado al cajero cuando está activo el cierre ciego; un supervisor siempre lo ve. */
    @Transactional(readOnly = true)
    public PreparacionCierre resumen(SesionActiva sesion) {
        ResumenDeTurno resumen = resumenes.de(turnos.exigirTurnoAbierto(sesion).id());
        boolean cierreCiego = parametros.cierreCiego() && sesion.rol() == Rol.CAJERO;
        return new PreparacionCierre(cierreCiego, cierreCiego ? null : resumen);
    }

    /** HU-09.11 crit. 2: revela el total registrado y la diferencia tras confirmar el monto ingresado. */
    @Transactional(readOnly = true)
    public ComparacionCierre comparar(BigDecimal totalLote, SesionActiva sesion) {
        validarTotalLote(totalLote);
        ResumenDeTurno resumen = resumenes.de(turnos.exigirTurnoAbierto(sesion).id());
        return new ComparacionCierre(resumen, totalLote.subtract(resumen.totalRegistrado()).setScale(2));
    }

    public CierreHecho cerrar(BigDecimal totalLote, String justificacion, SesionActiva sesion) {
        return cerrarTurno(() -> turnos.exigirTurnoAbierto(sesion), totalLote, justificacion, sesion, false);
    }

    /** HU-09.12 crit. 1: los turnos abiertos de los demás usuarios, que se pueden cerrar. */
    @Transactional(readOnly = true)
    public List<Turno> turnosAbiertosDeOtros(SesionActiva sesion) {
        return repositorio.abiertos().stream().filter(turno -> !turno.esDe(sesion.usuarioId())).toList();
    }

    /** HU-09.12: lo cobrado en el turno abierto de otro usuario, antes de cerrarlo. */
    @Transactional(readOnly = true)
    public ResumenDeTurno resumenDeOtro(long turnoId, SesionActiva sesion) {
        return resumenes.de(turnoAbiertoDeOtro(turnoId, sesion).id());
    }

    /** HU-09.12 crit. 1 a 3: el supervisor cierra el turno de otro usuario. */
    public CierreHecho cerrarDeOtro(long turnoId, BigDecimal totalLote, String justificacion, SesionActiva sesion) {
        return cerrarTurno(() -> turnoAbiertoDeOtro(turnoId, sesion), totalLote, justificacion, sesion, true);
    }

    private Turno turnoAbiertoDeOtro(long turnoId, SesionActiva sesion) {
        Turno turno = repositorio.buscarPorId(turnoId).orElseThrow(() -> new NegocioException("TURNO_NO_ENCONTRADO",
                "El turno no existe."));
        if (turno.estado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_YA_CERRADO", "El turno ya se cerró.");
        }
        if (turno.esDe(sesion.usuarioId())) {
            throw new NegocioException("TURNO_PROPIO", "Su propio turno se cierra desde la pantalla Caja.");
        }
        return turno;
    }

    private CierreHecho cerrarTurno(Supplier<Turno> elegido, BigDecimal totalLote,
                                    String justificacion, SesionActiva sesion, boolean porSupervisor) {
        long turnoId = transacciones.execute(transaccion -> registrar(elegido, totalLote, justificacion, sesion,
                porSupervisor));
        ComprobanteDeCierre comprobante = comprobante(turnoId);
        return new CierreHecho(comprobante, imprimir(comprobante, comprobante.cierre().cajaId(), false));
    }

    private long registrar(Supplier<Turno> elegido, BigDecimal totalLote, String justificacion,
                           SesionActiva sesion, boolean porSupervisor) {
        AccionSensible accion = porSupervisor ? AccionSensible.TURNO_CERRADO_POR_SUPERVISOR
                : AccionSensible.TURNO_CERRADO;
        Turno turno;
        BigDecimal totalRegistrado;
        BigDecimal diferencia;
        String motivo = justificacion == null || justificacion.isBlank() ? null : justificacion.strip();
        try {
            turno = elegido.get();
            turnos.exigirSinVentasSuspendidas(turno);
            if (ventas.tieneVentaEnCursoConProductos(turno.id())) {
                throw new NegocioException("VENTA_EN_CURSO",
                        "Hay una venta en curso con productos: cóbrela o cancélela antes de cerrar la caja.");
            }
            validarTotalLote(totalLote);
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
            throw rechazar(sesion, error, accion);
        }
        ventas.cancelarVentaVaciaEnCurso(turno.id());
        if (!repositorio.cerrar(turno.id(), sesion.usuarioId(), totalRegistrado, totalLote.setScale(2), diferencia,
                motivo)) {
            throw rechazar(sesion, new NegocioException("TURNO_YA_CERRADO", "El turno ya se cerró."), accion);
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("caja", turno.cajaNombre());
        if (porSupervisor) {
            detalle.put("cajero", turno.usuarioNombre());
        }
        detalle.put("totalRegistrado", totalRegistrado);
        detalle.put("totalLote", totalLote.setScale(2));
        detalle.put("diferencia", diferencia);
        if (motivo != null) {
            detalle.put("justificacion", motivo);
        }
        String totales = "registrado Q%s, cierre de lote Q%s, diferencia Q%s".formatted(totalRegistrado.toPlainString(),
                totalLote.setScale(2).toPlainString(), diferencia.toPlainString());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), accion, true, porSupervisor
                ? "Turno de %s cerrado por supervisor en la caja '%s': %s".formatted(turno.usuarioNombre(),
                        turno.cajaNombre(), totales)
                : "Turno cerrado en la caja '%s': %s".formatted(turno.cajaNombre(), totales),
                "turno", turno.id(), detalle).enCaja(turno.cajaId()));
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

    private NegocioException rechazar(SesionActiva sesion, NegocioException error, AccionSensible accion) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), accion, false,
                (accion == AccionSensible.TURNO_CERRADO ? "Cierre de caja rechazado: "
                        : "Cierre de la caja de otro usuario rechazado: ") + error.getMessage(), "turno", null,
                null).enCaja(sesion.cajaId()));
        return error;
    }

    private static void validarTotalLote(BigDecimal totalLote) {
        if (totalLote == null || totalLote.signum() < 0 || totalLote.stripTrailingZeros().scale() > 2) {
            throw new NegocioException("TOTAL_LOTE_INVALIDO",
                    "Ingrese el total del cierre de lote de la terminal, en quetzales con hasta 2 decimales.");
        }
    }
}
