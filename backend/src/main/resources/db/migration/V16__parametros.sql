-- Parámetros de operación del supermercado (Architecture.md §5, tabla parametro). Los edita el Administrador con
-- HU-12.13 (#119); mientras tanto quedan con su valor inicial.

CREATE TABLE parametro (
    clave          text PRIMARY KEY,
    valor          jsonb NOT NULL,
    modificado_en  timestamptz NOT NULL DEFAULT now()
);

-- HU-06.04 crit. 4: a partir de esta cantidad por línea se pide al cajero que la confirme.
INSERT INTO parametro (clave, valor) VALUES ('cantidad_max_linea', '50');
