-- HU-12.02: el establecimiento tal como está inscrito en la SAT (Architecture.md §5.2).
-- Crit. 3: uno por sucursal; en V1 solo existe la sucursal "Matriz" y EP-13 agregará las demás.
-- Las facturas guardarán una copia del establecimiento al emitirse (factura.emisor_snapshot, HU-08.02).

CREATE TABLE establecimiento (
    sucursal_id    bigint PRIMARY KEY REFERENCES sucursal (id),
    codigo_sat     text UNIQUE NOT NULL CHECK (codigo_sat ~ '^[1-9][0-9]{0,5}$'),
    nombre         text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 200),
    direccion      text NOT NULL CHECK (length(direccion) BETWEEN 1 AND 300),
    modificado_en  timestamptz NOT NULL DEFAULT now(),
    modificado_por bigint NOT NULL REFERENCES usuario (id)
);
