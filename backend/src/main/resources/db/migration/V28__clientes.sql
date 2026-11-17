-- EP-05 Clientes (Architecture.md §5.2): HU-05.02 Buscar cliente por NIT, HU-05.04 Consultar el nombre del
-- contribuyente y HU-05.05 Registrar un cliente nuevo.
-- "Consumidor Final" no es una fila de cliente: la venta lleva cliente_id NULL y la factura va a "CF" (HU-05.01).

CREATE TABLE cliente (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_id         text NOT NULL DEFAULT 'NIT' CHECK (tipo_id IN ('NIT', 'CUI')),     -- CUI llega con HU-05.06
    identificacion  text NOT NULL,                     -- HU-05.03 crit. 3: sin guiones ni espacios, en mayúsculas
    nombre          text NOT NULL CHECK (length(btrim(nombre)) BETWEEN 1 AND 200),
    direccion       text NOT NULL DEFAULT 'Ciudad' CHECK (length(direccion) BETWEEN 1 AND 300),   -- HU-05.05 crit. 1
    correo          text CHECK (length(correo) <= 254 AND correo ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    ultima_compra   timestamptz,
    registrado_en   timestamptz NOT NULL DEFAULT now(),
    registrado_por  bigint NOT NULL REFERENCES usuario (id),
    UNIQUE (tipo_id, identificacion),                  -- HU-05.05 crit. 2: un NIT no se registra dos veces
    CHECK (tipo_id <> 'NIT' OR identificacion ~ '^[0-9]{1,12}[0-9K]$'),
    CHECK (tipo_id <> 'CUI' OR identificacion ~ '^[0-9]{13}$')
);

-- El cliente de la venta: NULL es Consumidor Final (HU-05.01 crit. 1).
ALTER TABLE venta ADD COLUMN cliente_id bigint REFERENCES cliente (id);
