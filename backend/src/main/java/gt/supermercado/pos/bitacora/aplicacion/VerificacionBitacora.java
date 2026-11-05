package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.ResultadoVerificacion;
import gt.supermercado.pos.bitacora.dominio.VerificadorCadena;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verificación de que en la bitácora no falten registros ni haya registros alterados (HU-11.05 crit. 3). */
@Service
public class VerificacionBitacora {

    private final VerificadorCadena verificador;

    public VerificacionBitacora(VerificadorCadena verificador) {
        this.verificador = verificador;
    }

    @Transactional(readOnly = true)
    public ResultadoVerificacion verificar() {
        return verificador.verificar();
    }
}
