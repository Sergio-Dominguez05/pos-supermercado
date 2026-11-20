package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.facturacion.dominio.MotivoRechazo.Tipo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-08.06 crit. 1: el error del certificador en lenguaje comprensible, y si se corrige en caja (crit. 2 y 3). */
class TraductorErroresTest {

    @Test
    void unErrorDelReceptorSeCorrigeEnCaja() {
        MotivoRechazo motivo = TraductorErrores.traducir("E-401", "El NIT del receptor no está inscrito en la SAT");

        assertThat(motivo.tipo()).isEqualTo(Tipo.RECEPTOR);
        assertThat(motivo.corregibleEnCaja()).isTrue();
        assertThat(motivo.mensaje()).isEqualTo("El NIT o el nombre del cliente no es válido para la SAT. Corrija el "
                + "cliente y reenvíe la factura; no hace falta volver a cobrar.");
        assertThat(motivo.detalle()).isEqualTo("El NIT del receptor no está inscrito en la SAT");
        assertThat(motivo.codigo()).isEqualTo("E-401");
    }

    @Test
    void unaDiferenciaDeMontosQuedaParaElSupervisor() {   // y HU-04.09 crit. 2
        MotivoRechazo motivo = TraductorErrores.traducir("E-210", "La suma de los ítems no cuadra con el Gran Total");

        assertThat(motivo.tipo()).isEqualTo(Tipo.MONTOS);
        assertThat(motivo.corregibleEnCaja()).isFalse();
        assertThat(motivo.mensaje()).contains("Los montos de la factura no coinciden", "avise a su supervisor");
    }

    @Test
    void elNitDelEmisorEsUnErrorDelEmisorNoDelCliente() {
        MotivoRechazo motivo = TraductorErrores.traducir("E-101", "El NIT del emisor no tiene afiliación vigente");

        assertThat(motivo.tipo()).isEqualTo(Tipo.EMISOR);
        assertThat(motivo.corregibleEnCaja()).isFalse();
    }

    @Test
    void lasPalabrasSeReconocenSinTildesNiMayusculasYCompletas() {
        assertThat(TraductorErrores.traducir(null, "AFILIACIÓN AL IVA incorrecta").tipo()).isEqualTo(Tipo.EMISOR);
        assertThat(TraductorErrores.traducir(null, "MONTO GRAVABLE inválido en la línea 3").tipo()).isEqualTo(Tipo.MONTOS);
        // "inactiva" contiene "iva", pero no es un error de montos.
        assertThat(TraductorErrores.traducir(null, "La cuenta del usuario está inactiva").tipo()).isEqualTo(Tipo.OTRO);
    }

    @Test
    void unErrorDesconocidoMuestraElMensajeDelCertificador() {
        MotivoRechazo motivo = TraductorErrores.traducir("X-999", "Servicio en mantenimiento");

        assertThat(motivo.tipo()).isEqualTo(Tipo.OTRO);
        assertThat(motivo.mensaje()).isEqualTo("El certificador rechazó la factura (Servicio en mantenimiento). La venta "
                + "queda pendiente de facturar: avise a su supervisor.");
    }

    @Test
    void losCodigosDelSimuladorSeClasificanPorCodigo() {
        assertThat(TraductorErrores.traducir("SIM-001", "cualquier texto").tipo()).isEqualTo(Tipo.RECEPTOR);
        assertThat(TraductorErrores.traducir("SIM-002", "cualquier texto").tipo()).isEqualTo(Tipo.MONTOS);
    }
}
