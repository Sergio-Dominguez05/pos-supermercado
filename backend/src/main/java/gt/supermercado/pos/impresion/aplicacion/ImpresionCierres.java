package gt.supermercado.pos.impresion.aplicacion;

import gt.supermercado.pos.caja.dominio.ComprobanteDeCierre;
import gt.supermercado.pos.caja.dominio.ImpresionDeCierre;
import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.impresion.dominio.CanalImpresion;
import gt.supermercado.pos.impresion.dominio.EscPos;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import gt.supermercado.pos.impresion.dominio.TicketCierre;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * HU-09.10: el comprobante de cierre en la impresora de la caja, con las copias de cierre configuradas (HU-12.08
 * crit. 3); la reimpresión sale una sola vez, como copia.
 */
@Service
public class ImpresionCierres implements ImpresionDeCierre {

    private final GestionImpresoras impresoras;
    private final CanalImpresion canal;

    public ImpresionCierres(GestionImpresoras impresoras, CanalImpresion canal) {
        this.impresoras = impresoras;
        this.canal = canal;
    }

    @Override
    public Resultado imprimir(ComprobanteDeCierre comprobante, Long cajaId, boolean reimpresion) {
        if (cajaId == null) {
            return new Resultado(false, "Este equipo no está vinculado a una caja: no hay impresora donde imprimir.");
        }
        Optional<Impresora> impresora = impresoras.deLaCaja(cajaId);
        if (impresora.isEmpty()) {
            return new Resultado(false, "La caja no tiene impresora configurada: el cierre se puede reimprimir.");
        }
        try {
            canal.enviar(impresora.get().host(), impresora.get().puerto(), EscPos.codificar(
                    TicketCierre.armar(comprobante, impresora.get().anchoMm(), reimpresion),
                    reimpresion ? 1 : impresora.get().copiasCierre()));
            return new Resultado(true, reimpresion ? "Se reimprimió el comprobante de cierre." : null);
        } catch (ImpresoraNoDisponibleException e) {
            return new Resultado(false, "No se pudo imprimir: " + e.getMessage() + " El cierre se puede reimprimir.");
        }
    }
}
