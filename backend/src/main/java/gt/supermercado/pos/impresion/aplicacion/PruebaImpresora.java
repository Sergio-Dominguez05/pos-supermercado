package gt.supermercado.pos.impresion.aplicacion;

import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.impresion.dominio.CanalImpresion;
import gt.supermercado.pos.impresion.dominio.EscPos;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import gt.supermercado.pos.impresion.dominio.PaginaDePrueba;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/** HU-12.08 crit. 2: imprime la página de prueba en la impresora, una sola copia. */
@Service
public class PruebaImpresora {

    private final GestionImpresoras impresoras;
    private final CanalImpresion canal;

    public PruebaImpresora(GestionImpresoras impresoras, CanalImpresion canal) {
        this.impresoras = impresoras;
        this.canal = canal;
    }

    /** El resultado de la prueba, para mostrarlo al Administrador. */
    public record ResultadoPrueba(boolean exitosa, String mensaje) {}

    /** 404 si la impresora no existe. Si no responde, la prueba falla con el motivo, sin lanzar. */
    public ResultadoPrueba imprimir(long impresoraId) {
        Impresora impresora = impresoras.obtener(impresoraId);
        try {
            canal.enviar(impresora.host(), impresora.puerto(),
                    EscPos.codificar(PaginaDePrueba.armar(impresora, OffsetDateTime.now()), 1));
            return new ResultadoPrueba(true, "Página de prueba enviada a '%s'. Revise que salga completa."
                    .formatted(impresora.nombre()));
        } catch (ImpresoraNoDisponibleException e) {
            return new ResultadoPrueba(false, e.getMessage()
                    + " Revise que esté encendida, con papel y conectada a la red.");
        }
    }
}
