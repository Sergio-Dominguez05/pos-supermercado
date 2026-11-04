-- HU-01.07 Editar usuario.

-- Crit. 5: cada cambio guarda el valor anterior y el nuevo de cada campo (Architecture.md §5.2, HU-11.03),
-- por ejemplo {"rol": {"antes": "CAJERO", "despues": "SUPERVISOR"}, "credencial": "modificado"}.
ALTER TABLE bitacora ADD COLUMN cambios jsonb;

-- Crit. 4: la sesión copia el rol al iniciar, igual que los permisos, para que un cambio de rol
-- (rol y permisos juntos) se aplique a partir del siguiente inicio de sesión.
ALTER TABLE sesion ADD COLUMN rol_id smallint REFERENCES rol (id);
UPDATE sesion s SET rol_id = u.rol_id FROM usuario u WHERE u.id = s.usuario_id;
ALTER TABLE sesion ALTER COLUMN rol_id SET NOT NULL;
