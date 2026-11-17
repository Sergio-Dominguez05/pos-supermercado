package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * HU-08.03 crit. 3: la certificación no debe demorar la atención. Cualquier llamada al certificador se corta al
 * vencer el tiempo límite configurado (HU-12.03), aunque el adaptador no respete su propio límite.
 */
final class LlamadaConLimite {

    private static final ExecutorService EJECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private LlamadaConLimite() {
    }

    /** El resultado de la llamada, o {@link CertificadorNoDisponibleException} si no responde dentro del límite. */
    static <T> T conLimite(Supplier<T> llamada, Duration limite) {
        CompletableFuture<T> resultado = CompletableFuture.supplyAsync(llamada, EJECUTOR);
        try {
            return resultado.get(limite.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            resultado.cancel(true);
            throw new CertificadorNoDisponibleException(
                    "El certificador no respondió en %s.".formatted(segundos(limite)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CertificadorNoDisponibleException("Se interrumpió la espera del certificador.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private static String segundos(Duration limite) {
        long milisegundos = limite.toMillis();
        if (milisegundos % 1000 != 0) {
            return milisegundos + " ms";
        }
        return milisegundos == 1000 ? "1 segundo" : (milisegundos / 1000) + " segundos";
    }
}
