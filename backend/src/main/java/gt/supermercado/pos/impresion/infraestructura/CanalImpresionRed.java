package gt.supermercado.pos.impresion.infraestructura;

import gt.supermercado.pos.impresion.dominio.CanalImpresion;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Envía el ticket ESC/POS por TCP a la impresora de la red (puerto 9100, "raw printing"), sin instalar nada en las
 * cajas (Architecture.md §3). Los tiempos son cortos para no demorar la atención si la impresora está apagada.
 */
@Component
@ConditionalOnProperty(name = "pos.impresion.modo", havingValue = "red")
class CanalImpresionRed implements CanalImpresion {

    private static final int CONEXION_MS = 3000;
    private static final int ESCRITURA_MS = 5000;

    @Override
    public void enviar(String host, int puerto, byte[] datos) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), CONEXION_MS);
            socket.setSoTimeout(ESCRITURA_MS);
            OutputStream salida = socket.getOutputStream();
            salida.write(datos);
            salida.flush();
        } catch (IOException | IllegalArgumentException e) {
            throw new ImpresoraNoDisponibleException(
                    "La impresora %s:%d no respondió.".formatted(host, puerto), e);
        }
    }
}
