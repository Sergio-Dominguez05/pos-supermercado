package gt.supermercado.pos.compartido.infraestructura;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-12.15 crit. 1: las credenciales se guardan cifradas con AES-256-GCM. */
class CifradorAesGcmTest {

    private static final String LLAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTRA_LLAVE = Base64.getEncoder().encodeToString("una llave distinta de 32 bytes!!"
            .getBytes(StandardCharsets.UTF_8));

    private final CifradorAesGcm cifrador = new CifradorAesGcm(LLAVE);

    @Test
    void loCifradoSeDescifraIgualYNoContieneElSecreto() {
        byte[] cifrado = cifrador.cifrar("token-secreto-ñ");

        assertThat(cifrador.descifrar(cifrado)).isEqualTo("token-secreto-ñ");
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain("token-secreto");
    }

    @Test
    void cadaCifradoUsaOtroVector() {
        assertThat(cifrador.cifrar("mismo")).isNotEqualTo(cifrador.cifrar("mismo"));
    }

    @Test
    void unSecretoAlteradoOCifradoConOtraLlaveNoSeDescifra() {
        byte[] cifrado = cifrador.cifrar("token");
        cifrado[cifrado.length - 1] ^= 1;

        assertThatThrownBy(() -> cifrador.descifrar(cifrado)).hasMessageContaining("se alteró");
        assertThatThrownBy(() -> new CifradorAesGcm(OTRA_LLAVE).descifrar(cifrador.cifrar("token")))
                .hasMessageContaining("otra llave");
    }

    @Test
    void sinLlaveArrancaPeroNoCifraYUnaLlaveInvalidaSeRechazaAlArrancar() {
        assertThatThrownBy(() -> new CifradorAesGcm("").cifrar("token")).hasMessageContaining("POS_LLAVE_MAESTRA");
        assertThatThrownBy(() -> new CifradorAesGcm("no-es-base64!")).hasMessageContaining("Base64");
        assertThatThrownBy(() -> new CifradorAesGcm(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
    }
}
