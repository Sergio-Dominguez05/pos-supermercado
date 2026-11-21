package gt.supermercado.pos.bitacora.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * HU-11.10: el plazo de conservación y el archivo de la bitácora. Ningún método elimina registros: archivar los mueve
 * a otra tabla, también inmutable, con su misma secuencia y su mismo hash (la base de datos lo garantiza).
 */
public interface ArchivoBitacora {

    /** Lo que se sabe de la conservación: el plazo y cuántos registros hay activos y archivados. */
    record Estado(int plazoAnios, long activos, long archivados, OffsetDateTime masAntiguo,
                  OffsetDateTime archivadoHasta) {}

    Estado estado();

    void cambiarPlazo(int anios);

    /** Mueve al archivo los registros ocurridos antes del día {@code hasta} (hora de Guatemala); cuántos movió. */
    long archivar(LocalDate hasta);
}
