-- HU-06.09 Mantener el precio durante la venta.
-- Si el precio de un producto cambia mientras está en una venta, las unidades ya agregadas conservan su precio
-- (crit. 2) y las que se agreguen después usan el nuevo (crit. 3), en otra línea. Por eso una venta puede tener
-- varias líneas del mismo producto, una por precio; volver a escanear sigue sumando a la línea del precio vigente
-- (HU-06.02 crit. 2).

ALTER TABLE venta_linea DROP CONSTRAINT venta_linea_venta_id_producto_id_key;
ALTER TABLE venta_linea ADD CONSTRAINT venta_linea_producto_y_precio UNIQUE (venta_id, producto_id, precio_unitario);
