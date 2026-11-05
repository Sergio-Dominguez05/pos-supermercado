package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.ContextoEvento;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroBitacora;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto de entrada de la bitácora para los demás módulos (HU-11.01, Architecture.md §7.4). Completa cada evento con
 * la caja donde ocurre (HU-11.02 crit. 1), para que ningún caso de uso tenga que pasarla a mano.
 */
@Service
public class Bitacora {

    private final RegistroBitacora registro;
    private final ContextoEvento contexto;

    @Autowired
    public Bitacora(RegistroBitacora registro, ContextoEvento contexto) {
        this.registro = registro;
        this.contexto = contexto;
    }

    /** Sin contexto de caja; para pruebas unitarias. */
    public Bitacora(RegistroBitacora registro) {
        this(registro, ContextoEvento.NINGUNO);
    }

    /** Registra dentro de la transacción de la operación: si la operación se revierte, el registro también. */
    @Transactional
    public void registrar(EventoBitacora evento) {
        registro.agregar(conContexto(evento));
    }

    /**
     * Registra en una transacción aparte, para que el intento quede guardado aunque la operación
     * que falló se revierta (HU-11.01 crit. 3).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoFallido(EventoBitacora evento) {
        registro.agregar(conContexto(evento));
    }

    private EventoBitacora conContexto(EventoBitacora evento) {
        return evento.cajaId() != null ? evento : evento.enCaja(contexto.cajaActual());
    }
}
