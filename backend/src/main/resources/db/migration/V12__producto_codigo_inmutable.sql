-- HU-03.02 crit. 2: el código de barras no se puede modificar después de creado, para mantener la trazabilidad de
-- las ventas. Si está mal, se desactiva el producto y se registra uno nuevo. La base de datos lo garantiza para
-- cualquier rol, no solo la API.

CREATE FUNCTION producto_codigo_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.codigo_barras IS DISTINCT FROM OLD.codigo_barras THEN
        RAISE EXCEPTION 'El código de barras de un producto no se puede modificar (producto %).', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER producto_codigo_inmutable
    BEFORE UPDATE OF codigo_barras ON producto
    FOR EACH ROW EXECUTE FUNCTION producto_codigo_inmutable();
