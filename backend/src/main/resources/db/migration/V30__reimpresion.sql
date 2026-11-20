-- HU-08.12 Reimprimir una factura (Architecture.md §5.2): cada reimpresión, con quién la pidió y en qué caja.

CREATE TABLE reimpresion (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    factura_id  bigint NOT NULL REFERENCES factura (id),
    usuario_id  bigint NOT NULL REFERENCES usuario (id),
    caja_id     bigint REFERENCES caja (id),
    impresa_en  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX reimpresion_factura ON reimpresion (factura_id);
