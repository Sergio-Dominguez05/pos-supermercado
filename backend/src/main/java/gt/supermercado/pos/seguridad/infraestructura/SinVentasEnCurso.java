package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.VerificadorVentaEnCurso;
import org.springframework.stereotype.Component;

/**
 * Mientras no existan las ventas nadie puede tener una venta en curso.
 * HU-06.01 debe reemplazar esta clase por una implementación del módulo {@code ventas} que consulte
 * {@code venta WHERE usuario_id = ? AND estado = 'EN_CURSO'}, y borrar esta.
 */
@Component
class SinVentasEnCurso implements VerificadorVentaEnCurso {

    @Override
    public boolean tieneVentaEnCurso(long usuarioId) {
        return false;
    }
}
