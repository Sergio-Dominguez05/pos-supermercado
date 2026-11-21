-- HU-11.10 Conservar la bitácora (Architecture.md §5.3).
--
-- Crit. 1 y 3: el plazo de conservación queda configurado (por omisión 5 años, nunca menos de 4) y la base de datos
--              sigue sin permitir borrar registros: ni la bitácora ni su archivo admiten UPDATE, DELETE ni TRUNCATE.
--              No existe ningún proceso que elimine registros.
-- Crit. 2:     los registros antiguos se pueden archivar: se MUEVEN a bitacora_archivo con su misma secuencia y su
--              mismo hash, así que la cadena de HU-11.05 sigue completa. La consulta y la verificación leen las dos
--              tablas juntas (vista bitacora_completa).
--
-- Si una migración futura agrega columnas a bitacora, debe agregarlas también a bitacora_archivo en la misma posición
-- (antes de archivado_en) y a la vista bitacora_completa.

-- Plazo de conservación, propio del módulo de bitácora.
CREATE TABLE bitacora_conservacion (
    id           smallint PRIMARY KEY CHECK (id = 1),
    plazo_anios  smallint NOT NULL DEFAULT 5 CHECK (plazo_anios BETWEEN 4 AND 50),
    modificado_en timestamptz NOT NULL DEFAULT now()
);
INSERT INTO bitacora_conservacion (id) VALUES (1);

