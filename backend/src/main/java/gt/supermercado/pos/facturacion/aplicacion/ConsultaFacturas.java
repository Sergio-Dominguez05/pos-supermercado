package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Consultas de facturas para otros módulos: por ahora, la factura impresa (HU-08.05). */
@Service
public class ConsultaFacturas {

    /** El nombre que se imprime mientras no se configure la conexión con un certificador (desarrollo y pruebas). */
    static final String SIN_CERTIFICADOR = "Certificador FEL";

    private final RepositorioFacturas facturas;
    private final GestionConexionFel conexion;

    public ConsultaFacturas(RepositorioFacturas facturas, GestionConexionFel conexion) {
        this.facturas = facturas;
        this.conexion = conexion;
    }

    /** El nombre y el NIT del certificador, que van en la factura impresa (HU-08.05 crit. 1). */
    public record Certificador(String nombre, String nit) {}

    @Transactional(readOnly = true)
    public Optional<FacturaImprimible> paraImprimir(long ventaId) {
        return facturas.imprimible(ventaId);
    }

    @Transactional(readOnly = true)
    public Certificador certificador() {
        return conexion.configuracion()
                .map(config -> new Certificador(config.certificador(), config.nitCertificador()))
                .orElse(new Certificador(SIN_CERTIFICADOR, null));
    }
}
