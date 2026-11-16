-- HU-09.01 Abrir el turno de caja (Architecture.md §5.2, EP-09 Caja).
-- Sin fondo inicial: no se manejan pagos en efectivo (crit. 3).
-- El cierre (cerrado_por, totales, diferencia y justificación) llega con HU-09.08 a 09.12.

CREATE TABLE turno (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caja_id     bigint NOT NULL REFERENCES caja (id),
    usuario_id  bigint NOT NULL REFERENCES usuario (id),
    estado      text NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'CERRADO')),
    abierto_en  timestamptz NOT NULL DEFAULT now(),   -- hora del servidor (crit. 2)
    cerrado_en  timestamptz,
    CHECK (estado = 'ABIERTO' OR cerrado_en IS NOT NULL)
);

-- Un turno abierto por caja y por usuario, garantizado por la base de datos (Architecture.md §5.4, HU-09.03).
CREATE UNIQUE INDEX turno_abierto_por_caja    ON turno (caja_id)    WHERE estado = 'ABIERTO';
CREATE UNIQUE INDEX turno_abierto_por_usuario ON turno (usuario_id) WHERE estado = 'ABIERTO';
