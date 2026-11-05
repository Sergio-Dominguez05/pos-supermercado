-- HU-11.02 crit. 1: cada registro guarda la caja donde ocurrió el evento (NULL fuera de una caja,
-- por ejemplo desde la PC de administración o al arrancar el sistema).
ALTER TABLE bitacora ADD COLUMN caja_id bigint REFERENCES caja (id);
