-- #136: "Caja Final", "caja final" y "cajafinal" son la misma caja dentro de una sucursal. La clave de unicidad
-- ignora mayúsculas y todos los espacios; el nombre que se muestra se guarda tal como lo normaliza la aplicación
-- (sin espacios al inicio ni al final y con uno solo entre palabras).

CREATE FUNCTION caja_clave_nombre(nombre text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN lower(regexp_replace(nombre, '\s+', '', 'g'));

-- Si ya hay cajas que ahora serían la misma, la migración se detiene y las nombra: se deben renombrar desde la
-- pantalla de Cajas (para que quede en la bitácora) antes de instalar esta versión.
DO $$
DECLARE
    choques text;
BEGIN
    SELECT string_agg(grupo, '; ') INTO choques
    FROM (SELECT string_agg('"' || nombre || '"', ', ' ORDER BY id) AS grupo
          FROM caja
          GROUP BY sucursal_id, caja_clave_nombre(nombre)
          HAVING count(*) > 1) duplicadas;
    IF choques IS NOT NULL THEN
        RAISE EXCEPTION 'Hay cajas con el mismo nombre si se ignoran mayúsculas y espacios: %. Renombre una de cada grupo desde Configuración > Cajas y vuelva a iniciar.', choques;
    END IF;
END
$$;

DROP INDEX caja_nombre_por_sucursal;
CREATE UNIQUE INDEX caja_nombre_por_sucursal ON caja (sucursal_id, caja_clave_nombre(nombre));
