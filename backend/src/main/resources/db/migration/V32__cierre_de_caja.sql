-- HU-09.08 Cerrar la caja y HU-09.09 Justificar diferencias (Architecture.md §5.2, tabla turno).
-- Crit. 5: el cierre guarda quién cerró, el total registrado en el POS, el total del cierre de lote de la terminal y
-- la diferencia. HU-09.09 crit. 1: una diferencia distinta de cero exige una justificación.

ALTER TABLE turno
    ADD COLUMN cerrado_por      bigint REFERENCES usuario (id),   -- el cajero; un supervisor con HU-09.12
    ADD COLUMN total_registrado numeric(12, 2),
    ADD COLUMN total_lote       numeric(12, 2) CHECK (total_lote >= 0),
    ADD COLUMN diferencia       numeric(12, 2),
    ADD COLUMN justificacion    text CHECK (length(btrim(justificacion)) BETWEEN 5 AND 500),
    ADD CONSTRAINT turno_cerrado_con_totales
        CHECK (estado = 'ABIERTO' OR (total_lote IS NOT NULL AND total_registrado IS NOT NULL
                                      AND diferencia IS NOT NULL AND cerrado_por IS NOT NULL)),
    ADD CONSTRAINT turno_diferencia_justificada
        CHECK (diferencia IS NULL OR diferencia = 0 OR justificacion IS NOT NULL);

-- HU-09.08 crit. 4: un turno cerrado no se modifica.
CREATE FUNCTION turno_cerrado_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.estado = 'CERRADO' THEN
        RAISE EXCEPTION 'El turno % ya está cerrado y no se puede modificar', OLD.id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER turno_cerrado_inmutable
    BEFORE UPDATE ON turno
    FOR EACH ROW EXECUTE FUNCTION turno_cerrado_inmutable();
