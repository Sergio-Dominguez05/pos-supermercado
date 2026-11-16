-- HU-06.07: eliminar una línea de la venta en curso. Sin DELETE (regla de V1): la línea se marca como eliminada,
-- sale de la venta y de sus totales, y queda en la base como evidencia junto con su registro en la bitácora.

ALTER TABLE venta_linea
    ADD COLUMN eliminada_en  timestamptz,
    ADD COLUMN eliminada_por bigint REFERENCES usuario (id),
    ADD CONSTRAINT venta_linea_eliminada_completa CHECK ((eliminada_en IS NULL) = (eliminada_por IS NULL));

-- Volver a escanear un producto eliminado crea una línea nueva: la unicidad por producto y precio (V15) solo cuenta
-- las líneas vigentes.
ALTER TABLE venta_linea DROP CONSTRAINT venta_linea_producto_y_precio;
CREATE UNIQUE INDEX venta_linea_producto_y_precio ON venta_linea (venta_id, producto_id, precio_unitario)
    WHERE eliminada_en IS NULL;

-- Una línea eliminada ya no cambia: ni su cantidad ni su marca de eliminación.
CREATE FUNCTION venta_linea_eliminada_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.eliminada_en IS NOT NULL THEN
        RAISE EXCEPTION 'La línea % de la venta fue eliminada y no se puede modificar', OLD.id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER venta_linea_eliminada_inmutable
    BEFORE UPDATE ON venta_linea
    FOR EACH ROW EXECUTE FUNCTION venta_linea_eliminada_inmutable();
