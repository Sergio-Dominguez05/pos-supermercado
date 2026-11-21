package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.LogoTicket;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-12.09 crit. 1: el logo se guarda en blanco y negro, del tamaño que imprime el papel. */
class LogoMonocromoTest {

    @Test
    void convierteAUnBitPorPuntoYCompletaElAnchoAMultiploDeOcho() throws Exception {
        BufferedImage imagen = new BufferedImage(10, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 10; x++) {
                // Izquierda negra, centro gris claro (blanco) y derecha transparente (blanco).
                imagen.setRGB(x, y, x < 3 ? 0xFF000000 : x < 6 ? 0xFFDDDDDD : 0x00000000);
            }
        }

        LogoTicket logo = LogoMonocromo.desdeBase64(base64(imagen));

        assertThat(logo.ancho()).isEqualTo(16);
        assertThat(logo.alto()).isEqualTo(4);
        // La imagen de 10 puntos queda centrada en 16: empieza en el punto 3.
        assertThat(logo.negro(3, 0)).isTrue();
        assertThat(logo.negro(5, 0)).isTrue();
        assertThat(logo.negro(6, 0)).isFalse();
        assertThat(logo.negro(12, 3)).isFalse();
        assertThat(logo.negro(0, 0)).isFalse();
    }

    @Test
    void unaImagenGrandeSeEscalaSinDeformarse() throws Exception {
        BufferedImage grande = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);

        LogoTicket logo = LogoMonocromo.desdeBase64("data:image/png;base64," + base64(grande));

        assertThat(logo.ancho()).isEqualTo(384);
        assertThat(logo.alto()).isEqualTo(192);
    }

    @Test
    void rechazaLoQueNoEsUnaImagen() {
        assertThatThrownBy(() -> LogoMonocromo.desdeBase64("bm8gZXMgdW5hIGltYWdlbg=="))
                .isInstanceOf(NegocioException.class).hasMessageContaining("PNG, JPG");
        assertThatThrownBy(() -> LogoMonocromo.desdeBase64("%%%"))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void elPngDeLaVistaPreviaEsElMismoLogoEnBlancoYNegro() throws Exception {
        LogoTicket logo = new LogoTicket(8, 1, new byte[] {(byte) 0b1010_0000});

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(LogoMonocromo.png(logo)));

        assertThat(png.getWidth()).isEqualTo(8);
        assertThat(png.getRGB(0, 0) & 0xFFFFFF).isZero();
        assertThat(png.getRGB(1, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
        assertThat(LogoMonocromo.dataUrl(logo)).startsWith("data:image/png;base64,");
        assertThat(LogoMonocromo.dataUrl(null)).isNull();
    }

    private static String base64(BufferedImage imagen) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return Base64.getEncoder().encodeToString(salida.toByteArray());
    }
}
