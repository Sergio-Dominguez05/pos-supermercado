package gt.supermercado.pos.compartido.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11.04: el permiso que exigía la acción rechazada, sacado de su regla de autorización. */
class PermisosExigidosTest {

    @Test
    void unPermiso() {
        assertThat(PermisosExigidos.de("hasAuthority('FACTURA_ANULAR')")).containsExactly("FACTURA_ANULAR");
    }

    @Test
    void variosPermisosAlternativos() {
        assertThat(PermisosExigidos.de("hasAuthority('CAJA_OPERAR_PROPIA') or hasAuthority('CAJA_VER_CIERRES')"))
                .containsExactly("CAJA_OPERAR_PROPIA", "CAJA_VER_CIERRES");
    }

    @Test
    void sinReglaNiPermisosNoHayNada() {
        assertThat(PermisosExigidos.de(null)).isEmpty();
        assertThat(PermisosExigidos.de("isAuthenticated()")).isEmpty();
    }
}
