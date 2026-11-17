-- HU-08.02: datos que el documento FEL (DTE) pide y que la configuración todavía no tenía. Decisión de Sergio: se
-- registran en la configuración, no como constantes.
--  * Emisor: la afiliación al IVA (General o Pequeño Contribuyente); de ella dependen las frases del DTE.
--  * Establecimiento: la dirección desglosada (código postal, municipio, departamento), que el DTE usa como
--    dirección del emisor.
-- Quedan opcionales en la base para no romper los datos ya guardados; la aplicación los exige al guardar y el
-- generador del DTE avisa si faltan.

ALTER TABLE emisor
    ADD COLUMN afiliacion_iva text CHECK (afiliacion_iva IN ('GEN', 'PEQ'));

ALTER TABLE establecimiento
    ADD COLUMN codigo_postal text CHECK (codigo_postal ~ '^[0-9]{5}$'),
    ADD COLUMN municipio     text CHECK (length(municipio) BETWEEN 1 AND 100),
    ADD COLUMN departamento  text CHECK (length(departamento) BETWEEN 1 AND 100);
