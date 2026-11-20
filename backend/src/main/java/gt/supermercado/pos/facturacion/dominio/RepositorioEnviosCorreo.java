package gt.supermercado.pos.facturacion.dominio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HU-08.15: los envíos por correo pendientes ({@code tarea_pendiente} de tipo {@code ENVIAR_CORREO}) y sus intentos
 * ({@code fel_intento} con operación {@code CORREO}).
 */
public interface RepositorioEnviosCorreo {

    /**
     * Las facturas cuyo envío ya se puede intentar: con la tarea vencida y la factura certificada (o anulada, para
     * descartarla). Las que esperan certificación quedan para después.
     */
    List<Long> porEnviar(int limite);

    /**
     * La tarea de la factura, bloqueada hasta el final de la transacción para que dos procesos no la envíen a la vez.
     * Vacío si no hay, si otro proceso la tiene o, sin {@code aunqueEspere}, si todavía no le toca.
     */
    Optional<EnvioPendiente> bloquear(long facturaId, boolean aunqueEspere);

    /** Crea la tarea de enviar la factura, si no tiene una pendiente. */
    void programar(long facturaId);

    /** Termina la tarea: enviada, descartada o agotados los intentos ({@code error} dice por qué). */
    void completar(long facturaId, String error);

    /** Deja la tarea para después. Con {@code contarIntento}, espera 1, 2, 4, 8... minutos; sin él, 5 minutos. */
    void reprogramar(long facturaId, String error, boolean contarIntento);

    /** El intento de envío con su resultado, para el historial de la factura (HU-08.13). */
    void registrarIntento(long facturaId, boolean exitoso, String codigoError, Map<String, Object> respuesta);
}
