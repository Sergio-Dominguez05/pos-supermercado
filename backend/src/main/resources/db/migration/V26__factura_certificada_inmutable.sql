-- HU-08.04 Evitar facturas duplicadas (Architecture.md §5.3).
-- Crit. 3: una venta con factura certificada no se vuelve a facturar. UNIQUE (venta_id) de V25 ya impide una segunda
-- factura; además, una factura certificada no cambia sus datos: solo puede pasar a ANULADA (HU-08.10).

CREATE FUNCTION factura_certificada_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.estado IN ('CERTIFICADA', 'ANULADA') AND (
            NEW.estado NOT IN (OLD.estado, 'ANULADA')
            OR NEW.venta_id IS DISTINCT FROM OLD.venta_id
            OR NEW.uuid_autorizacion IS DISTINCT FROM OLD.uuid_autorizacion
            OR NEW.serie IS DISTINCT FROM OLD.serie
            OR NEW.numero IS DISTINCT FROM OLD.numero
            OR NEW.fecha_certificacion IS DISTINCT FROM OLD.fecha_certificacion
            OR NEW.receptor_tipo IS DISTINCT FROM OLD.receptor_tipo
            OR NEW.receptor_id IS DISTINCT FROM OLD.receptor_id
            OR NEW.receptor_nombre IS DISTINCT FROM OLD.receptor_nombre
            OR NEW.emisor_snapshot IS DISTINCT FROM OLD.emisor_snapshot
            OR NEW.emitida_en IS DISTINCT FROM OLD.emitida_en
            OR NEW.xml_enviado IS DISTINCT FROM OLD.xml_enviado) THEN
        RAISE EXCEPTION 'La factura % ya está certificada y no se puede volver a facturar ni modificar', OLD.id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER factura_certificada_inmutable
    BEFORE UPDATE ON factura
    FOR EACH ROW EXECUTE FUNCTION factura_certificada_inmutable();
