package gt.supermercado.pos.caja.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-09.02 Impedir ventas sin caja abierta: la regla que usarán ventas, pagos y facturación antes de registrar un
 * cobro. Esos módulos todavía no existen (EP-06 a EP-08), así que se prueba directamente sobre el caso de uso.
 */
class GestionTurnosTest {

    private static final long CAJA = 5;
    private static final long CAJERA = 20;

    private final TurnosEnMemoria turnos = new TurnosEnMemoria();
    // exigirTurnoAbierto no consulta las cajas ni escribe en la bitácora.
    private final GestionTurnos gestion = new GestionTurnos(turnos, null, new Bitacora(evento -> {
        throw new AssertionError("No debe registrar en la bitácora: " + evento);
    }));

    @Test
    void conElTurnoPropioAbiertoDevuelveEseTurnoParaAsociarleLaVenta() {   // crit. 2
        Turno abierto = turnos.agregar(CAJA, CAJERA, "Ana López");

        assertThat(gestion.exigirTurnoAbierto(sesion(CAJERA, CAJA))).isEqualTo(abierto);
    }

    @Test
    void conLaCajaCerradaSeRechazaLaVenta() {   // crit. 1
        assertThatThrownBy(() -> gestion.exigirTurnoAbierto(sesion(CAJERA, CAJA)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La caja está cerrada. Abra su turno para empezar a vender.")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("CAJA_CERRADA");
    }

    @Test
    void sinCajaVinculadaTampocoSePuedeVender() {
        assertThatThrownBy(() -> gestion.exigirTurnoAbierto(sesion(CAJERA, null)))
                .isInstanceOf(NegocioException.class)
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("CAJA_CERRADA");
    }

    @Test
    void noSeVendeEnElTurnoDeOtroUsuario() {
        turnos.agregar(CAJA, 99, "Luis Pérez");

        assertThatThrownBy(() -> gestion.exigirTurnoAbierto(sesion(CAJERA, CAJA)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Luis Pérez")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("CAJA_CON_TURNO_DE_OTRO_USUARIO");
    }

    private static SesionActiva sesion(long usuarioId, Long cajaId) {
        return new SesionActiva(1, usuarioId, "ana", "Ana López", Rol.CAJERO, "Cajero", false,
                Set.of("VENTA_REGISTRAR", "CAJA_OPERAR_PROPIA"), cajaId, cajaId == null ? null : "Caja 1");
    }

    /** Doble en memoria con lo necesario para consultar turnos abiertos. */
    private static final class TurnosEnMemoria implements RepositorioTurnos {

        private final List<Turno> abiertos = new ArrayList<>();

        Turno agregar(long cajaId, long usuarioId, String usuarioNombre) {
            Turno turno = new Turno(abiertos.size() + 1L, cajaId, "Caja 1", usuarioId, usuarioNombre,
                    EstadoTurno.ABIERTO, OffsetDateTime.now());
            abiertos.add(turno);
            return turno;
        }

        @Override
        public Optional<Turno> buscarPorId(long id) {
            return abiertos.stream().filter(t -> t.id() == id).findFirst();
        }

        @Override
        public Optional<Turno> abiertoEnCaja(long cajaId) {
            return abiertos.stream().filter(t -> t.cajaId() == cajaId).findFirst();
        }

        @Override
        public Optional<Turno> abiertoDeUsuario(long usuarioId) {
            return abiertos.stream().filter(t -> t.usuarioId() == usuarioId).findFirst();
        }

        @Override
        public long abrir(long cajaId, long usuarioId) {
            throw new UnsupportedOperationException();
        }
    }
}
