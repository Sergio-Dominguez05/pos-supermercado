-- HU-06.01 Iniciar una venta (Architecture.md §5.2, EP-06 Ventas).
-- La venta en curso se guarda en el servidor en cada operación (§6.1): el precio queda fijo al agregar la línea
-- (HU-06.09) y un corte de luz en la caja no pierde la venta.
-- cliente_id llega con EP-05, cuando exista la tabla cliente; mientras tanto toda venta es a Consumidor Final.

CREATE SEQUENCE venta_numero_seq;

CREATE TABLE venta (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero         bigint UNIQUE NOT NULL DEFAULT nextval('venta_numero_seq'),   -- número interno único (crit. 4)
    uuid           uuid UNIQUE NOT NULL DEFAULT gen_random_uuid(),               -- id idempotente para FEL (EP-08)
    turno_id       bigint NOT NULL REFERENCES turno (id),                        -- HU-09.02 crit. 2
    caja_id        bigint NOT NULL REFERENCES caja (id),
    usuario_id     bigint NOT NULL REFERENCES usuario (id),
    estado         text NOT NULL DEFAULT 'EN_CURSO'
                   CHECK (estado IN ('EN_CURSO', 'SUSPENDIDA', 'CANCELADA', 'FINALIZADA')),
    tasa_iva       numeric(5,4) NOT NULL,                                        -- HU-04.01 crit. 3
    subtotal       numeric(12,2),                                                -- se fijan al finalizar (HU-06.15)
    iva            numeric(12,2),
    total          numeric(12,2),
    creada_en      timestamptz NOT NULL DEFAULT now(),
    finalizada_en  timestamptz,
    CHECK (tasa_iva > 0 AND tasa_iva < 1)
);

-- Un turno tiene como máximo una venta en curso: la pantalla de caja trabaja siempre sobre ella.
CREATE UNIQUE INDEX venta_en_curso_por_turno ON venta (turno_id) WHERE estado = 'EN_CURSO';
CREATE INDEX venta_usuario_estado ON venta (usuario_id, estado);

-- Cada línea guarda una copia de la descripción y del precio vigente al agregarla (HU-06.09) y su desglose de IVA
-- con 6 decimales, sin redondear (HU-04.03).
CREATE TABLE venta_linea (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id          bigint NOT NULL REFERENCES venta (id),
    producto_id       bigint NOT NULL REFERENCES producto (id),
    descripcion       varchar(100) NOT NULL,
    cantidad          int NOT NULL CHECK (cantidad > 0),
    precio_unitario   numeric(12,2) NOT NULL CHECK (precio_unitario > 0),
    subtotal_con_iva  numeric(18,6) NOT NULL,
    base_imponible    numeric(18,6) NOT NULL,
    iva               numeric(18,6) NOT NULL,
    agregada_en       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (venta_id, producto_id),                    -- volver a escanear suma a la misma línea (HU-06.02 crit. 2)
    CHECK (base_imponible + iva = subtotal_con_iva)
);
