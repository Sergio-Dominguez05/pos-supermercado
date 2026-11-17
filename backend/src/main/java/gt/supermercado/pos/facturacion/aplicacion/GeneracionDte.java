package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.configuracion.aplicacion.GestionEmisor;
import gt.supermercado.pos.configuracion.aplicacion.GestionEstablecimiento;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.GeneradorDte;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * HU-08.02: arma el DTE de una venta con los datos configurados del emisor (HU-12.01) y del establecimiento de su
 * sucursal (HU-12.02). Lo usa la certificación al finalizar la venta (HU-08.03), que guarda el XML y la copia de los
 * datos en la factura.
 */
@Service
public class GeneracionDte {

    private final GestionEmisor emisor;
    private final GestionEstablecimiento establecimientos;

    public GeneracionDte(GestionEmisor emisor, GestionEstablecimiento establecimientos) {
        this.emisor = emisor;
        this.establecimientos = establecimientos;
    }

    /** Los datos del DTE de la venta. En V1 todas las cajas son de la sucursal Matriz (EP-13 agrega las demás). */
    @Transactional(readOnly = true)
    public DatosDte datos(Venta venta, Receptor receptor, OffsetDateTime emitidaEn) {
        return new DatosDte(emisor.emisor().orElse(null),
                establecimientos.deLaSucursal(establecimientos.sucursalPrincipal()).orElse(null), receptor, emitidaEn,
                venta);
    }

    /** El XML del DTE de la venta. 422 si falta configurar el emisor o el establecimiento. */
    @Transactional(readOnly = true)
    public String generar(Venta venta, Receptor receptor, OffsetDateTime emitidaEn) {
        return GeneradorDte.generar(datos(venta, receptor, emitidaEn));
    }
}
