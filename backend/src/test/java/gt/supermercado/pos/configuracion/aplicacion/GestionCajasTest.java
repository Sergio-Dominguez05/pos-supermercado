package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.Caja;
import gt.supermercado.pos.configuracion.dominio.RepositorioCajas;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regla de HU-12.05 que todavía no se puede producir por la API: el turno abierto (crit. 3). */
class GestionCajasTest {

    private final CajasEnMemoria cajas = new CajasEnMemoria();
    private final Set<Long> conTurnoAbierto = new HashSet<>();
    private final List<EventoBitacora> eventos = new ArrayList<>();
    private final GestionCajas gestion = new GestionCajas(cajas, conTurnoAbierto::contains, new Bitacora(eventos::add));
    private final SesionActiva admin = new SesionActiva(1, 1, "admin", "Administrador", Rol.ADMIN, "Administrador",
            false, Set.of());

    @Test
    void noSeDesactivaUnaCajaConTurnoAbierto() {
        cajas.agregar(5, "Caja 1");
        conTurnoAbierto.add(5L);

        assertThatThrownBy(() -> gestion.desactivar(5, admin))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La caja tiene un turno abierto: primero se debe cerrar el turno.")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("CAJA_CON_TURNO_ABIERTO");
        assertThat(cajas.buscarPorId(5).orElseThrow().activo()).isTrue();
        assertThat(eventos).singleElement().satisfies(e -> assertThat(e.exitoso()).isFalse());
    }

    @Test
    void sinTurnoAbiertoSeDesactiva() {
        cajas.agregar(5, "Caja 1");

        assertThat(gestion.desactivar(5, admin).activo()).isFalse();
        assertThat(eventos).singleElement().satisfies(e -> assertThat(e.accion()).isEqualTo("CAJA_DESACTIVADA"));
    }

    /** Doble en memoria con lo necesario para activar y desactivar. */
    private static final class CajasEnMemoria implements RepositorioCajas {

        private final Map<Long, Caja> porId = new LinkedHashMap<>();

        void agregar(long id, String nombre) {
            porId.put(id, new Caja(id, nombre, null, true, false));
        }

        @Override
        public Optional<Caja> buscarPorId(long id) {
            return Optional.ofNullable(porId.get(id));
        }

        @Override
        public void cambiarActivo(long id, boolean activo) {
            Caja c = porId.get(id);
            porId.put(id, new Caja(c.id(), c.nombre(), c.serieFel(), activo, c.equipoVinculado()));
        }

        @Override
        public List<Caja> listar() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Caja> buscarPorEquipo(byte[] tokenHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existeNombre(String nombre, Long excluirId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long crear(String nombre, String serieFel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long id, String nombre, String serieFel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void vincularEquipo(long id, byte[] tokenHash) {
            throw new UnsupportedOperationException();
        }
    }
}
