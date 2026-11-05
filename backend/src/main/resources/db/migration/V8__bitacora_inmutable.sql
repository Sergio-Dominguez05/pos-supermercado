-- HU-11.05 Proteger la bitácora contra cambios (Architecture.md §5.3).
--
-- Crit. 2: triggers que rechazan UPDATE, DELETE y TRUNCATE para cualquier rol, incluido el dueño del esquema.
-- Crit. 3: cadena de hashes. Cada registro recibe una secuencia consecutiva y
--          hash = SHA-256(hash anterior ‖ datos del registro). La secuencia no es el id: un id se pierde cuando
--          la operación que registraba se revierte, mientras que un hueco en la secuencia significa que
--          borraron un registro. verificar_bitacora() lista los registros alterados, los eslabones rotos y
--          los faltantes.

ALTER TABLE bitacora
    ADD COLUMN secuencia     bigint,
    ADD COLUMN hash_anterior bytea,
    ADD COLUMN hash          bytea;

-- Primer eslabón de la cadena.
CREATE FUNCTION bitacora_inicio_cadena() RETURNS bytea
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT digest('POS Supermercado: inicio de la cadena de la bitácora', 'sha256') $$;

-- Huella de un registro: hash anterior más todos sus datos, en una forma canónica que no depende de la zona
-- horaria de la sesión y que no admite ambigüedades (jsonb escapa los textos y distingue los nulos).
CREATE FUNCTION bitacora_huella(b bitacora) RETURNS bytea
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT digest(b.hash_anterior || convert_to(jsonb_build_array(
               b.secuencia, b.id, extract(epoch FROM b.ocurrido_en), b.usuario_id, b.rol, b.caja_id,
               b.modulo, b.accion, b.exitoso, b.descripcion, b.entidad, b.entidad_id, b.cambios)::text, 'UTF8'),
           'sha256')
$$;

-- Cabeza de la cadena: la última secuencia y su hash. Solo la actualiza el trigger de inserción.
CREATE TABLE bitacora_cadena (
    id                smallint PRIMARY KEY CHECK (id = 1),
    ultima_secuencia  bigint NOT NULL,
    ultimo_hash       bytea NOT NULL
);

-- Los registros que ya existían se encadenan en el orden en que se crearon.
DO $$
DECLARE
    registro  bitacora;
    anterior  bytea := bitacora_inicio_cadena();
    numero    bigint := 0;
BEGIN
    FOR registro IN SELECT * FROM bitacora ORDER BY id LOOP
        numero := numero + 1;
        registro.secuencia := numero;
        registro.hash_anterior := anterior;
        registro.hash := bitacora_huella(registro);
        UPDATE bitacora SET secuencia = registro.secuencia, hash_anterior = registro.hash_anterior,
                            hash = registro.hash
        WHERE id = registro.id;
        anterior := registro.hash;
    END LOOP;
    INSERT INTO bitacora_cadena (id, ultima_secuencia, ultimo_hash) VALUES (1, numero, anterior);
END
$$;

ALTER TABLE bitacora
    ALTER COLUMN secuencia SET NOT NULL,
    ALTER COLUMN hash_anterior SET NOT NULL,
    ALTER COLUMN hash SET NOT NULL,
    ADD CONSTRAINT bitacora_secuencia_unica UNIQUE (secuencia);

-- Cada inserción toma la cabeza con un bloqueo de fila, así los registros concurrentes se encadenan uno
-- detrás de otro. SECURITY DEFINER: la aplicación no tiene permisos sobre bitacora_cadena.
-- El tiempo de espera del bloqueo se limita a 5 s para que un error de programación (registrar dos veces en
-- transacciones anidadas que se esperan entre sí) termine en error en lugar de colgar la aplicación.
CREATE FUNCTION bitacora_encadenar() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    cabeza  bitacora_cadena;
    espera  text := current_setting('lock_timeout');
BEGIN
    PERFORM set_config('lock_timeout', '5s', true);
    SELECT * INTO cabeza FROM bitacora_cadena WHERE id = 1 FOR UPDATE;
    PERFORM set_config('lock_timeout', espera, true);

    NEW.secuencia := cabeza.ultima_secuencia + 1;
    NEW.hash_anterior := cabeza.ultimo_hash;
    NEW.hash := bitacora_huella(NEW);
    UPDATE bitacora_cadena SET ultima_secuencia = NEW.secuencia, ultimo_hash = NEW.hash WHERE id = 1;
    RETURN NEW;
END
$$;

CREATE TRIGGER bitacora_encadenar BEFORE INSERT ON bitacora
    FOR EACH ROW EXECUTE FUNCTION bitacora_encadenar();

-- Crit. 2: nadie modifica ni borra registros, ni siquiera el dueño del esquema.
CREATE FUNCTION bitacora_inmutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora no se puede modificar ni borrar (HU-11.05).'
        USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER bitacora_sin_cambios BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION bitacora_inmutable();

CREATE TRIGGER bitacora_sin_vaciar BEFORE TRUNCATE ON bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION bitacora_inmutable();

-- Crit. 3: problemas encontrados en la cadena; ninguna fila significa que está íntegra.
CREATE FUNCTION verificar_bitacora() RETURNS TABLE (secuencia bigint, problema text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    cabeza  bitacora_cadena;
    ultima  bitacora;
BEGIN
    -- Registros cuyo contenido ya no coincide con su huella.
    RETURN QUERY
        SELECT b.secuencia, 'El registro fue alterado: su contenido no coincide con su huella.'
        FROM bitacora b
        WHERE b.hash <> bitacora_huella(b);

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
        FROM (SELECT b.secuencia, b.hash_anterior,
                     lag(b.secuencia) OVER (ORDER BY b.secuencia) AS previa,
                     lag(b.hash) OVER (ORDER BY b.secuencia) AS hash_previo
              FROM bitacora b) x
        WHERE (x.previa IS NULL AND (x.secuencia <> 1 OR x.hash_anterior <> bitacora_inicio_cadena()))
           OR (x.previa IS NOT NULL AND (x.secuencia <> x.previa + 1 OR x.hash_anterior <> x.hash_previo));

    -- Registros faltantes al final: la cabeza recuerda la última secuencia y su hash.
    SELECT * INTO cabeza FROM bitacora_cadena WHERE id = 1;
    SELECT * INTO ultima FROM bitacora b ORDER BY b.secuencia DESC LIMIT 1;
    IF cabeza.ultima_secuencia > coalesce(ultima.secuencia, 0) THEN
        RETURN QUERY SELECT cabeza.ultima_secuencia,
            format('Faltan los registros %s a %s.', coalesce(ultima.secuencia, 0) + 1, cabeza.ultima_secuencia);
    ELSIF ultima.secuencia IS NOT NULL AND cabeza.ultimo_hash <> ultima.hash THEN
        RETURN QUERY SELECT ultima.secuencia, 'El último registro no coincide con la cabeza de la cadena.'::text;
    END IF;
END
$$;

-- La aplicación solo puede leer y agregar registros; la cabeza de la cadena la maneja el trigger.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pos_app') THEN
        REVOKE ALL ON bitacora_cadena FROM pos_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON bitacora FROM pos_app;
    END IF;
END
$$;
