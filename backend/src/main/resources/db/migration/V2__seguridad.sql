-- Sprint 1 · EP-01 Seguridad: roles, matriz inicial de permisos, usuarios y sesiones (Architecture.md §5.2).
-- Columnas que dependen de otras historias llegan con ellas: sesion.caja_id con HU-12.05 (cajas)
-- y permiso.exclusivo_admin con HU-01.10 (configurar permisos por rol).

CREATE TABLE rol (
    id      smallint PRIMARY KEY,
    codigo  text UNIQUE NOT NULL CHECK (codigo IN ('ADMIN', 'SUPERVISOR', 'CAJERO')),
    nombre  text NOT NULL
);

INSERT INTO rol (id, codigo, nombre) VALUES
    (1, 'ADMIN', 'Administrador'),
    (2, 'SUPERVISOR', 'Supervisor'),
    (3, 'CAJERO', 'Cajero');

CREATE TABLE permiso (
    codigo       text PRIMARY KEY,
    modulo       text NOT NULL,
    descripcion  text NOT NULL
);

CREATE TABLE rol_permiso (
    rol_id   smallint REFERENCES rol (id),
    permiso  text REFERENCES permiso (codigo),
    PRIMARY KEY (rol_id, permiso)
);

-- Matriz de permisos (configuración inicial), al final de docs/original/userStoriesE1.md.
INSERT INTO permiso (codigo, modulo, descripcion) VALUES
    ('VENTA_REGISTRAR',          'ventas',        'Registrar ventas'),
    ('VENTA_SUSPENDER_CANCELAR', 'ventas',        'Suspender y cancelar ventas en curso'),
    ('PAGO_REGISTRAR',           'pagos',         'Registrar pagos con tarjeta'),
    ('CLIENTE_GESTIONAR',        'clientes',      'Registrar y editar clientes'),
    ('FACTURA_REIMPRIMIR',       'facturacion',   'Reimprimir facturas'),
    ('FACTURA_ANULAR',           'facturacion',   'Anular facturas'),
    ('CAJA_OPERAR_PROPIA',       'caja',          'Abrir y cerrar su propia caja'),
    ('CAJA_CERRAR_OTRA',         'caja',          'Cerrar la caja de otro usuario'),
    ('CAJA_VER_CIERRES',         'caja',          'Ver cierres de caja de otros usuarios'),
    ('REPORTE_CONSULTAR',        'reportes',      'Consultar reportes'),
    ('BITACORA_CONSULTAR',       'bitacora',      'Consultar bitácora'),
    ('PRODUCTO_CONSULTAR',       'catalogo',      'Consultar el catálogo de productos'),
    ('PRODUCTO_EDITAR',          'catalogo',      'Crear productos y cambiar precios'),
    ('CATALOGO_IMPORTAR',        'catalogo',      'Cargar el catálogo desde un archivo'),
    ('USUARIO_ADMINISTRAR',      'seguridad',     'Administrar usuarios'),
    ('CREDENCIAL_RESTABLECER',   'seguridad',     'Restablecer contraseñas y PIN'),
    ('PERMISO_CONFIGURAR',       'seguridad',     'Configurar permisos por rol'),
    ('CONFIGURACION_MODIFICAR',  'configuracion', 'Modificar la configuración del sistema');

-- El Administrador tiene todos los permisos.
INSERT INTO rol_permiso (rol_id, permiso) SELECT 1, codigo FROM permiso;

INSERT INTO rol_permiso (rol_id, permiso) VALUES
    (2, 'VENTA_REGISTRAR'), (2, 'VENTA_SUSPENDER_CANCELAR'), (2, 'PAGO_REGISTRAR'), (2, 'CLIENTE_GESTIONAR'),
    (2, 'FACTURA_REIMPRIMIR'), (2, 'FACTURA_ANULAR'), (2, 'CAJA_OPERAR_PROPIA'), (2, 'CAJA_CERRAR_OTRA'),
    (2, 'CAJA_VER_CIERRES'), (2, 'REPORTE_CONSULTAR'), (2, 'BITACORA_CONSULTAR'), (2, 'PRODUCTO_CONSULTAR'),
    (3, 'VENTA_REGISTRAR'), (3, 'VENTA_SUSPENDER_CANCELAR'), (3, 'PAGO_REGISTRAR'), (3, 'CLIENTE_GESTIONAR'),
    (3, 'FACTURA_REIMPRIMIR'), (3, 'CAJA_OPERAR_PROPIA'), (3, 'PRODUCTO_CONSULTAR');

CREATE TABLE usuario (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username             text UNIQUE NOT NULL,              -- inmutable (HU-01.07)
    nombre_completo      text NOT NULL,
    rol_id               smallint NOT NULL REFERENCES rol (id),
    credencial_hash      text NOT NULL,                     -- Argon2id de la contraseña o el PIN, nunca el valor
    credencial_temporal  boolean NOT NULL DEFAULT true,     -- HU-01.15
    activo               boolean NOT NULL DEFAULT true,
    ultimo_acceso        timestamptz,
    creado_en            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sesion (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash   bytea UNIQUE NOT NULL,                     -- SHA-256 del token; el token solo viaja en la cookie
    usuario_id   bigint NOT NULL REFERENCES usuario (id),
    permisos     text[] NOT NULL,                           -- copia al iniciar sesión (HU-01.07 crit. 4)
    iniciada_en  timestamptz NOT NULL DEFAULT now(),
    cerrada_en   timestamptz
);
