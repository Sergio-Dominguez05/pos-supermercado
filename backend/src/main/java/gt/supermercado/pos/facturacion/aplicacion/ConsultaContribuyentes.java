package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.Contribuyente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * HU-05.04: el nombre del contribuyente según el certificador FEL. La consulta tiene un límite corto
 * (Architecture.md §7.2, ≈3 s) para no detener la venta: si vence, se trata como no disponible (crit. 4).
 */
@Service
public class ConsultaContribuyentes {

    static final Duration LIMITE = Duration.ofSeconds(3);

    private static final Logger LOG = LoggerFactory.getLogger(ConsultaContribuyentes.class);

    private final CertificadorFel certificador;

    public ConsultaContribuyentes(CertificadorFel certificador) {
        this.certificador = certificador;
    }

    /** Cómo terminó la consulta: con el nombre, sin el NIT en la SAT, o sin respuesta del servicio. */
    public enum Resultado { ENCONTRADO, NO_EXISTE, NO_DISPONIBLE }

    public record ConsultaNit(Resultado resultado, String nombre) {}

    /** {@code nit} ya normalizado y válido. Nunca lanza: cualquier falla del servicio es "no disponible". */
    public ConsultaNit porNit(String nit) {
        return consultar(() -> certificador.consultarNit(nit), "NIT");
    }

    /** HU-05.06 crit. 3: el nombre por CUI ({@code cui} con sus 13 dígitos). Nunca lanza, como {@link #porNit}. */
    public ConsultaNit porCui(String cui) {
        return consultar(() -> certificador.consultarCui(cui), "CUI");
    }

    private ConsultaNit consultar(java.util.function.Supplier<Optional<Contribuyente>> consulta, String tipo) {
        try {
            Optional<Contribuyente> contribuyente = LlamadaConLimite.conLimite(consulta, LIMITE);
            return contribuyente.filter(c -> c.nombre() != null && !c.nombre().isBlank())
                    .map(c -> new ConsultaNit(Resultado.ENCONTRADO, c.nombre().strip()))
                    .orElse(new ConsultaNit(Resultado.NO_EXISTE, null));
        } catch (CertificadorNoDisponibleException e) {
            return new ConsultaNit(Resultado.NO_DISPONIBLE, null);
        } catch (RuntimeException e) {
            LOG.warn("Falló la consulta del {} al certificador", tipo, e);
            return new ConsultaNit(Resultado.NO_DISPONIBLE, null);
        }
    }
}
