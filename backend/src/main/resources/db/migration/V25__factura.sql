-- HU-08.03 Certificar la factura al finalizar la venta (Architecture.md §5.2, §6.2 y §6.3, EP-08 Facturación).
-- Al finalizar el cobro, en la misma transacción que la venta, se guarda la factura PENDIENTE con el XML del DTE y la
-- copia de los datos del emisor, y la tarea de certificarla. La llamada al certificador va después del COMMIT.
-- Crit. 4: la factura se asocia a su venta, que guarda la caja, el turno y el usuario.

CREATE TABLE factura (
    id                          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id                    bigint UNIQUE NOT NULL REFERENCES venta (id),   -- HU-08.04: una factura por venta
    estado                      text NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN
                                    ('PENDIENTE', 'CERTIFICADA', 'CONTINGENCIA', 'RECHAZADA', 'ANULADA')),
    receptor_tipo               text NOT NULL CHECK (receptor_tipo IN ('NIT', 'CUI', 'CF')),   -- copia (snapshot)
    receptor_id                 text NOT NULL,
    receptor_nombre             text NOT NULL,
    receptor_direccion          text,                                           -- llega con EP-05
    receptor_correo             text,
    emisor_snapshot             jsonb NOT NULL,          -- emisor y establecimiento al momento de emitir
    serie                       text,
    numero                      text,
    uuid_autorizacion           uuid UNIQUE,             -- número de autorización de la SAT (crit. 2)
    fecha_certificacion         timestamptz,
    numero_acceso_contingencia  bigint,                  -- HU-08.07
    emitida_en                  timestamptz NOT NULL DEFAULT now(),
    xml_enviado                 text NOT NULL,
    xml_certificado             text,
    -- Una factura certificada siempre tiene su autorización, serie, número y fecha (crit. 2).
    CHECK (estado NOT IN ('CERTIFICADA', 'ANULADA') OR (uuid_autorizacion IS NOT NULL AND serie IS NOT NULL
           AND numero IS NOT NULL AND fecha_certificacion IS NOT NULL))
);

CREATE INDEX factura_pendientes ON factura (emitida_en) WHERE estado IN ('PENDIENTE', 'CONTINGENCIA');

-- Las respuestas del certificador, también las fallidas (HU-08.06 crit. 4).
CREATE TABLE fel_intento (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    factura_id    bigint NOT NULL REFERENCES factura (id),
    operacion     text NOT NULL CHECK (operacion IN ('CERTIFICAR', 'CONSULTAR', 'ANULAR', 'CORREO')),
    exitoso       boolean NOT NULL,
    codigo_error  text,
    respuesta     jsonb,
    intentado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX fel_intento_factura ON fel_intento (factura_id);

-- "Outbox": la certificación pendiente sobrevive a un corte. Los reintentos son de HU-08.08.
CREATE TABLE tarea_pendiente (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo             text NOT NULL CHECK (tipo IN ('CERTIFICAR', 'ENVIAR_CORREO')),
    factura_id       bigint NOT NULL REFERENCES factura (id),
    intentos         int NOT NULL DEFAULT 0,
    proximo_intento  timestamptz NOT NULL DEFAULT now(),
    ultimo_error     text,
    completada       boolean NOT NULL DEFAULT false
);

CREATE INDEX tarea_pendiente_por_hacer ON tarea_pendiente (proximo_intento) WHERE NOT completada;
