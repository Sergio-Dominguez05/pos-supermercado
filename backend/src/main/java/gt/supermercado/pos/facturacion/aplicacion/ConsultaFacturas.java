package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.facturacion.dominio.RepositorioAnulaciones;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Consultas de facturas para otros módulos: la factura impresa (HU-08.05) y las que se reimprimen (HU-08.12). */
@Service
public class ConsultaFacturas {

    /** El nombre que se imprime mientras no se configure la conexión con un certificador (desarrollo y pruebas). */
    static final String SIN_CERTIFICADOR = "Certificador FEL";
    static final int RESULTADOS_MAXIMOS = 50;

    private final RepositorioFacturas facturas;
    private final GestionConexionFel conexion;
    private final RepositorioAnulaciones emitidas;

    public ConsultaFacturas(RepositorioFacturas facturas, GestionConexionFel conexion,
                            RepositorioAnulaciones emitidas) {
        this.facturas = facturas;
        this.conexion = conexion;
        this.emitidas = emitidas;
    }

    /** HU-08.12 crit. 1: la última factura certificada de la caja. */
    @Transactional(readOnly = true)
    public Optional<FacturaEmitida> ultimaCertificadaDeLaCaja(long cajaId) {
        return emitidas.ultimaCertificadaDeLaCaja(cajaId);
    }

    @Transactional(readOnly = true)
    public Optional<FacturaEmitida> emitida(long facturaId) {
        return emitidas.porId(facturaId);
    }

    /** HU-08.12 crit. 2: las facturas certificadas que coinciden; solo las del turno si {@code turnoId} no es nulo. */
    @Transactional(readOnly = true)
    public List<FacturaEmitida> certificadas(String texto, LocalDate fecha, Long turnoId) {
        return emitidas.buscarCertificadas(texto, fecha, turnoId, RESULTADOS_MAXIMOS);
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
