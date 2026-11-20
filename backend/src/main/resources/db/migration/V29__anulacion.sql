-- HU-08.10 Anular una factura (Architecture.md §5.2 y decisión D2).
-- Crit. 2 y 3: la anulación guarda el motivo, quién la hizo y la respuesta del certificador. HU-09.02 crit. 2 y D2: queda
-- asociada al turno de la venta, que debe seguir abierto, para que el cierre de esa caja la descuente.

CREATE TABLE anulacion (
    factura_id     bigint PRIMARY KEY REFERENCES factura (id),        -- una factura se anula una sola vez
    motivo         text NOT NULL CHECK (length(btrim(motivo)) BETWEEN 5 AND 255),
    usuario_id     bigint NOT NULL REFERENCES usuario (id),            -- crit. 5: desde la sesión de quien anula
    turno_id       bigint NOT NULL REFERENCES turno (id),
    anulada_en     timestamptz NOT NULL DEFAULT now(),
    respuesta_fel  jsonb NOT NULL
);

CREATE INDEX anulacion_turno ON anulacion (turno_id);
