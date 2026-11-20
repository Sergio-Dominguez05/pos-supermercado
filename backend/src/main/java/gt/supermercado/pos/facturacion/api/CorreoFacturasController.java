package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.EnvioFacturas;
import gt.supermercado.pos.facturacion.aplicacion.EnvioFacturas.ResultadoEnvio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-08.15 crit. 4: reenviar la factura por correo desde su detalle (HU-08.14), con el mismo permiso del detalle. */
@RestController
public class CorreoFacturasController {

    private final EnvioFacturas envio;

    public CorreoFacturasController(EnvioFacturas envio) {
        this.envio = envio;
    }

    /** 422 si no está certificada ({@code FACTURA_NO_CERTIFICADA}) o no tiene correo ({@code FACTURA_SIN_CORREO}). */
    @PostMapping("/api/facturas/{facturaId:\\d+}/correo")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public ResultadoEnvio reenviar(@PathVariable long facturaId) {
        return envio.reenviar(facturaId);
    }
}
