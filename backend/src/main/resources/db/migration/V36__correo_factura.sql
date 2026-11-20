-- HU-05.11 y HU-08.15: el correo al que se envía la factura y el servidor SMTP con que la envía el POS (decisión de
-- Sergio: lo envía el POS, no el certificador; Architecture.md §6.4).

-- HU-05.11: el correo para la factura de la venta. Al asignar el cliente se copia el suyo; el cajero lo agrega, lo
-- cambia o decide no enviarlo (NULL). Vale también para una venta a Consumidor Final.
ALTER TABLE venta ADD COLUMN correo_factura text
    CHECK (length(correo_factura) <= 254 AND correo_factura ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$');

-- HU-08.15: el servidor de correo. Una sola fila, como config_fel; la contraseña se guarda cifrada con AES-GCM.
CREATE TABLE config_correo (
    id                smallint PRIMARY KEY CHECK (id = 1),
    servidor          text NOT NULL CHECK (length(servidor) BETWEEN 1 AND 255),
    puerto            int NOT NULL CHECK (puerto BETWEEN 1 AND 65535),
    seguridad         text NOT NULL CHECK (seguridad IN ('STARTTLS', 'SSL', 'NINGUNA')),
    usuario           text CHECK (length(usuario) BETWEEN 1 AND 254),
    secreto_cifrado   bytea,
    remitente         text NOT NULL CHECK (length(remitente) <= 254 AND remitente ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    nombre_remitente  text CHECK (length(nombre_remitente) BETWEEN 1 AND 100),
    timeout_ms        int NOT NULL DEFAULT 10000 CHECK (timeout_ms BETWEEN 1000 AND 60000),
    modificado_en     timestamptz NOT NULL DEFAULT now(),
    modificado_por    bigint NOT NULL REFERENCES usuario (id),
    -- Con usuario, la contraseña es obligatoria; sin usuario, el servidor no pide autenticación.
    CHECK ((usuario IS NULL) = (secreto_cifrado IS NULL))
);

-- HU-08.15 crit. 3: un solo envío pendiente por factura; los reintentos reprograman la misma tarea.
CREATE UNIQUE INDEX tarea_pendiente_un_correo ON tarea_pendiente (factura_id)
    WHERE tipo = 'ENVIAR_CORREO' AND NOT completada;
