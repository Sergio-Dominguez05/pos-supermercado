-- HU-12.09 Personalizar el formato del comprobante: el logo en blanco y negro y los mensajes del encabezado y del pie.
-- Una sola fila (id = 1). Sin fila, el ticket sale como siempre: sin logo ni encabezado y "¡Gracias por su compra!".
-- Los datos que exige la SAT y los totales no se guardan aquí: el ticket los arma siempre (crit. 4).

CREATE TABLE formato_comprobante (
    id              smallint PRIMARY KEY CHECK (id = 1),
    encabezado      varchar(120),
    pie             varchar(200),
    logo            bytea,                                            -- 1 bit por punto, 8 puntos por byte
    logo_ancho      int CHECK (logo_ancho BETWEEN 8 AND 384 AND logo_ancho % 8 = 0),
    logo_alto       int CHECK (logo_alto BETWEEN 1 AND 240),
    modificado_por  bigint REFERENCES usuario (id),
    modificado_en   timestamptz NOT NULL DEFAULT now(),
    CHECK ((logo IS NULL) = (logo_ancho IS NULL) AND (logo IS NULL) = (logo_alto IS NULL)),
    CHECK (logo IS NULL OR length(logo) = logo_ancho / 8 * logo_alto)
);
