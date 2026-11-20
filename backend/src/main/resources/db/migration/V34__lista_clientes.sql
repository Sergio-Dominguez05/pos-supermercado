-- HU-05.10: búsqueda por nombre y cálculo de la última compra en la lista de clientes.
CREATE INDEX cliente_nombre_trgm ON cliente USING gin (f_unaccent(lower(nombre)) gin_trgm_ops);
CREATE INDEX venta_cliente_finalizada ON venta (cliente_id, finalizada_en DESC)
    WHERE estado = 'FINALIZADA' AND cliente_id IS NOT NULL;
