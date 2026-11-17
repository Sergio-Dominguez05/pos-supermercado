-- HU-06.12: suspender la venta en curso. Suspender es solo cambiar el estado (Architecture.md §5): la venta conserva
-- todas sus líneas, no genera factura ni cuenta para la caja. Se guarda la hora para listarlas al retomarlas
-- (HU-06.13 crit. 1).

ALTER TABLE venta
    ADD COLUMN suspendida_en timestamptz,
    ADD CONSTRAINT venta_suspendida_con_hora CHECK (estado <> 'SUSPENDIDA' OR suspendida_en IS NOT NULL);

-- Crit. 2: una caja puede tener varias ventas suspendidas; se buscan por caja (HU-06.13 crit. 3) y por turno, para
-- no cerrar la caja con ventas en espera (crit. 4).
CREATE INDEX venta_suspendida_por_caja ON venta (caja_id) WHERE estado = 'SUSPENDIDA';
CREATE INDEX venta_suspendida_por_turno ON venta (turno_id) WHERE estado = 'SUSPENDIDA';