-- El archivo: las mismas columnas de bitacora, en el mismo orden, más cuándo se archivó cada registro.
CREATE TABLE bitacora_archivo (
    id             bigint PRIMARY KEY,
    ocurrido_en    timestamptz NOT NULL,
    usuario_id     bigint REFERENCES usuario (id),
    rol            text,
    modulo         text NOT NULL,
    accion         text NOT NULL,
    exitoso        boolean NOT NULL,
    descripcion    text NOT NULL,
    entidad        text,
    entidad_id     bigint,
    cambios        jsonb,
    caja_id        bigint REFERENCES caja (id),
    secuencia      bigint NOT NULL UNIQUE,
    hash_anterior  bytea NOT NULL,
    hash           bytea NOT NULL,
    archivado_en   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX bitacora_archivo_fecha ON bitacora_archivo (ocurrido_en DESC);
CREATE INDEX bitacora_archivo_entidad ON bitacora_archivo (entidad, entidad_id);

-- La bitácora completa: lo activo y lo archivado, con las columnas de bitacora.
CREATE VIEW bitacora_completa AS
    SELECT b.*, false AS archivado FROM bitacora b
    UNION ALL
    SELECT a.id, a.ocurrido_en, a.usuario_id, a.rol, a.modulo, a.accion, a.exitoso, a.descripcion, a.entidad,
           a.entidad_id, a.cambios, a.caja_id, a.secuencia, a.hash_anterior, a.hash, true AS archivado
    FROM bitacora_archivo a;

-- El archivo tampoco se modifica ni se vacía, para ningún rol.
CREATE TRIGGER bitacora_archivo_sin_cambios BEFORE UPDATE OR DELETE ON bitacora_archivo
    FOR EACH ROW EXECUTE FUNCTION bitacora_inmutable();

CREATE TRIGGER bitacora_archivo_sin_vaciar BEFORE TRUNCATE ON bitacora_archivo
    FOR EACH STATEMENT EXECUTE FUNCTION bitacora_inmutable();

-- Un registro solo sale de bitacora cuando lo mueve archivar_bitacora() y ya está en el archivo idéntico (mismo id,
-- secuencia y hash). Cualquier otro DELETE, y todo UPDATE, se sigue rechazando.
CREATE FUNCTION bitacora_sin_cambios_salvo_archivo() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('pos.archivando_bitacora', true) = 'si'
       AND EXISTS (SELECT 1 FROM bitacora_archivo a
                   WHERE a.id = OLD.id AND a.secuencia = OLD.secuencia AND a.hash = OLD.hash) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'La bitácora no se puede modificar ni borrar (HU-11.05).'
        USING ERRCODE = 'insufficient_privilege';
END
$$;

DROP TRIGGER bitacora_sin_cambios ON bitacora;
CREATE TRIGGER bitacora_sin_cambios BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION bitacora_sin_cambios_salvo_archivo();

-- Crit. 2: mueve al archivo los registros ocurridos antes de "hasta". Solo los de hace más de 90 días, y nunca el
-- último registro de la cadena (su hash es la cabeza). Devuelve cuántos movió. SECURITY DEFINER: la aplicación no
-- tiene DELETE sobre bitacora.
CREATE FUNCTION archivar_bitacora(hasta timestamptz) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    movidos bigint;
BEGIN
    IF hasta > now() - interval '90 days' THEN
        RAISE EXCEPTION 'Solo se archivan registros de hace más de 90 días.' USING ERRCODE = 'check_violation';
    END IF;
    PERFORM set_config('pos.archivando_bitacora', 'si', true);
    INSERT INTO bitacora_archivo (id, ocurrido_en, usuario_id, rol, modulo, accion, exitoso, descripcion, entidad,
                                  entidad_id, cambios, caja_id, secuencia, hash_anterior, hash)
        SELECT b.id, b.ocurrido_en, b.usuario_id, b.rol, b.modulo, b.accion, b.exitoso, b.descripcion, b.entidad,
               b.entidad_id, b.cambios, b.caja_id, b.secuencia, b.hash_anterior, b.hash
        FROM bitacora b
        WHERE b.ocurrido_en < hasta
          AND b.secuencia < (SELECT ultima_secuencia FROM bitacora_cadena WHERE id = 1);
    GET DIAGNOSTICS movidos = ROW_COUNT;
    DELETE FROM bitacora b
    WHERE EXISTS (SELECT 1 FROM bitacora_archivo a WHERE a.id = b.id AND a.secuencia = b.secuencia AND a.hash = b.hash);
    PERFORM set_config('pos.archivando_bitacora', 'no', true);
    RETURN movidos;
END
$$;

-- HU-11.05 crit. 3 sobre la bitácora completa: la cadena se verifica uniendo lo activo y lo archivado.
CREATE OR REPLACE FUNCTION verificar_bitacora() RETURNS TABLE (secuencia bigint, problema text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    cabeza  bitacora_cadena;
    ultima  record;
BEGIN
    -- Registros cuyo contenido ya no coincide con su huella (también los archivados).
    RETURN QUERY
        SELECT b.secuencia, 'El registro fue alterado: su contenido no coincide con su huella.'
        FROM bitacora b
        WHERE b.hash <> bitacora_huella(b);
    RETURN QUERY
        SELECT a.secuencia, 'El registro archivado fue alterado: su contenido no coincide con su huella.'
        FROM bitacora_archivo a
        WHERE a.hash <> bitacora_huella(ROW(a.id, a.ocurrido_en, a.usuario_id, a.rol, a.modulo, a.accion, a.exitoso,
                                            a.descripcion, a.entidad, a.entidad_id, a.cambios, a.caja_id, a.secuencia,
                                            a.hash_anterior, a.hash)::bitacora);

    -- Eslabones rotos y registros faltantes entre dos registros.
    RETURN QUERY
        SELECT x.secuencia,
               CASE
                   WHEN x.previa IS NULL AND x.secuencia <> 1
                       THEN format('Faltan los registros 1 a %s.', x.secuencia - 1)
                   WHEN x.previa IS NOT NULL AND x.secuencia <> x.previa + 1
                       THEN format('Faltan los registros %s a %s.', x.previa + 1, x.secuencia - 1)
                   ELSE 'La cadena está rota: el hash anterior no coincide con el registro previo.'
               END
        FROM (SELECT c.secuencia, c.hash_anterior,
                     lag(c.secuencia) OVER (ORDER BY c.secuencia) AS previa,
                     lag(c.hash) OVER (ORDER BY c.secuencia) AS hash_previo
              FROM bitacora_completa c) x
        WHERE (x.previa IS NULL AND (x.secuencia <> 1 OR x.hash_anterior <> bitacora_inicio_cadena()))
           OR (x.previa IS NOT NULL AND (x.secuencia <> x.previa + 1 OR x.hash_anterior <> x.hash_previo));

    -- Registros faltantes al final: la cabeza recuerda la última secuencia y su hash.
    SELECT * INTO cabeza FROM bitacora_cadena WHERE id = 1;
    SELECT c.secuencia, c.hash INTO ultima FROM bitacora_completa c ORDER BY c.secuencia DESC LIMIT 1;
    IF cabeza.ultima_secuencia > coalesce(ultima.secuencia, 0) THEN
        RETURN QUERY SELECT cabeza.ultima_secuencia,
            format('Faltan los registros %s a %s.', coalesce(ultima.secuencia, 0) + 1, cabeza.ultima_secuencia);
    ELSIF ultima.secuencia IS NOT NULL AND cabeza.ultimo_hash <> ultima.hash THEN
        RETURN QUERY SELECT ultima.secuencia, 'El último registro no coincide con la cabeza de la cadena.'::text;
    END IF;
END
$$;

-- La aplicación lee el archivo y la vista; no escribe el archivo directamente (solo archivar_bitacora()).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pos_app') THEN
        REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON bitacora_archivo FROM pos_app;
        GRANT SELECT ON bitacora_archivo, bitacora_completa TO pos_app;
        GRANT SELECT, UPDATE ON bitacora_conservacion TO pos_app;
    END IF;
END
$$;
