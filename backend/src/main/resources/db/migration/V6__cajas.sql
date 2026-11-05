-- HU-12.05 Registrar las cajas (Architecture.md §5.2).
-- Llegan después: caja.impresora_id con HU-12.08 (impresoras) y el establecimiento SAT con HU-12.02.

-- V1: una sola sucursal, "Matriz"; deja listo EP-13 (sucursales).
CREATE TABLE sucursal (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo  text UNIQUE NOT NULL,
    nombre  text UNIQUE NOT NULL,
    activo  boolean NOT NULL DEFAULT true
);

INSERT INTO sucursal (codigo, nombre) VALUES ('MATRIZ', 'Matriz');

CREATE TABLE caja (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sucursal_id        bigint NOT NULL REFERENCES sucursal (id),
    nombre             text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 40),
    serie_fel          text CHECK (serie_fel ~ '^[A-Z0-9]{1,20}$'),   -- opcional: una serie por caja
    token_equipo_hash  bytea UNIQUE,                                  -- SHA-256 del token del equipo (crit. 4)
    activo             boolean NOT NULL DEFAULT true,
    creada_en          timestamptz NOT NULL DEFAULT now()
);

-- "Caja 1" y "caja 1" son la misma caja dentro de una sucursal.
CREATE UNIQUE INDEX caja_nombre_por_sucursal ON caja (sucursal_id, lower(nombre));

-- La sesión recuerda desde qué caja se inició (Architecture.md §6.5); NULL si el equipo no está vinculado.
ALTER TABLE sesion ADD COLUMN caja_id bigint REFERENCES caja (id);
