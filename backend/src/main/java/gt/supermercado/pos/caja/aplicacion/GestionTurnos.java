package gt.supermercado.pos.caja.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.caja.dominio.ResumenesDeTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.caja.dominio.VerificadorVentasSuspendidas;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.aplicacion.GestionCajas;
import gt.supermercado.pos.configuracion.aplicacion.Parametros;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Turnos de caja. HU-09.01: abrir el turno en la caja del equipo desde el que se inició sesión. El cierre llega con
 * HU-09.08.
 */
@Service
public class GestionTurnos {

    private static final String VER_TURNOS_AJENOS = "CAJA_VER_CIERRES";

    private final RepositorioTurnos turnos;
    private final ResumenesDeTurno resumenes;
    private final GestionCajas cajas;
    private final VerificadorVentasSuspendidas suspendidas;
    private final Bitacora bitacora;
    private final Parametros parametros;

    public GestionTurnos(RepositorioTurnos turnos, ResumenesDeTurno resumenes, GestionCajas cajas,
                         VerificadorVentasSuspendidas suspendidas, Bitacora bitacora, Parametros parametros) {
        this.turnos = turnos;
        this.resumenes = resumenes;
        this.cajas = cajas;
        this.suspendidas = suspendidas;
        this.bitacora = bitacora;
        this.parametros = parametros;
    }

    /**
     * HU-06.12 crit. 4: la caja no se cierra con ventas suspendidas; el cajero debe retomarlas (HU-06.13) o
     * cancelarlas antes. El cierre de caja (HU-09.08, #82) y el cierre por un supervisor (HU-09.12, #86) deben
     * llamarlo antes de cerrar el turno.
     */
    @Transactional(readOnly = true)
    public void exigirSinVentasSuspendidas(Turno turno) {
        int cantidad = suspendidas.suspendidasDelTurno(turno.id());
        if (cantidad > 0) {
            throw new NegocioException("VENTAS_SUSPENDIDAS", (cantidad == 1
                    ? "Hay 1 venta suspendida"
                    : "Hay %d ventas suspendidas".formatted(cantidad))
                    + ": retómelas o cancélelas antes de cerrar la caja.");
        }
    }

    /**
     * Turno abierto en la caja de esta sesión, sea de quien sea; vacío si la caja está cerrada o el equipo no está
     * vinculado a una caja. Con esto la pantalla sabe si debe pedir abrir el turno (crit. 1).
     */
    @Transactional(readOnly = true)
    public Optional<Turno> actualDeLaCaja(SesionActiva sesion) {
        return sesion.cajaId() == null ? Optional.empty() : turnos.abiertoEnCaja(sesion.cajaId());
    }

    /** HU-09.06: el supervisor ve todos los turnos abiertos; el cajero, únicamente el propio. */
    @Transactional(readOnly = true)
    public List<Turno> abiertosVisibles(SesionActiva sesion) {
        if (sesion.permisos().contains(VER_TURNOS_AJENOS)) {
            return turnos.abiertos();
        }
        if (resumenOculto(sesion)) {
            return List.of();
        }
        return turnos.abiertoDeUsuario(sesion.usuarioId()).stream().toList();
    }

    /** HU-09.06: consulta sin cerrar la caja y evita que un cajero lea el turno de otra persona. */
    @Transactional(readOnly = true)
    public ResumenDeTurno resumen(long turnoId, SesionActiva sesion) {
        Turno turno = turnos.buscarPorId(turnoId).filter(t -> t.estado() == EstadoTurno.ABIERTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("TURNO_ABIERTO_NO_ENCONTRADO",
                        "El turno abierto no existe."));
        if (!turno.esDe(sesion.usuarioId()) && !sesion.permisos().contains(VER_TURNOS_AJENOS)) {
            throw new AccessDeniedException("No puede consultar el resumen de otro cajero.");
        }
        if (resumenOculto(sesion)) {
            throw new AccessDeniedException("El resumen permanece oculto durante el cierre ciego.");
        }
        return resumenes.de(turno.id());
    }

