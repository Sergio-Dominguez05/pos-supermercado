-- HU-08.01, HU-12.03 y HU-12.15: datos de conexión con el certificador FEL (Architecture.md §5.2 y §7.5).
-- Una sola fila. La llave o token se guarda cifrado con AES-GCM; la llave maestra vive fuera de la base de datos.
-- HU-08.01 crit. 2: solo se acepta una dirección https (canal cifrado).

CREATE TABLE config_fel (
    id               smallint PRIMARY KEY CHECK (id = 1),
    certificador     text NOT NULL CHECK (length(certificador) BETWEEN 1 AND 60),
    url_servicio     text NOT NULL CHECK (url_servicio ~ '^https://[^/\s]+'),
    usuario          text NOT NULL CHECK (length(usuario) BETWEEN 1 AND 100),
    secreto_cifrado  bytea NOT NULL,
    ambiente         text NOT NULL CHECK (ambiente IN ('PRUEBAS', 'PRODUCCION')),
    timeout_ms       int NOT NULL DEFAULT 8000 CHECK (timeout_ms BETWEEN 1000 AND 60000),
    modificado_en    timestamptz NOT NULL DEFAULT now(),
    modificado_por   bigint NOT NULL REFERENCES usuario (id)
);
