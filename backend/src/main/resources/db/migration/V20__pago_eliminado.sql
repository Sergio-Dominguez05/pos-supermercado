-- HU-07.06: eliminar un pago antes de finalizar la venta. Sin DELETE (regla de V1): el pago se marca como eliminado,
-- deja de contar para la venta y queda en la base como evidencia, además de su registro en la bitácora (crit. 4).

ALTER TABLE pago
    ADD COLUMN eliminado_en  timestamptz,
    ADD COLUMN eliminado_por bigint REFERENCES usuario (id),
    ADD CONSTRAINT pago_eliminado_completo CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));

-- Un pago eliminado ya no cambia; y crit. 3: los pagos de una venta que ya no está en curso (finalizada, cancelada o
-- suspendida) no se modifican. Para corregir una venta finalizada hay que anular la factura (EP-08).
CREATE FUNCTION pago_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.eliminado_en IS NOT NULL THEN
        RAISE EXCEPTION 'El pago % fue eliminado y no se puede modificar', OLD.id;
    END IF;
    IF (SELECT estado FROM venta WHERE id = OLD.venta_id) <> 'EN_CURSO' THEN
        RAISE EXCEPTION 'El pago % es de una venta que ya no está en curso y no se puede modificar', OLD.id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER pago_inmutable
    BEFORE UPDATE ON pago
    FOR EACH ROW EXECUTE FUNCTION pago_inmutable();
