-- Sprint 1 · EP-11 Bitácora: versión mínima para registrar los inicios de sesión (HU-01.01 crit. 5).
-- Llegan después: caja_id con HU-12.05, cambios antes/después con HU-11.03,
-- y el encadenamiento de hashes y los triggers de inmutabilidad con HU-11.05 (Architecture.md §5.3).

CREATE TABLE bitacora (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- consecutivo
    ocurrido_en  timestamptz NOT NULL DEFAULT now(),                -- hora del servidor (HU-11.02)
    usuario_id   bigint REFERENCES usuario (id),                    -- NULL si el usuario no existe o es el sistema
    rol          text,
    modulo       text NOT NULL,
    accion       text NOT NULL,
    exitoso      boolean NOT NULL,
    descripcion  text NOT NULL,                                     -- nunca contraseñas, PIN ni tarjetas
    entidad      text,
    entidad_id   bigint
);

CREATE INDEX bitacora_fecha ON bitacora (ocurrido_en DESC);
CREATE INDEX bitacora_entidad ON bitacora (entidad, entidad_id);

-- La aplicación solo puede leer y agregar registros (V1__base.sql le dio UPDATE por defecto).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pos_app') THEN
        REVOKE UPDATE ON bitacora FROM pos_app;
    END IF;
END
$$;
