package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.ConflictoException;
import gt.supermercado.pos.seguridad.dominio.RepositorioSesiones;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regla de HU-01.16 que todavía no se puede producir por la API: la venta en curso (crit. 2). */
class CierreSesionTest {

    private final List<Long> sesionesCerradas = new ArrayList<>();
    private final List<EventoBitacora> eventos = new ArrayList<>();
    private final SesionActiva sesion =
            new SesionActiva(10, 3, "cajera", "Cajera", Rol.CAJERO, "Cajero", false, Set.of());

    @Test
    void conUnaVentaEnCursoNoSeCierraLaSesionYSeAvisaQueHacer() {
        CierreSesion cierre = cierre(true);

        assertThatThrownBy(() -> cierre.cerrar(sesion))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("Tiene una venta en curso: finalícela, suspéndala o cancélela antes de cerrar sesión.")
                .extracting(e -> ((ConflictoException) e).getCodigo()).isEqualTo("VENTA_EN_CURSO");
        assertThat(sesionesCerradas).isEmpty();
        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo("CIERRE_SESION");
            assertThat(e.exitoso()).isFalse();
        });
    }

    @Test
    void sinVentaEnCursoSeCierraSoloEstaSesionYQuedaEnLaBitacora() {
        cierre(false).cerrar(sesion);

        assertThat(sesionesCerradas).containsExactly(10L);
        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo("CIERRE_SESION");
            assertThat(e.exitoso()).isTrue();
            assertThat(e.usuarioId()).isEqualTo(3L);
        });
    }

    private CierreSesion cierre(boolean ventaEnCurso) {
        RepositorioSesiones sesiones = new RepositorioSesiones() {
            @Override
            public long abrir(byte[] tokenHash, long usuarioId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<SesionActiva> buscarActiva(byte[] tokenHash) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void cerrar(long sesionId) {
                sesionesCerradas.add(sesionId);
            }
        };
        return new CierreSesion(sesiones, usuarioId -> ventaEnCurso, new Bitacora(eventos::add));
    }
}
