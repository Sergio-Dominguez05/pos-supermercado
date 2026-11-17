-- HU-12.08 Configurar las impresoras y HU-08.05 Imprimir la factura (Architecture.md §3 y §5.2).
-- Las impresoras térmicas están en la red: el servidor les envía el ticket ESC/POS por TCP (puerto 9100 por omisión).

CREATE TABLE impresora (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 60),
    host            text NOT NULL CHECK (host ~ '^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$'),   -- IP o nombre
    puerto          int NOT NULL DEFAULT 9100 CHECK (puerto BETWEEN 1 AND 65535),
    ancho_mm        smallint NOT NULL CHECK (ancho_mm IN (58, 80)),                                   -- crit. 1
    copias_factura  smallint NOT NULL DEFAULT 1 CHECK (copias_factura BETWEEN 1 AND 5),               -- crit. 3
    copias_cierre   smallint NOT NULL DEFAULT 1 CHECK (copias_cierre BETWEEN 1 AND 5),
    modificado_en   timestamptz NOT NULL DEFAULT now(),
    modificado_por  bigint NOT NULL REFERENCES usuario (id)
);

CREATE UNIQUE INDEX impresora_nombre ON impresora (lower(nombre));

-- HU-12.05 crit. 2 (pendiente heredado): la impresora de cada caja.
ALTER TABLE caja ADD COLUMN impresora_id bigint REFERENCES impresora (id);

-- HU-08.05 crit. 1: la representación impresa lleva el nombre y el NIT del certificador.
ALTER TABLE config_fel ADD COLUMN nit_certificador text CHECK (nit_certificador ~ '^[0-9]{1,12}[0-9K]$');
