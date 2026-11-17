package gt.supermercado.pos.facturacion.infraestructura.fel;

import gt.supermercado.pos.facturacion.aplicacion.CertificadorFel;
import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConexionFel;
import gt.supermercado.pos.facturacion.dominio.Contribuyente;
import gt.supermercado.pos.facturacion.dominio.ResultadoAnulacion;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Certificador en memoria para desarrollar y probar sin el certificador real.
 * El escenario ({@code pos.fel.simulador.escenario}) permite probar aceptación, rechazo y contingencia.
 */
@Component
@ConditionalOnProperty(name = "pos.fel.modo", havingValue = "simulado")
public class CertificadorFelSimulado implements CertificadorFel {

    /**
     * RESPUESTA_PERDIDA (HU-08.04 crit. 2): el certificador certifica el documento, pero la respuesta no llega al POS,
     * que la toma como falta de conexión.
     */
    public enum Escenario { ACEPTAR, RECHAZAR, SIN_CONEXION, RESPUESTA_PERDIDA }

    private final Map<UUID, ResultadoCertificacion> certificadas = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> envios = new ConcurrentHashMap<>();
    private final AtomicLong correlativo = new AtomicLong();
    private volatile Escenario escenario;

    public CertificadorFelSimulado(@Value("${pos.fel.simulador.escenario:ACEPTAR}") Escenario escenario) {
        this.escenario = escenario;
    }

    /** Permite cambiar el escenario en pruebas automáticas. */
    public void usarEscenario(Escenario escenario) {
        this.escenario = escenario;
    }

    /** Cuántas veces se envió a certificar el documento con ese identificador, para las pruebas de HU-08.04. */
    public int enviosDe(UUID identificadorUnico) {
        AtomicInteger cuenta = envios.get(identificadorUnico);
        return cuenta == null ? 0 : cuenta.get();
    }

    /** Como un certificador real: el mismo identificador nunca genera una segunda factura (HU-08.04 crit. 1). */
    @Override
    public ResultadoCertificacion certificar(String xmlDte, UUID identificadorUnico) {
        verificarConexion();
        envios.computeIfAbsent(identificadorUnico, id -> new AtomicInteger()).incrementAndGet();
        ResultadoCertificacion previa = certificadas.get(identificadorUnico);
        if (previa != null) {
            return previa;
        }
        if (escenario == Escenario.RECHAZAR) {
            return ResultadoCertificacion.rechazada("SIM-001", "NIT del receptor no válido (simulado).");
        }
        ResultadoCertificacion certificada = certificadas.computeIfAbsent(identificadorUnico,
                id -> ResultadoCertificacion.aceptada(UUID.randomUUID(), "SIM",
                        String.valueOf(correlativo.incrementAndGet()), OffsetDateTime.now()));
        if (escenario == Escenario.RESPUESTA_PERDIDA) {
            throw new CertificadorNoDisponibleException("Se perdió la respuesta del certificador (simulado).");
        }
        return certificada;
    }

    @Override
    public Optional<ResultadoCertificacion> consultarPorIdentificador(UUID identificadorUnico) {
        verificarConexion();
        return Optional.ofNullable(certificadas.get(identificadorUnico));
    }

    @Override
    public ResultadoAnulacion anular(UUID autorizacion, String motivo) {
        verificarConexion();
        boolean existe = certificadas.values().stream().anyMatch(r -> autorizacion.equals(r.autorizacion()));
        return existe
                ? new ResultadoAnulacion(true, null, null)
                : new ResultadoAnulacion(false, "SIM-404", "La factura no existe en el certificador (simulado).");
    }

    /** ACEPTAR: un nombre simulado; RECHAZAR: la SAT no conoce el NIT; SIN_CONEXION: sin respuesta (HU-05.04). */
    @Override
    public Optional<Contribuyente> consultarNit(String nit) {
        verificarConexion();
        if (escenario == Escenario.RECHAZAR) {
            return Optional.empty();
        }
        return Optional.of(new Contribuyente(nit, "CONTRIBUYENTE SIMULADO " + nit));
    }

    @Override
    public Optional<Contribuyente> consultarCui(String cui) {
        verificarConexion();
        return Optional.of(new Contribuyente(cui, "PERSONA SIMULADA " + cui));
    }

    @Override
    public void solicitarEnvioCorreo(UUID autorizacion, String correo) {
        verificarConexion();
    }

    /** ACEPTAR: conexión exitosa; RECHAZAR: credenciales rechazadas; SIN_CONEXION: sin respuesta. */
    @Override
    public ResultadoPruebaConexion probarConexion(ConexionFel conexion) {
        verificarConexion();
        if (escenario == Escenario.RECHAZAR) {
            return ResultadoPruebaConexion.fallida("El certificador rechazó el usuario o la llave (simulado).");
        }
        return ResultadoPruebaConexion.exitosa("Conexión exitosa con el certificador simulado, ambiente %s."
                .formatted(conexion.ambiente() == AmbienteFel.PRODUCCION ? "de producción" : "de pruebas"));
    }

    private void verificarConexion() {
        if (escenario == Escenario.SIN_CONEXION) {
            throw new CertificadorNoDisponibleException("Sin conexión con el certificador (simulado).");
        }
    }
}
