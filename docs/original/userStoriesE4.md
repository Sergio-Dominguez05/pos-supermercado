# Historias de usuario — EP-04: Cálculo de IVA

## Contexto

Esta épica calcula el IVA de cada venta y lo muestra desglosado en los comprobantes. El único impuesto que maneja el sistema es el **IVA del 12 %**, que ya viene **incluido en el precio** de venta registrado en el catálogo de productos (EP-03). **Ningún producto está exento de IVA.**

**Fórmulas de referencia:**
- Base imponible = Precio con IVA ÷ 1.12
- IVA = Precio con IVA − Base imponible

**Reglas de redondeo (según el certificador FEL):**
- Los cálculos por línea se manejan con hasta **6 decimales**, sin redondear.
- Las sumas de las líneas no se redondean.
- Solo se redondean los **resultados finales** (Subtotal, IVA y TOTAL), a **2 decimales**.
- Redondeo al medio hacia arriba: si el decimal que se elimina es mayor o igual a 5, el decimal anterior aumenta en 1.

**Formato de totales en el ticket:** Subtotal, IVA, TOTAL.

**Roles involucrados:** Cajero, Supervisor, Administrador y el propio sistema.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-04.01 | Aplicar IVA del 12 % a todos los productos | Alta |
| HU-04.02 | Calcular base imponible e IVA desde el precio con IVA | Alta |
| HU-04.03 | Calcular el IVA por línea | Alta |
| HU-04.04 | Calcular y mostrar los totales del comprobante | Alta |
| HU-04.05 | Aplicar reglas de redondeo | Alta |
| HU-04.06 | Recalcular el IVA con descuentos | Futura |
| HU-04.07 | Calcular el IVA en productos por peso | Futura |
| HU-04.08 | Calcular el IVA en devoluciones y notas de crédito | Futura |
| HU-04.09 | Asegurar consistencia con el documento FEL | Alta |

---

## HU-04.01 — Aplicar IVA del 12 % a todos los productos

**Como** administrador,
**quiero** que el sistema aplique el IVA del 12 % a todos los productos,
**para** cumplir con la legislación tributaria de Guatemala.

**Criterios de aceptación:**
1. Todos los productos vendidos se consideran gravados con IVA del 12 %; no existen productos exentos.
2. La tasa del 12 % está definida en el sistema y no se puede modificar desde la interfaz.
3. La tasa queda guardada en cada venta, para que las ventas pasadas se mantengan correctas aunque la tasa cambie en el futuro.

**Prioridad:** Alta

---

## HU-04.02 — Calcular base imponible e IVA desde el precio con IVA

**Como** sistema POS,
**quiero** separar la base imponible y el IVA a partir del precio con IVA,
**para** desglosar correctamente el impuesto sin cambiar el precio que paga el cliente.

**Criterios de aceptación:**
1. **Dado** un producto con precio de Q11.20, **entonces** la base imponible es Q10.00 y el IVA es Q1.20.
2. **Dado** un producto con precio de Q5.00, **entonces** la base imponible es Q4.464286 y el IVA es Q0.535714 (6 decimales).
3. El cálculo nunca modifica el precio con IVA registrado en el catálogo.
4. Los cálculos internos usan aritmética decimal exacta (no números de punto flotante), para evitar errores de precisión.

**Prioridad:** Alta

---

## HU-04.03 — Calcular el IVA por línea

**Como** supervisor,
**quiero** que cada línea de la venta tenga su base imponible y su IVA,
**para** revisar el cálculo de cualquier producto y generar correctamente la factura electrónica.

**Criterios de aceptación:**
1. Cada línea guarda: cantidad, precio unitario con IVA, subtotal con IVA, base imponible e IVA.
2. La base imponible y el IVA de cada línea se guardan con hasta 6 decimales, sin redondear a 2.
3. El desglose por línea se puede consultar en el detalle de la venta.

**Prioridad:** Alta

---

## HU-04.04 — Calcular y mostrar los totales del comprobante

**Como** cajero,
**quiero** ver el total de la venta con el IVA desglosado,
**para** informar al cliente el monto correcto a pagar.

