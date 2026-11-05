package gt.supermercado.pos.seguridad.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasCredencialTest {

    @Test
    void elNombreDeUsuarioSeNormalizaSinEspaciosYEnMinusculas() {
        assertThat(ReglasCredencial.normalizarUsername("  Ana.Lopez ")).isEqualTo("ana.lopez");
    }

    @Test
    void tambienSeQuitanLosEspaciosIntermediosYCualquierTipoDeEspacio() {   // #136
        assertThat(ReglasCredencial.normalizarUsername(" A d   Mi  N")).isEqualTo("admin");
        assertThat(ReglasCredencial.normalizarUsername("ana	.lopez ")).isEqualTo("ana.lopez");
        assertThat(ReglasCredencial.normalizarUsername("   ")).isEmpty();
    }

    @Test
    void elNombreDeUsuarioTieneDe3A30CaracteresPermitidos() {
        assertThat(ReglasCredencial.esUsernameValido("ana")).isTrue();
        assertThat(ReglasCredencial.esUsernameValido("cajero_01-turno.b")).isTrue();
        assertThat(ReglasCredencial.esUsernameValido("a".repeat(30))).isTrue();

        assertThat(ReglasCredencial.esUsernameValido("ab")).isFalse();
        assertThat(ReglasCredencial.esUsernameValido("a".repeat(31))).isFalse();
        assertThat(ReglasCredencial.esUsernameValido("ana lopez")).isFalse();
        assertThat(ReglasCredencial.esUsernameValido("josé")).isFalse();
        assertThat(ReglasCredencial.esUsernameValido("Ana")).isFalse();
    }

    @Test
    void laContrasenaTieneAlMenos8CaracteresConLetrasYNumeros() {
        assertThat(ReglasCredencial.esContrasenaValida("Clave2026")).isTrue();
        assertThat(ReglasCredencial.esContrasenaValida("abcd1234")).isTrue();

        assertThat(ReglasCredencial.esContrasenaValida("abc1234")).isFalse();
        assertThat(ReglasCredencial.esContrasenaValida("soloLetras")).isFalse();
        assertThat(ReglasCredencial.esContrasenaValida("12345678")).isFalse();
        assertThat(ReglasCredencial.esContrasenaValida("a1".repeat(65))).isFalse();
    }

    @Test
    void elPinEsNumericoDe4A6Digitos() {
        assertThat(ReglasCredencial.esPinValido("1234")).isTrue();
        assertThat(ReglasCredencial.esPinValido("123456")).isTrue();

        assertThat(ReglasCredencial.esPinValido("123")).isFalse();
        assertThat(ReglasCredencial.esPinValido("1234567")).isFalse();
        assertThat(ReglasCredencial.esPinValido("12a4")).isFalse();
    }

    @Test
    void cadaRolUsaSuTipoDeCredencial() {
        assertThat(ReglasCredencial.esCredencialValida(TipoCredencial.de(Rol.CAJERO), "4821")).isTrue();
        assertThat(ReglasCredencial.esCredencialValida(TipoCredencial.de(Rol.CAJERO), "Clave2026")).isFalse();
        assertThat(ReglasCredencial.esCredencialValida(TipoCredencial.de(Rol.SUPERVISOR), "4821")).isFalse();
        assertThat(ReglasCredencial.esCredencialValida(TipoCredencial.de(Rol.ADMIN), "Clave2026")).isTrue();
    }
}
