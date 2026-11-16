package gt.supermercado.pos.caja.dominio;

import java.util.Optional;

/** Turnos de caja. */
public interface RepositorioTurnos {

    Optional<Turno> buscarPorId(long id);

    Optional<Turno> abiertoEnCaja(long cajaId);

    Optional<Turno> abiertoDeUsuario(long usuarioId);

    /** Abre el turno con la hora del servidor y devuelve su id. */
    long abrir(long cajaId, long usuarioId);
}
