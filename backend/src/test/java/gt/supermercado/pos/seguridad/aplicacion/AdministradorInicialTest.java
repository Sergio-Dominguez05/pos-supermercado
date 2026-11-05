package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministradorInicialTest {

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final List<EventoBitacora> eventos = new ArrayList<>();
    private final Bitacora bitacora = new Bitacora(eventos::add);

    @Test
    void creaElAdministradorConLaContrasenaCodificadaYLoRegistraEnLaBitacora() {
        administrador("Inicial2026").run(null);

        assertThat(usuarios.creados).containsExactly("admin|ADMIN|codificada:Inicial2026");
        assertThat(eventos).singleElement()
                .satisfies(e -> assertThat(e.accion()).isEqualTo("USUARIO_CREADO"))
                .satisfies(e -> assertThat(e.descripcion()).doesNotContain("Inicial2026"));
    }

    @Test
    void noHaceNadaSiYaExisteUnAdministrador() {
        usuarios.hayAdministrador = true;

        administrador("Inicial2026").run(null);

        assertThat(usuarios.creados).isEmpty();
        assertThat(eventos).isEmpty();
    }

    @Test
    void sinContrasenaConfiguradaNoCreaNingunUsuario() {
        administrador("").run(null);

        assertThat(usuarios.creados).isEmpty();
    }

    @Test
    void unaContrasenaDebilDetieneElArranque() {
        assertThatThrownBy(() -> administrador("corta1").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> administrador("sinnumeros").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> administrador("12345678").run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(usuarios.creados).isEmpty();
    }

    private AdministradorInicial administrador(String contrasena) {
        return new AdministradorInicial(contrasena, usuarios, new CodificadorDePrueba(), bitacora);
    }

    private static final class UsuariosEnMemoria implements RepositorioUsuarios {

        boolean hayAdministrador;
        final List<String> creados = new ArrayList<>();

        @Override
        public Optional<Usuario> buscarPorUsername(String username) {
            return Optional.empty();
        }

        @Override
        public boolean existeAlgunoConRol(Rol rol) {
            return rol == Rol.ADMIN && hayAdministrador;
        }

        @Override
        public long crear(String username, String nombreCompleto, Rol rol, String credencialHash) {
            creados.add(username + "|" + rol + "|" + credencialHash);
            return creados.size();
        }

        @Override
        public Optional<Usuario> buscarPorId(long id) {
            return Optional.empty();
        }

        @Override
        public long contarAdministradoresActivos() {
            return hayAdministrador ? 1 : 0;
        }

        @Override
        public void actualizar(long id, String nombreCompleto, Rol rol, String credencialHash) {
        }

        @Override
        public void cambiarCredencial(long id, String credencialHash) {
        }

        @Override
        public void restablecerCredencial(long id, String credencialHash) {
        }

        @Override
        public void cambiarActivo(long id, boolean activo) {
        }

        @Override
        public void registrarAcceso(long usuarioId) {
        }
    }

    private static final class CodificadorDePrueba implements PasswordEncoder {

        @Override
        public String encode(CharSequence valor) {
            return "codificada:" + valor;
        }

        @Override
        public boolean matches(CharSequence valor, String codificado) {
            return encode(valor).equals(codificado);
        }
    }
}
