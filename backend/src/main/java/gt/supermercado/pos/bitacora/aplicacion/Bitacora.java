package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroBitacora;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Punto de entrada de la bitácora para los demás módulos (HU-11.01, Architecture.md §7.4). */
@Service
public class Bitacora {

    private final RegistroBitacora registro;

    public Bitacora(RegistroBitacora registro) {
        this.registro = registro;
    }

    /** Registra dentro de la transacción de la operación: si la operación se revierte, el registro también. */
    @Transactional
    public void registrar(EventoBitacora evento) {
        registro.agregar(evento);
    }

    /**
     * Registra en una transacción aparte, para que el intento quede guardado aunque la operación
     * que falló se revierta (HU-11.01 crit. 3).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoFallido(EventoBitacora evento) {
        registro.agregar(evento);
    }
}
