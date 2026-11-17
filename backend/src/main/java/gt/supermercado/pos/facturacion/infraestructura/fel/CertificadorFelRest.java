package gt.supermercado.pos.facturacion.infraestructura.fel;

import gt.supermercado.pos.facturacion.aplicacion.CertificadorFel;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConexionFel;
import gt.supermercado.pos.facturacion.dominio.Contribuyente;
import gt.supermercado.pos.facturacion.dominio.ResultadoAnulacion;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador REST del certificador FEL (Architecture.md §7.2), con {@code pos.fel.modo=rest}.
 *
 * <p>El certificador todavía no se elige (Architecture.md §12.2, punto 3), así que este adaptador es genérico:
 * <ul>
 *   <li>HU-08.01 crit. 2: solo habla por {@code https} (TLS, con la validación de certificados de Java).</li>
 *   <li>HU-08.01 crit. 3: la prueba de conexión hace un {@code GET} autenticado a la dirección del servicio, sin
 *       emitir facturas, con el tiempo límite configurado.</li>
 *   <li>Certificar, consultar, anular y el resto responden que falta elegir el certificador: cada uno tiene su API.
 *       Al elegirlo, se implementan aquí con su documentación (HU-08.03 en adelante).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "pos.fel.modo", havingValue = "rest")
public class CertificadorFelRest implements CertificadorFel {

    private static final String PENDIENTE =
            "El adaptador REST todavía no implementa esta operación: falta elegir el certificador (Architecture.md §12.2).";

    @Override
    public ResultadoPruebaConexion probarConexion(ConexionFel conexion) {
        URI direccion = URI.create(conexion.urlServicio());
        if (!"https".equalsIgnoreCase(direccion.getScheme())) {
            return ResultadoPruebaConexion.fallida("La dirección del servicio debe ser https:// (canal cifrado).");
        }
        Duration limite = Duration.ofMillis(conexion.timeoutMs());
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(limite)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest peticion = HttpRequest.newBuilder(direccion).timeout(limite).GET()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (conexion.usuario() + ":" + conexion.secreto()).getBytes(StandardCharsets.UTF_8)))
                .build();
        try {
            return resultado(cliente.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode());
        } catch (HttpTimeoutException e) {
            throw new CertificadorNoDisponibleException(
                    "no respondió en %d segundos.".formatted(conexion.timeoutMs() / 1000));
        } catch (IOException e) {
            throw new CertificadorNoDisponibleException("no se pudo conectar con " + direccion.getHost() + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CertificadorNoDisponibleException("se interrumpió la prueba de conexión.");
        }
    }

    /** Cómo se lee la respuesta del certificador a la prueba de conexión. */
    static ResultadoPruebaConexion resultado(int estado) {
        if (estado >= 200 && estado < 300) {
            return ResultadoPruebaConexion.exitosa("Conexión exitosa con el certificador.");
        }
        if (estado == 401 || estado == 403) {
            return ResultadoPruebaConexion.fallida("El certificador rechazó el usuario o la llave (HTTP %d)."
                    .formatted(estado));
        }
        return ResultadoPruebaConexion.fallida("El certificador respondió con un error (HTTP %d): revise la dirección."
                .formatted(estado));
    }

    @Override
    public ResultadoCertificacion certificar(String xmlDte, UUID identificadorUnico) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Optional<ResultadoCertificacion> consultarPorIdentificador(UUID identificadorUnico) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public ResultadoAnulacion anular(UUID autorizacion, String motivo) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Optional<Contribuyente> consultarNit(String nit) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Optional<Contribuyente> consultarCui(String cui) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public void solicitarEnvioCorreo(UUID autorizacion, String correo) {
        throw new UnsupportedOperationException(PENDIENTE);
    }
}