**Criterios de aceptación:**
1. La sección de totales del ticket y de la pantalla de venta se muestra en este orden: **Subtotal**, **IVA** y **TOTAL**.
2. El Subtotal es la suma de las bases imponibles de las líneas, redondeada a 2 decimales al final.
3. El IVA es la suma del IVA de las líneas, redondeada a 2 decimales al final.
4. El TOTAL es la suma de los subtotales con IVA de las líneas, con 2 decimales.
5. Subtotal más IVA es igual al TOTAL.
6. Los totales se actualizan en tiempo real cada vez que se agrega, modifica o elimina una línea.

**Prioridad:** Alta

---

## HU-04.05 — Aplicar reglas de redondeo

**Como** administrador,
**quiero** que los montos se redondeen según las reglas del certificador FEL,
**para** evitar diferencias de centavos y rechazos de facturas.

**Criterios de aceptación:**
1. Los valores por línea y las sumas intermedias se manejan con hasta 6 decimales y no se redondean.
2. Solo los resultados finales (Subtotal, IVA y TOTAL) se redondean a 2 decimales.
3. El redondeo es al medio hacia arriba: **dado** que el decimal que se elimina es mayor o igual a 5, **entonces** el decimal anterior aumenta en 1 (por ejemplo, 10.715 → 10.72 y 10.714 → 10.71).
4. Las reglas de redondeo están documentadas y cubiertas por pruebas automáticas.

**Prioridad:** Alta

---

## HU-04.06 — Recalcular el IVA con descuentos

> **Fuera de la primera versión.** No se manejan descuentos.

**Como** supervisor,
**quiero** que el IVA se calcule sobre el precio con descuento,
**para** no cobrar ni declarar impuesto sobre un monto que el cliente no pagó.

**Criterios de aceptación:**
1. **Dado** un descuento por línea, **entonces** la base imponible y el IVA de esa línea se calculan sobre el precio con descuento.
2. **Dado** un descuento al total de la venta, **entonces** el descuento se distribuye proporcionalmente entre las líneas y se recalcula el IVA de cada una.

**Prioridad:** Futura

---

## HU-04.07 — Calcular el IVA en productos por peso

> **Fuera de la primera versión.** Todos los productos se venden por unidad.

**Como** sistema POS,
**quiero** calcular correctamente el IVA de los productos que se venden por peso,
**para** que las ventas a granel también se facturen con montos exactos.

**Criterios de aceptación:**
1. **Dado** un producto por peso, **entonces** el subtotal con IVA es igual al precio por unidad de medida multiplicado por el peso.
2. El peso se maneja con la precisión que entrega la balanza.

**Prioridad:** Futura

---

## HU-04.08 — Calcular el IVA en devoluciones y notas de crédito

> **Fuera de la primera versión.** No se manejan devoluciones.

**Como** supervisor,
**quiero** que las devoluciones reviertan el IVA con los mismos montos de la venta original,
**para** que los reportes de IVA y la información enviada a la SAT sean correctos.

**Criterios de aceptación:**
1. **Dado** una devolución total, **entonces** la nota de crédito tiene la misma base imponible y el mismo IVA que la factura original.
2. **Dado** una devolución parcial, **entonces** se usan los montos de las líneas devueltas tal como se calcularon en la venta original.

**Prioridad:** Futura

---

## HU-04.09 — Asegurar consistencia con el documento FEL

**Como** administrador,
**quiero** que los montos de IVA del POS coincidan exactamente con los del documento enviado al certificador FEL,
**para** evitar rechazos de facturas y diferencias en la declaración de impuestos.

**Criterios de aceptación:**
1. Los montos de cada línea (hasta 6 decimales) y los totales (2 decimales) enviados al certificador son los mismos que se guardan en el POS.
2. **Dado** que el certificador rechaza una factura por una diferencia de montos, **entonces** el error se registra con el detalle para su corrección.
3. Existen pruebas automáticas con casos reales (muchas líneas, cantidades altas, precios con centavos) que validan la consistencia.

**Prioridad:** Alta

