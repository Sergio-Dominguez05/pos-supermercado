package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Consulta y modifica los parámetros de caja y venta de HU-12.13. */
@Service
public class GestionParametrosOperacion {

    private final Parametros parametros;
    private final RepositorioParametros repositorio;
    private final Bitacora bitacora;

    public GestionParametrosOperacion(Parametros parametros, RepositorioParametros repositorio, Bitacora bitacora) {
        this.parametros = parametros;
        this.repositorio = repositorio;
        this.bitacora = bitacora;
    }

    /** HU-12.07: {@code limiteConsumidorFinal} en quetzales (Q2,500.00 al inicio). */
    public record Configuracion(int cantidadMaximaPorLinea, boolean cierreCiego, BigDecimal limiteConsumidorFinal) {}

    /** {@code limiteConsumidorFinal} vacío conserva el límite vigente. */
    public record Solicitud(Integer cantidadMaximaPorLinea, Boolean cierreCiego, BigDecimal limiteConsumidorFinal) {}

    /** Un monto razonable para el límite: hasta Q999,999.99. */
    static final BigDecimal LIMITE_MAXIMO = new BigDecimal("999999.99");

    @Transactional(readOnly = true)
    public Configuracion consultar() {
        return new Configuracion(parametros.cantidadMaximaPorLinea(), parametros.cierreCiego(),
                parametros.limiteConsumidorFinal());
    }

    @Transactional
    public Configuracion guardar(Solicitud solicitud, SesionActiva sesion) {
        int cantidad;
        boolean cierreCiego;
        BigDecimal limite;
        try {
            if (solicitud == null || solicitud.cantidadMaximaPorLinea() == null
                    || solicitud.cantidadMaximaPorLinea() <= 0
                    || solicitud.cantidadMaximaPorLinea() > Parametros.CANTIDAD_MAXIMA_ABSOLUTA) {
                throw new NegocioException("CANTIDAD_MAX_LINEA_INVALIDA",
                        "La cantidad máxima por línea debe ser un número entero entre 1 y %d."
                                .formatted(Parametros.CANTIDAD_MAXIMA_ABSOLUTA));
            }
            if (solicitud.cierreCiego() == null) {
                throw new NegocioException("CIERRE_CIEGO_REQUERIDO",
                        "Indique si el cierre ciego está activado.");
            }
            cantidad = solicitud.cantidadMaximaPorLinea();
            cierreCiego = solicitud.cierreCiego();
            limite = solicitud.limiteConsumidorFinal() == null ? parametros.limiteConsumidorFinal()
                    : solicitud.limiteConsumidorFinal();
            if (limite.signum() <= 0 || limite.compareTo(LIMITE_MAXIMO) > 0
                    || limite.stripTrailingZeros().scale() > 2) {
                throw new NegocioException("LIMITE_CONSUMIDOR_FINAL_INVALIDO",
                        "El límite para Consumidor Final debe ser un monto mayor que Q0.00, con hasta 2 decimales.");
            }
            limite = limite.setScale(2);
        } catch (NegocioException error) {
            throw rechazar(sesion, error);
        }

        Configuracion anterior = consultar();
        Map<String, Object> cambios = new LinkedHashMap<>();
        if (anterior.cantidadMaximaPorLinea() != cantidad) {
            cambios.put("cantidadMaximaPorLinea",
                    EventoBitacora.cambio(anterior.cantidadMaximaPorLinea(), cantidad));
        }
        if (anterior.cierreCiego() != cierreCiego) {
            cambios.put("cierreCiego", EventoBitacora.cambio(anterior.cierreCiego(), cierreCiego));
        }
        // HU-12.07 crit. 3: el límite, con el valor anterior y el nuevo.
        if (anterior.limiteConsumidorFinal().compareTo(limite) != 0) {
            cambios.put("limiteConsumidorFinal", EventoBitacora.cambio(anterior.limiteConsumidorFinal(), limite));
        }
        // PUT reemplaza la configuración completa. Escribir ambos valores también serializa actualizaciones concurrentes.
        repositorio.guardarEntero(Parametros.CANTIDAD_MAX_LINEA, cantidad);
        repositorio.guardarBooleano(Parametros.CIERRE_CIEGO, cierreCiego);
        repositorio.guardarDecimal(Parametros.LIMITE_CONSUMIDOR_FINAL, limite);
        if (cambios.isEmpty()) {
            return new Configuracion(cantidad, cierreCiego, limite);
        }

        bitacora.registrar(evento(sesion, true,
                "Parámetros de caja y venta modificados: " + String.join(", ", cambios.keySet()), cambios));
        return new Configuracion(cantidad, cierreCiego, limite);
    }

    private NegocioException rechazar(SesionActiva sesion, NegocioException error) {
        bitacora.registrarIntentoFallido(evento(sesion, false,
                "Cambio de parámetros de caja y venta rechazado: " + error.getMessage(), null));
        return error;
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion,
                                          Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "parametros_operacion", null, cambios);
    }
}
