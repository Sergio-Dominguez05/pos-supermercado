-- HU-08.07 Emitir comprobantes en contingencia, HU-08.08 Certificarlos y HU-08.09 Avisar los pendientes.

-- HU-08.07 crit. 1: el número de acceso que el POS asigna a cada comprobante emitido en contingencia. Va en el DTE
-- (DatosGenerales/@NumeroAcceso, 9 dígitos) para que la SAT lo vincule con la certificación posterior.
CREATE SEQUENCE numero_acceso_contingencia_seq START WITH 100000001 MINVALUE 100000001 MAXVALUE 999999999;

ALTER TABLE factura ADD CONSTRAINT factura_numero_acceso_unico UNIQUE (numero_acceso_contingencia);

-- HU-08.08: el proceso automático que certifica las pendientes registra en la bitácora a nombre del usuario técnico
-- "sistema" (decisión de Sergio). Tiene su propio rol, sin permisos, y no puede iniciar sesión: está inactivo y su
-- credencial no es un hash válido.
ALTER TABLE rol DROP CONSTRAINT rol_codigo_check;
ALTER TABLE rol ADD CONSTRAINT rol_codigo_check CHECK (codigo IN ('ADMIN', 'SUPERVISOR', 'CAJERO', 'SISTEMA'));
INSERT INTO rol (id, codigo, nombre) VALUES (4, 'SISTEMA', 'Sistema');

INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal, activo)
VALUES ('sistema', 'Proceso automático del sistema', 4, '!', false, false);

-- La tarea de certificar una factura recién emitida espera un minuto: mientras tanto la certifica la propia venta
-- (HU-08.03), y el proceso automático no la envía en paralelo.
ALTER TABLE tarea_pendiente ALTER COLUMN proximo_intento SET DEFAULT now() + interval '1 minute';
