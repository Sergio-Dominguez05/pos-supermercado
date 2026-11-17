-- HU-12.01: datos fiscales de la empresa emisora (Architecture.md §5.2). Una sola fila.
-- Crit. 4: las facturas guardan una copia del emisor al emitirse (factura.emisor_snapshot, HU-08.02), así que cambiar
-- estos datos no altera las facturas ya emitidas.

CREATE TABLE emisor (
    id               smallint PRIMARY KEY CHECK (id = 1),
    nit              text NOT NULL CHECK (nit ~ '^[0-9]{1,12}[0-9K]$'),   -- HU-05.03 crit. 3: formato uniforme
    razon_social     text NOT NULL CHECK (length(razon_social) BETWEEN 1 AND 200),
    nombre_comercial text NOT NULL CHECK (length(nombre_comercial) BETWEEN 1 AND 200),
    direccion_fiscal text NOT NULL CHECK (length(direccion_fiscal) BETWEEN 1 AND 300),
    telefono         text CHECK (telefono ~ '^[0-9+ -]{8,20}$'),
    correo           text CHECK (length(correo) <= 254 AND correo ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    modificado_en    timestamptz NOT NULL DEFAULT now(),
    modificado_por   bigint NOT NULL REFERENCES usuario (id)
);