    private boolean resumenOculto(SesionActiva sesion) {
        return sesion.rol() == Rol.CAJERO && parametros.cierreCiego();
    }

    /**
     * HU-09.02: el turno abierto del usuario en la caja de su sesión, al que se deben asociar sus ventas, pagos y
     * anulaciones (crit. 2). Si la caja está cerrada o el turno es de otra persona, se rechaza la operación: los
     * módulos de ventas (EP-06), pagos (EP-07) y facturación (EP-08) lo llaman antes de registrar cualquier cobro, así
     * la regla se cumple en el servidor y no solo en la pantalla.
     */
    @Transactional(readOnly = true)
    public Turno exigirTurnoAbierto(SesionActiva sesion) {
        Turno turno = actualDeLaCaja(sesion).orElseThrow(() -> new NegocioException("CAJA_CERRADA",
                sesion.cajaId() == null
                        ? "Este equipo no está vinculado a ninguna caja: no se puede vender."
                        : "La caja está cerrada. Abra su turno para empezar a vender."));
        if (!turno.esDe(sesion.usuarioId())) {
            throw new NegocioException("CAJA_CON_TURNO_DE_OTRO_USUARIO",
                    "La caja tiene abierto el turno de %s: no puede vender en él.".formatted(turno.usuarioNombre()));
        }
        return turno;
    }

    /**
     * Abre el turno del usuario en la caja de su sesión con la hora del servidor (crit. 2), sin fondo inicial
     * (crit. 3), y lo registra en la bitácora (crit. 4). Una caja y un usuario tienen como máximo un turno abierto
     * (HU-09.03); la base de datos también lo garantiza.
     */
    @Transactional
    public Turno abrir(SesionActiva actor) {
        if (actor.cajaId() == null) {
            throw rechazar(actor, "SIN_CAJA",
                    "Este equipo no está vinculado a ninguna caja. Pida al Administrador que lo vincule.");
        }
        if (!cajas.obtener(actor.cajaId()).activo()) {
            // La caja se desactivó después de que el usuario inició sesión en ella.
            throw rechazar(actor, "CAJA_INACTIVA", "La caja de este equipo está desactivada.");
        }
        Optional<Turno> enLaCaja = turnos.abiertoEnCaja(actor.cajaId());
        if (enLaCaja.isPresent()) {
            Turno turno = enLaCaja.get();
            throw turno.esDe(actor.usuarioId())
                    ? rechazar(actor, "TURNO_YA_ABIERTO", "Ya tiene abierto su turno en esta caja.")
                    : rechazar(actor, "CAJA_CON_TURNO_DE_OTRO_USUARIO",
                            "La caja tiene abierto el turno de %s. Primero se debe cerrar ese turno."
                                    .formatted(turno.usuarioNombre()));
        }
        Optional<Turno> propio = turnos.abiertoDeUsuario(actor.usuarioId());
        if (propio.isPresent()) {
            throw rechazar(actor, "TURNO_ABIERTO_EN_OTRA_CAJA",
                    "Ya tiene un turno abierto en %s. Primero debe cerrarlo.".formatted(propio.get().cajaNombre()));
        }

        long id;
        try {
            id = turnos.abrir(actor.cajaId(), actor.usuarioId());
        } catch (DuplicateKeyException e) {
            // Otro usuario abrió un turno en la misma caja al mismo tiempo.
            throw rechazar(actor, "TURNO_YA_ABIERTO", "La caja ya tiene un turno abierto.");
        }
        Turno turno = turnos.buscarPorId(id).orElseThrow();
        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), AccionSensible.TURNO_ABIERTO,
                true, "Turno abierto en la caja '%s'".formatted(turno.cajaNombre()), "turno", id, null));
        return turno;
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva actor, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(),
                AccionSensible.TURNO_ABIERTO, false, "Apertura de turno rechazada: " + mensaje, null, null, null));
        return new NegocioException(codigo, mensaje);
    }
}
