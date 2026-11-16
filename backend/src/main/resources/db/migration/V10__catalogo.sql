-- HU-03.01 Registrar un producto (Architecture.md §5.2, EP-03 Catálogo).
-- Llegan después: codigo_no_encontrado con HU-03.08 y el historial de precios con HU-03.11.

CREATE TABLE producto (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_barras  text UNIQUE NOT NULL CHECK (codigo_barras ~ '^[A-Z0-9-]{1,50}$'),   -- inmutable (HU-03.02)
    descripcion    varchar(100) NOT NULL CHECK (length(descripcion) >= 1),
    precio         numeric(12,2) NOT NULL CHECK (precio > 0),                           -- incluye IVA (EP-04)
    activo         boolean NOT NULL DEFAULT true,                                       -- crit. 6
    modificado_en  timestamptz NOT NULL DEFAULT now()
);

-- Búsqueda por descripción sin tildes ni mayúsculas (HU-03.07).
CREATE INDEX producto_desc_trgm ON producto USING gin (f_unaccent(lower(descripcion)) gin_trgm_ops);
