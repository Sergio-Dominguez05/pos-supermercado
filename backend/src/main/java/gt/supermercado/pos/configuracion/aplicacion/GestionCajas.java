package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.dominio.Caja;
import gt.supermercado.pos.configuracion.dominio.ReglasCaja;
import gt.supermercado.pos.configuracion.dominio.RepositorioCajas;
import gt.supermercado.pos.configuracion.dominio.VerificadorTurnoAbiertoCaja;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TokenSesion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro de las cajas del supermercado (HU-12.05): crear, editar, activar, desactivar y vincular el equipo físico.
 * La impresora de cada caja llega con HU-12.08 (#117).
 */
@Service
public class GestionCajas {

    static final String MODULO = "configuracion";

    private final RepositorioCajas cajas;
    private final VerificadorTurnoAbiertoCaja turnos;
    private final Bitacora bitacora;

    public GestionCajas(RepositorioCajas cajas, VerificadorTurnoAbiertoCaja turnos, Bitacora bitacora) {
        this.cajas = cajas;
        this.turnos = turnos;
        this.bitacora = bitacora;
    }

    /** Resultado de vincular un equipo: el token solo existe aquí, para guardarlo en la cookie del equipo. */
    public record EquipoVinculado(Caja caja, String token) {}

    @Transactional(readOnly = true)
    public List<Caja> listar() {
        return cajas.listar();
    }

    @Transactional(readOnly = true)
    public Caja obtener(long id) {
        return cajas.buscarPorId(id).orElseThrow(GestionCajas::noEncontrada);
    }

    /** Caja a la que está vinculado el equipo que hace la petición, si lo está. */
    @Transactional(readOnly = true)
    public Optional<Caja> delEquipo(String tokenEquipo) {
        return tokenEquipo == null || tokenEquipo.isBlank()
                ? Optional.empty()
                : cajas.buscarPorEquipo(TokenSesion.hash(tokenEquipo));
    }

    @Transactional
    public Caja crear(String nombre, String serieFel, SesionActiva actor) {
        String nombreNormal = ReglasCaja.normalizarNombre(nombre);
        String serie = ReglasCaja.normalizarSerie(serieFel);
        validar(nombreNormal, serie, null, actor, "CAJA_CREADA");

        long id;
        try {
            id = cajas.crear(nombreNormal, serie);
        } catch (DuplicateKeyException e) {
            throw rechazar(actor, "CAJA_CREADA", null, "CAJA_DUPLICADA", "Ya existe una caja con ese nombre.");
        }
        bitacora.registrar(evento(actor, "CAJA_CREADA", true, "Caja '%s' creada".formatted(nombreNormal), id, null));
        return obtener(id);
    }

    @Transactional
    public Caja editar(long id, String nombre, String serieFel, SesionActiva actor) {
        Caja actual = obtener(id);
        String nombreNormal = ReglasCaja.normalizarNombre(nombre);
        String serie = ReglasCaja.normalizarSerie(serieFel);
        validar(nombreNormal, serie, id, actor, "CAJA_EDITADA");

        Map<String, Object> cambios = new LinkedHashMap<>();
        if (!nombreNormal.equals(actual.nombre())) {
            cambios.put("nombre", EventoBitacora.cambio(actual.nombre(), nombreNormal));
        }
        if (!Objects.equals(serie, actual.serieFel())) {
            cambios.put("serieFel", cambioNulable(actual.serieFel(), serie));
        }
        if (cambios.isEmpty()) {
            return actual;
        }
        try {
            cajas.actualizar(id, nombreNormal, serie);
        } catch (DuplicateKeyException e) {
            throw rechazar(actor, "CAJA_EDITADA", id, "CAJA_DUPLICADA", "Ya existe una caja con ese nombre.");
        }
        bitacora.registrar(evento(actor, "CAJA_EDITADA", true,
                "Caja '%s' editada: %s".formatted(nombreNormal, String.join(", ", cambios.keySet())), id, cambios));
        return obtener(id);
    }

    @Transactional
    public Caja desactivar(long id, SesionActiva actor) {
        Caja caja = obtener(id);
        if (!caja.activo()) {
            return caja;
        }
        if (turnos.tieneTurnoAbierto(id)) {
            throw rechazar(actor, "CAJA_DESACTIVADA", id, "CAJA_CON_TURNO_ABIERTO",
                    "La caja tiene un turno abierto: primero se debe cerrar el turno.");
        }
        return cambiarActivo(caja, false, actor);
    }

    @Transactional
    public Caja activar(long id, SesionActiva actor) {
        Caja caja = obtener(id);
        return caja.activo() ? caja : cambiarActivo(caja, true, actor);
    }

    /**
     * Vincula el equipo desde el que se hace la petición a la caja (crit. 4). Si la caja ya tenía otro equipo,
     * ese queda desvinculado. Solo se guarda el hash del token.
     */
    @Transactional
    public EquipoVinculado vincularEquipo(long id, SesionActiva actor) {
        Caja caja = obtener(id);
        if (!caja.activo()) {
            throw rechazar(actor, "EQUIPO_VINCULADO", id, "CAJA_INACTIVA", "No se puede vincular un equipo a una caja inactiva.");
        }
        String token = TokenSesion.generar();
        cajas.vincularEquipo(id, TokenSesion.hash(token));
        bitacora.registrar(evento(actor, "EQUIPO_VINCULADO", true,
                "Equipo vinculado a la caja '%s'%s".formatted(caja.nombre(),
                        caja.equipoVinculado() ? " (reemplaza al equipo anterior)" : ""), id, null));
        return new EquipoVinculado(obtener(id), token);
    }

    private Caja cambiarActivo(Caja caja, boolean activo, SesionActiva actor) {
        cajas.cambiarActivo(caja.id(), activo);
        bitacora.registrar(evento(actor, activo ? "CAJA_ACTIVADA" : "CAJA_DESACTIVADA", true,
                "Caja '%s' %s".formatted(caja.nombre(), activo ? "activada" : "desactivada"), caja.id(),
                Map.of("activo", EventoBitacora.cambio(!activo, activo))));
        return obtener(caja.id());
    }

    private void validar(String nombre, String serie, Long id, SesionActiva actor, String accion) {
        if (!ReglasCaja.esNombreValido(nombre)) {
            throw rechazar(actor, accion, id, "NOMBRE_CAJA_INVALIDO", "El nombre de la caja debe tener de 1 a 40 caracteres.");
        }
        if (!ReglasCaja.esSerieValida(serie)) {
            throw rechazar(actor, accion, id, "SERIE_INVALIDA",
                    "La serie de facturación debe tener de 1 a 20 letras o números, sin espacios.");
        }
        if (cajas.existeNombre(nombre, id)) {
            throw rechazar(actor, accion, id, "CAJA_DUPLICADA", "Ya existe una caja con ese nombre.");
        }
    }

    /** {@link EventoBitacora#cambio} no admite nulos; una serie vacía se registra como texto vacío. */
    private static Map<String, Object> cambioNulable(String antes, String despues) {
        return EventoBitacora.cambio(antes == null ? "" : antes, despues == null ? "" : despues);
    }

    private static RecursoNoEncontradoException noEncontrada() {
        return new RecursoNoEncontradoException("CAJA_NO_ENCONTRADA", "La caja no existe.");
    }

    private static EventoBitacora evento(SesionActiva actor, String accion, boolean exitoso, String descripcion,
                                         Long cajaId, Map<String, Object> cambios) {
        return new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, accion, exitoso, descripcion,
                cajaId == null ? null : "caja", cajaId, cambios);
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva actor, String accion, Long cajaId, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(evento(actor, accion, false, "Operación sobre caja rechazada: " + mensaje,
                cajaId, null));
        return new NegocioException(codigo, mensaje);
    }
}
