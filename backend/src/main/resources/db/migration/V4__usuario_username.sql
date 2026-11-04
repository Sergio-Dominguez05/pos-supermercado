-- HU-01.05: los nombres de usuario se guardan en minúsculas, de 3 a 30 caracteres
-- (letras, números, punto, guion o guion bajo). La base de datos lo garantiza además del servicio,
-- así 'Ana' y 'ana' nunca pueden ser dos usuarios distintos.

ALTER TABLE usuario
    ADD CONSTRAINT usuario_username_formato CHECK (username ~ '^[a-z0-9._-]{3,30}$');
