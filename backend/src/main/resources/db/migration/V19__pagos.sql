-- EP-07 Pagos (Architecture.md §5.2). HU-07.03: el pago con tarjeta que el cajero procesó en la terminal bancaria.
-- Se registra antes de finalizar el cobro (HU-07.08). En V1 hay una tarjeta por venta, pero se valida en el servicio
-- (no con UNIQUE) para no bloquear el pago dividido futuro (HU-07.05).

CREATE TABLE pago (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id       bigint NOT NULL REFERENCES venta (id),
    turno_id       bigint NOT NULL REFERENCES turno (id),      -- HU-09.02 crit. 2: el turno abierto del cajero
    usuario_id     bigint NOT NULL REFERENCES usuario (id),
    tipo_tarjeta   text NOT NULL CHECK (tipo_tarjeta IN ('DEBITO', 'CREDITO')),
    autorizacion   varchar(30) NOT NULL CHECK (btrim(autorizacion) <> ''),
    ultimos4       char(4) CHECK (ultimos4 ~ '^\d{4}$'),        -- crit. 4: nunca el número completo
    monto          numeric(12, 2) NOT NULL CHECK (monto > 0),   -- crit. 7: en quetzales
    registrado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX pago_venta ON pago (venta_id);
CREATE INDEX pago_turno ON pago (turno_id);
