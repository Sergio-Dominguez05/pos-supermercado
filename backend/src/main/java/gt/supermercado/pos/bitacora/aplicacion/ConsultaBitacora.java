package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.CambioRegistrado;
import gt.supermercado.pos.bitacora.dominio.LecturaBitacora;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lectura de la bitácora para otros módulos. Solo consulta: no existe forma de modificarla (HU-11.05). */
@Service
public class ConsultaBitacora {

    private final LecturaBitacora lectura;

    public ConsultaBitacora(LecturaBitacora lectura) {
        this.lectura = lectura;
    }

    @Transactional(readOnly = true)
    public List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo) {
        return lectura.cambios(accion, entidad, entidadId, campo);
    }
}
