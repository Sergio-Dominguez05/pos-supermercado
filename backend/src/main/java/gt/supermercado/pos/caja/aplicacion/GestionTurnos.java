package gt.supermercado.pos.caja.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.aplicacion.GestionCajas;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Turnos de caja. HU-09.01: abrir el turno en la caja del equipo desde el que se inició sesión. El cierre llega con
 * HU-09.08.
 */
@Service
public class GestionTurnos {

    private final RepositorioTurnos turnos;
    private final GestionCajas cajas;
    private final Bitacora bitacora;

    public GestionTurnos(RepositorioTurnos turnos, GestionCajas cajas, Bitacora bitacora) {
        this.turnos = turnos;
        this.cajas = cajas;
        this.bitacora = bitacora;
    }

    /**
     * Turno abierto en la caja de esta sesión, sea de quien sea; vacío si la caja está cerrada o el equipo no está
     * vinculado a una caja. Con esto la pantalla sabe si debe pedir abrir el turno (crit. 1).
     */
    @Transactional(readOnly = true)
    public Optional<Turno> actualDeLaCaja(SesionActiva sesion) {
        return sesion.cajaId() == null ? Optional.empty() : turnos.abiertoEnCaja(sesion.cajaId());
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
