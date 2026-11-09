-- HU-03.08 crit. 4: cada código escaneado o escrito que no existe en el catálogo queda registrado, con la caja y el
-- usuario, para que un administrador registre el producto. Se marca resuelto al registrar un producto con ese código.
-- Architecture.md §5.2.

CREATE TABLE codigo_no_encontrado (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo       text NOT NULL CHECK (length(codigo) BETWEEN 1 AND 50),
    caja_id      bigint REFERENCES caja (id),
    usuario_id   bigint REFERENCES usuario (id),
    ocurrido_en  timestamptz NOT NULL DEFAULT now(),
    resuelto     boolean NOT NULL DEFAULT false
);

-- La lista del administrador solo muestra los pendientes, agrupados por código.
CREATE INDEX codigo_no_encontrado_pendiente ON codigo_no_encontrado (codigo) WHERE NOT resuelto;
