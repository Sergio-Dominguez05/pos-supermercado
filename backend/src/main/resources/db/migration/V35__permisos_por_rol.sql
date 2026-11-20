-- HU-01.10 Configurar permisos por rol (Architecture.md §5.2). El Administrador activa o desactiva los permisos del
-- Supervisor y del Cajero; la base de datos también impide lo que la historia no permite, por si alguien escribe
-- directamente en las tablas.

-- Crit. 4 y decisión de Sergio: crear productos y cambiar precios, restablecer contraseñas o PIN, administrar
-- usuarios y configurar permisos son solo del Administrador (con los dos últimos, otro rol podría darse más acceso).
ALTER TABLE permiso ADD COLUMN exclusivo_admin boolean NOT NULL DEFAULT false;

UPDATE permiso SET exclusivo_admin = true
WHERE codigo IN ('PRODUCTO_EDITAR', 'CREDENCIAL_RESTABLECER', 'USUARIO_ADMINISTRAR', 'PERMISO_CONFIGURAR');

CREATE FUNCTION rol_permiso_reglas() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    -- Crit. 3: al Administrador no se le quitan permisos.
    IF TG_OP IN ('DELETE', 'UPDATE') AND OLD.rol_id = 1 THEN
        RAISE EXCEPTION 'Los permisos del Administrador no se pueden reducir';
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        -- El usuario técnico "sistema" (V31) no tiene permisos.
        IF NEW.rol_id = 4 THEN
            RAISE EXCEPTION 'El rol Sistema no tiene permisos';
        END IF;
        -- Crit. 4: los permisos exclusivos solo los tiene el Administrador.
        IF NEW.rol_id <> 1 AND (SELECT exclusivo_admin FROM permiso WHERE codigo = NEW.permiso) THEN
            RAISE EXCEPTION 'El permiso % es exclusivo del Administrador', NEW.permiso;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END
$$;

CREATE TRIGGER rol_permiso_reglas
    BEFORE INSERT OR UPDATE OR DELETE ON rol_permiso
    FOR EACH ROW EXECUTE FUNCTION rol_permiso_reglas();
