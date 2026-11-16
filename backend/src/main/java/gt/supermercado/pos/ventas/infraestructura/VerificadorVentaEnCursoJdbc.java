package gt.supermercado.pos.ventas.infraestructura;

import gt.supermercado.pos.seguridad.dominio.VerificadorVentaEnCurso;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import org.springframework.stereotype.Component;

/**
 * HU-01.16 crit. 2: no se cierra sesión con una venta en curso. Reemplaza a la implementación provisional
 * {@code SinVentasEnCurso} del módulo {@code seguridad}, que respondía siempre "no hay".
 *
 * <p>Solo cuenta una venta con productos: la pantalla inicia una venta vacía en cuanto el turno está abierto
 * (HU-06.01), y esa no tiene nada que finalizar ni cancelar. Con productos, el cajero siempre tiene salida: cancelarla
 * (HU-06.14).
 */
@Component
class VerificadorVentaEnCursoJdbc implements VerificadorVentaEnCurso {

    private final RepositorioVentas ventas;

    VerificadorVentaEnCursoJdbc(RepositorioVentas ventas) {
        this.ventas = ventas;
    }

    @Override
    public boolean tieneVentaEnCurso(long usuarioId) {
        return ventas.tieneVentaConProductos(usuarioId);
    }
}
