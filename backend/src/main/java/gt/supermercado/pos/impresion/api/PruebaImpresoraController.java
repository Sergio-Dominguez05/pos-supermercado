package gt.supermercado.pos.impresion.api;

import gt.supermercado.pos.impresion.aplicacion.PruebaImpresora;
import gt.supermercado.pos.impresion.aplicacion.PruebaImpresora.ResultadoPrueba;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-12.08 crit. 2: imprimir una página de prueba. Solo con el permiso de modificar la configuración. */
@RestController
public class PruebaImpresoraController {

    private final PruebaImpresora prueba;

    public PruebaImpresoraController(PruebaImpresora prueba) {
        this.prueba = prueba;
    }

    @PostMapping("/api/configuracion/impresoras/{id}/prueba")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public ResultadoPrueba imprimir(@PathVariable long id) {
        return prueba.imprimir(id);
    }
}
