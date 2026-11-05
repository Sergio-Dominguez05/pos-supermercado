package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.DirectorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.Usuario;
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

/** Reglas de HU-01.08 que la API todavía no puede producir: caja abierta (crit. 3) y último Administrador (crit. 5). */
class EstadoUsuariosTest {

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final List<EventoBitacora> eventos = new ArrayList<>();
    private final Set<Long> conCajaAbierta = new HashSet<>();
    private final EstadoUsuarios estado =
            new EstadoUsuarios(usuarios, usuarios.directorio(), conCajaAbierta::contains, new Bitacora(eventos::add));

    @Test
    void noSeDesactivaAUnUsuarioConCajaAbierta() {
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "cajera", Rol.CAJERO, true);
        conCajaAbierta.add(2L);

        assertThatThrownBy(() -> estado.desactivar(2, actor(1)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("El usuario tiene una caja abierta: primero se debe cerrar la caja.")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("CAJA_ABIERTA");
        assertThat(usuarios.activo(2)).isTrue();
        assertThat(eventos).singleElement().satisfies(e -> assertThat(e.exitoso()).isFalse());
    }

    @Test
    void sinCajaAbiertaSeDesactivaYQuedaEnLaBitacora() {
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "cajera", Rol.CAJERO, true);

        assertThat(estado.desactivar(2, actor(1)).activo()).isFalse();
        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo("USUARIO_DESACTIVADO");
            assertThat(e.cambios()).isEqualTo(Map.of("activo", Map.of("antes", true, "despues", false)));
        });
    }

    @Test
    void noSeDesactivaAlUltimoAdministradorActivo() {
        // Solo posible si otro rol tuviera el permiso de administrar usuarios; la regla protege igual.
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "supervisor", Rol.SUPERVISOR, true);

        assertThatThrownBy(() -> estado.desactivar(1, actor(2)))
                .hasMessage("No se puede desactivar al último Administrador activo.");
        assertThat(usuarios.activo(1)).isTrue();
    }

    @Test
    void conOtroAdministradorActivoSiSePuedeDesactivarUnAdministrador() {
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "admin2", Rol.ADMIN, true);

        assertThat(estado.desactivar(2, actor(1)).activo()).isFalse();
    }

    @Test
    void nadiePuedeDesactivarSuPropioUsuario() {
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "admin2", Rol.ADMIN, true);

        assertThatThrownBy(() -> estado.desactivar(1, actor(1))).hasMessage("No puede desactivar su propio usuario.");
    }

    @Test
    void activarODesactivarSinCambioDeEstadoNoRegistraNada() {
        usuarios.agregar(1, "admin", Rol.ADMIN, true);
        usuarios.agregar(2, "cajera", Rol.CAJERO, false);

        estado.desactivar(2, actor(1));
        estado.activar(1, actor(1));

        assertThat(eventos).isEmpty();
    }

    private static SesionActiva actor(long usuarioId) {
        return new SesionActiva(99, usuarioId, "actor", "Actor", Rol.ADMIN, "Administrador", false, Set.of());
    }

    /** Doble en memoria del repositorio; {@link #directorio()} lee los mismos datos. */
    private static final class UsuariosEnMemoria implements RepositorioUsuarios {

        private final Map<Long, Usuario> porId = new LinkedHashMap<>();

        void agregar(long id, String username, Rol rol, boolean activo) {
            porId.put(id, new Usuario(id, username, username, rol, "hash", false, activo));
        }

        boolean activo(long id) {
            return porId.get(id).activo();
        }

        @Override
        public Optional<Usuario> buscarPorId(long id) {
            return Optional.ofNullable(porId.get(id));
        }

        @Override
        public long contarAdministradoresActivos() {
            return porId.values().stream().filter(u -> u.rol() == Rol.ADMIN && u.activo()).count();
        }

        @Override
        public void cambiarActivo(long id, boolean activo) {
            Usuario u = porId.get(id);
            porId.put(id, new Usuario(u.id(), u.username(), u.nombreCompleto(), u.rol(), u.credencialHash(),
                    u.credencialTemporal(), activo));
        }

        DirectorioUsuarios directorio() {
            return new DirectorioUsuarios() {
                @Override
                public Optional<ResumenUsuario> buscarPorId(long id) {
                    return UsuariosEnMemoria.this.buscarPorId(id).map(u -> new ResumenUsuario(u.id(), u.username(),
                            u.nombreCompleto(), u.rol(), u.rol().name(), u.activo(), null));
                }

                @Override
                public List<ResumenUsuario> buscar(String texto, Rol rol, Boolean activo) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public Optional<Usuario> buscarPorUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existeAlgunoConRol(Rol rol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long crear(String username, String nombreCompleto, Rol rol, String credencialHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long id, String nombreCompleto, Rol rol, String credencialHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registrarAcceso(long usuarioId) {
            throw new UnsupportedOperationException();
        }
    }
}
