-- HU-12.13: el Administrador decide si el cierre de caja oculta el total registrado al cajero.
INSERT INTO parametro (clave, valor) VALUES ('cierre_ciego', 'false')
ON CONFLICT (clave) DO NOTHING;
