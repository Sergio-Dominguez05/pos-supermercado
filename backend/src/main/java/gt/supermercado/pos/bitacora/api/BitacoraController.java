package gt.supermercado.pos.bitacora.api;

import gt.supermercado.pos.bitacora.aplicacion.VerificacionBitacora;
import gt.supermercado.pos.bitacora.dominio.ResultadoVerificacion.Problema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bitácora. Solo lectura: no existe ningún endpoint para editar ni borrar registros, para ningún rol
 * (HU-11.05 crit. 1); la consulta de registros llega con HU-11.06.
 */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final VerificacionBitacora verificacion;

    public BitacoraController(VerificacionBitacora verificacion) {
        this.verificacion = verificacion;
    }

    public record Verificacion(boolean integra, long registros, List<Problema> problemas) {}

    /** HU-11.05 crit. 3: recalcula la cadena de hashes y dice si falta o se alteró algún registro. */
    @GetMapping("/verificacion")
    @PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
    public Verificacion verificar() {
        var resultado = verificacion.verificar();
        return new Verificacion(resultado.integra(), resultado.registros(), resultado.problemas());
    }
}
