package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.ArchivoBitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-11.10 Conservar la bitácora.
 * <ul>
 *   <li>Crit. 1: el plazo de conservación es configurable (por omisión 5 años; nunca menos de
 *       {@value #PLAZO_MINIMO_ANIOS}, el plazo de prescripción tributaria). Los registros se conservan por lo menos ese
 *       tiempo: la base de datos no permite borrarlos (HU-11.05).</li>
 *   <li>Crit. 2: los registros de hace más de {@value #DIAS_MINIMOS_PARA_ARCHIVAR} días se pueden archivar. Archivar
 *       los mueve a otra tabla con su misma secuencia y su mismo hash: se siguen consultando, exportando y verificando
 *       junto con los activos.</li>
 *   <li>Crit. 3: el sistema no elimina registros nunca, ni automáticamente ni a pedido; no existe esa operación.</li>
 * </ul>
 * Cambiar el plazo y archivar son solo del Administrador (lo exige el controlador) y quedan en la bitácora.
 */
@Service
public class ConservacionBitacora {

    public static final int PLAZO_MINIMO_ANIOS = 4;
    public static final int PLAZO_MAXIMO_ANIOS = 50;
    public static final int DIAS_MINIMOS_PARA_ARCHIVAR = 90;

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private final ArchivoBitacora archivo;
    private final Bitacora bitacora;
    private final Clock reloj;

    @Autowired
    public ConservacionBitacora(ArchivoBitacora archivo, Bitacora bitacora) {
        this(archivo, bitacora, Clock.system(GUATEMALA));
    }

    ConservacionBitacora(ArchivoBitacora archivo, Bitacora bitacora, Clock reloj) {
        this.archivo = archivo;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /**
     * Lo que ve el Administrador: el plazo, cuántos registros hay, desde cuándo, hasta qué día se puede archivar y
     * desde qué día los registros ya cumplieron el plazo (aun así no se eliminan).
     */
    public record EstadoConservacion(int plazoAnios, int plazoMinimoAnios, long registrosActivos,
                                     long registrosArchivados, OffsetDateTime registroMasAntiguo,
                                     OffsetDateTime archivadoHasta, LocalDate archivableAntesDe,
                                     LocalDate plazoCumplidoAntesDe) {}

    @Transactional(readOnly = true)
    public EstadoConservacion estado() {
        ArchivoBitacora.Estado estado = archivo.estado();
        LocalDate hoy = LocalDate.now(reloj.withZone(GUATEMALA));
        return new EstadoConservacion(estado.plazoAnios(), PLAZO_MINIMO_ANIOS, estado.activos(), estado.archivados(),
                estado.masAntiguo(), estado.archivadoHasta(), ultimoDiaArchivable(hoy).plusDays(1),
                hoy.minusYears(estado.plazoAnios()));
    }

    /** Crit. 1: el plazo en años, entre {@value #PLAZO_MINIMO_ANIOS} y {@value #PLAZO_MAXIMO_ANIOS}. */
    @Transactional
    public EstadoConservacion cambiarPlazo(Integer anios, SesionActiva sesion) {
        int anterior = archivo.estado().plazoAnios();
        if (anios == null || anios < PLAZO_MINIMO_ANIOS || anios > PLAZO_MAXIMO_ANIOS) {
            NegocioException error = new NegocioException("PLAZO_CONSERVACION_INVALIDO",
                    "El plazo de conservación debe ser de %d a %d años; menos de %d no cumple la conservación tributaria."
                            .formatted(PLAZO_MINIMO_ANIOS, PLAZO_MAXIMO_ANIOS, PLAZO_MINIMO_ANIOS));
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.PARAMETRO_MODIFICADO, false, "Cambio del plazo de conservación de la bitácora "
                            + "rechazado: " + error.getMessage(), "bitacora_conservacion", 1L, null));
            throw error;
        }
        if (anios != anterior) {
            archivo.cambiarPlazo(anios);
            bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.PARAMETRO_MODIFICADO, true, "Plazo de conservación de la bitácora: %d → %d años"
                            .formatted(anterior, anios), "bitacora_conservacion", 1L,
                    Map.of("plazoConservacionAnios", EventoBitacora.cambio(anterior, anios))));
        }
        return estado();
    }

    /** Lo que devuelve el archivo: cuántos registros se movieron y el estado después. */
    public record ResultadoArchivo(long archivados, EstadoConservacion estado) {}

    /**
     * Crit. 2: archiva los registros ocurridos antes del día {@code antesDe}, que debe ser de hace más de
     * {@value #DIAS_MINIMOS_PARA_ARCHIVAR} días. No borra nada: los mueve con su secuencia y su hash.
     */
    @Transactional
    public ResultadoArchivo archivar(LocalDate antesDe, SesionActiva sesion) {
        LocalDate hoy = LocalDate.now(reloj.withZone(GUATEMALA));
        if (antesDe == null || antesDe.isAfter(ultimoDiaArchivable(hoy).plusDays(1))) {
            NegocioException error = new NegocioException("ARCHIVO_DEMASIADO_RECIENTE",
                    "Solo se archivan registros de hace más de %d días: elija una fecha hasta el %s."
                            .formatted(DIAS_MINIMOS_PARA_ARCHIVAR, ultimoDiaArchivable(hoy).plusDays(1)));
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.BITACORA_ARCHIVADA, false, "Archivo de la bitácora rechazado: " + error.getMessage(),
                    "bitacora", null, null));
            throw error;
        }
        long archivados = archivo.archivar(antesDe);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("antesDe", antesDe.toString());
        detalle.put("registros", archivados);
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.BITACORA_ARCHIVADA,
                true, "Bitácora archivada: %d registros anteriores al %s (se siguen consultando)".formatted(archivados,
                        antesDe), "bitacora", null, detalle));
        return new ResultadoArchivo(archivados, estado());
    }

    private static LocalDate ultimoDiaArchivable(LocalDate hoy) {
        return hoy.minusDays(DIAS_MINIMOS_PARA_ARCHIVAR + 1L);
    }
}
