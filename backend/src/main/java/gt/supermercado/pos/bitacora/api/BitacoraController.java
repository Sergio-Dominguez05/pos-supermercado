package gt.supermercado.pos.bitacora.api;

import gt.supermercado.pos.bitacora.aplicacion.ConsultaBitacora;
import gt.supermercado.pos.bitacora.aplicacion.ConsultaBitacora.OpcionesBitacora;
import gt.supermercado.pos.bitacora.aplicacion.ConsultaBitacora.SolicitudBusqueda;
import gt.supermercado.pos.bitacora.aplicacion.VerificacionBitacora;
import gt.supermercado.pos.bitacora.dominio.DetalleEvento;
import gt.supermercado.pos.bitacora.dominio.PaginaBitacora;
import gt.supermercado.pos.bitacora.dominio.ResultadoVerificacion.Problema;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Bitácora. Solo lectura: no existe ningún endpoint para editar ni borrar registros, para ningún rol
 * (HU-11.05 crit. 1). La consulta, sus filtros y el detalle son de HU-11.06 a 11.08.
 */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final VerificacionBitacora verificacion;

    private final ConsultaBitacora consulta;

    public BitacoraController(VerificacionBitacora verificacion, ConsultaBitacora consulta) {
        this.verificacion = verificacion;
        this.consulta = consulta;
    }

    /**
     * HU-11.06 y 11.07: los registros del más reciente al más antiguo, con filtros combinables; 422 si las fechas
     * están invertidas o la acción no existe. Cada búsqueda queda en la bitácora (crit. 4).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
    public PaginaBitacora buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) Boolean exitoso,
            @RequestParam(required = false) String documento,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal SesionActiva sesion) {
        return consulta.buscar(new SolicitudBusqueda(desde, hasta, usuarioId, cajaId, modulo, accion, exitoso,
                documento, pagina), sesion);
    }

    @GetMapping("/opciones")
    @PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
    public OpcionesBitacora opciones() {
        return consulta.opciones();
    }

    /** HU-11.08: el registro completo y su documento relacionado; 404 si no existe. */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
    public DetalleEvento detalle(@PathVariable long id, @AuthenticationPrincipal SesionActiva sesion) {
        return consulta.detalle(id, sesion);
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
