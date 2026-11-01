# Historias de usuario — EP-07: Pagos

## Contexto

En la primera versión el supermercado **solo acepta pagos con tarjeta** (débito o crédito) en quetzales. El cobro se procesa en la terminal bancaria (POS de tarjeta), que **no está integrada** con el sistema: el cajero cobra en la terminal y registra en el POS el número de autorización que esta entrega.

**Fuera de la primera versión:** pago dividido entre varias tarjetas, pagos en efectivo, transferencias, cajón de dinero, reembolsos e integración con terminales bancarias. Esas historias se conservan como referencia con prioridad Futura. No se aceptan pagos en dólares.

**Roles involucrados:** Cajero, Supervisor, Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-07.01 | Cobrar en efectivo y calcular el vuelto | Futura |
| HU-07.02 | Usar montos rápidos en efectivo | Futura |
| HU-07.03 | Cobrar con tarjeta | Alta |
| HU-07.04 | Cobrar con transferencia | Futura |
| HU-07.05 | Dividir el pago entre varias tarjetas | Futura |
| HU-07.06 | Eliminar un pago antes de finalizar | Alta |
| HU-07.07 | Validar que el pago cubra el total | Alta |
| HU-07.08 | Finalizar el cobro | Alta |
| HU-07.09 | Abrir el cajón de dinero | Futura |
| HU-07.10 | Reembolsar una devolución | Futura |

---

## HU-07.01 — Cobrar en efectivo y calcular el vuelto

> **Fuera de la primera versión.** Por ahora solo se aceptan pagos con tarjeta.

**Como** cajero,
**quiero** ingresar el monto en efectivo que me entrega el cliente y que el sistema calcule el vuelto,
**para** no cometer errores al dar el cambio.

**Criterios de aceptación:**
1. **Dado** que selecciono "Efectivo" e ingreso el monto recibido, **entonces** el sistema calcula y muestra el vuelto en letra grande.
2. **Dado** que el monto recibido es menor al total pendiente, **entonces** el sistema muestra cuánto falta y no permite finalizar solo con ese pago.
3. El vuelto se muestra también en el ticket impreso.

**Prioridad:** Futura

---

## HU-07.02 — Usar montos rápidos en efectivo

> **Fuera de la primera versión.** Depende de HU-07.01.

**Como** cajero,
**quiero** botones con montos frecuentes (pago exacto, Q50, Q100, Q200),
**para** registrar el efectivo recibido con una sola tecla.

**Criterios de aceptación:**
1. El sistema muestra un botón de "Pago exacto" y botones con las denominaciones de billetes más usadas.
2. Cada botón tiene un atajo de teclado.

**Prioridad:** Futura

---

## HU-07.03 — Cobrar con tarjeta

**Como** cajero,
**quiero** registrar el pago con tarjeta de débito o crédito que procesé en la terminal bancaria,
**para** dejar constancia del cobro y emitir la factura.

**Criterios de aceptación:**
1. **Dado** que paso al cobro, **entonces** el sistema muestra el total a cobrar para que lo ingrese en la terminal bancaria.
2. El monto del pago se propone automáticamente igual al total pendiente.
3. Debo indicar el tipo de tarjeta (débito o crédito) e ingresar el número de autorización que entrega la terminal.
4. Puedo registrar los últimos 4 dígitos de la tarjeta; el sistema nunca guarda el número completo de la tarjeta.
5. El monto con tarjeta no puede ser mayor al total pendiente.
6. **Dado** que la terminal rechaza la tarjeta, **entonces** no registro el pago y puedo intentar con otra tarjeta por el total o cancelar la venta (HU-06.14).
7. Todos los montos se manejan en quetzales (Q).

**Prioridad:** Alta

---

## HU-07.04 — Cobrar con transferencia

> **Fuera de la primera versión.** Por ahora solo se aceptan pagos con tarjeta.

**Como** cajero,
**quiero** registrar un pago por transferencia bancaria,
**para** aceptar pagos de clientes que pagan desde su banca móvil.

**Criterios de aceptación:**
1. Debo ingresar el número de referencia de la transferencia y el banco.
2. El monto por transferencia no puede ser mayor al total pendiente.

**Prioridad:** Futura

---

## HU-07.05 — Dividir el pago entre varias tarjetas

> **Fuera de la primera versión.** Por ahora cada venta se paga con una sola tarjeta.

**Como** cajero,
**quiero** dividir el pago de una venta entre varias tarjetas,
**para** atender a clientes que pagan una parte con cada tarjeta.

**Criterios de aceptación:**
1. Puedo registrar varios pagos con tarjeta en la misma venta, cada uno con su monto, tipo de tarjeta y número de autorización.
2. El sistema muestra en todo momento el total, lo pagado y lo pendiente.
3. Todos los pagos quedan registrados en la venta y en la factura, si el formato lo requiere.

**Prioridad:** Futura

---

## HU-07.06 — Eliminar un pago antes de finalizar

**Como** cajero,
**quiero** quitar un pago que registré por error,
**para** corregirlo antes de terminar la venta.

**Criterios de aceptación:**
1. Puedo eliminar cualquier pago mientras la venta no se haya finalizado.
2. El total pendiente se recalcula de inmediato.
3. Una vez finalizada la venta, los pagos no se pueden modificar; para corregir hay que anular la factura (EP-08).
4. La eliminación de un pago queda registrada en la bitácora.

**Prioridad:** Alta

---

## HU-07.07 — Validar que el pago cubra el total

**Como** supervisor,
**quiero** que el sistema no permita finalizar una venta con pagos incompletos,
**para** evitar facturas que no se cobraron completas.

**Criterios de aceptación:**
1. **Dado** que la suma de los pagos es menor al total, **entonces** no se puede finalizar la venta.
2. **Dado** que la suma de los pagos es igual al total, **entonces** se habilita la opción de finalizar.

**Prioridad:** Alta

---

## HU-07.08 — Finalizar el cobro

**Como** cajero,
**quiero** confirmar el cobro,
**para** que se emita la factura y quede lista la siguiente venta.

**Criterios de aceptación:**
1. **Dado** que el pago está completo, **cuando** confirmo, **entonces** el sistema registra la venta, sus pagos y emite la factura (EP-08).
2. Cada pago queda asociado a la venta, la caja, el turno y el usuario.
3. Los montos cobrados se suman al resumen del turno de caja abierto (EP-09).
4. **Dado** que ocurre un error al guardar, **entonces** la venta no queda registrada a medias y el cajero puede reintentar.
5. Al terminar, el sistema queda listo para una nueva venta.

**Prioridad:** Alta

---

## HU-07.09 — Abrir el cajón de dinero

> **Fuera de la primera versión.** No se manejan pagos en efectivo.

**Como** cajero,
**quiero** que el cajón de dinero se abra automáticamente al cobrar en efectivo,
**para** entregar el vuelto rápidamente.

**Criterios de aceptación:**
1. **Dado** que la venta incluye un pago en efectivo, **cuando** se finaliza el cobro, **entonces** el cajón se abre automáticamente.
2. Cada apertura del cajón sin venta queda registrada en la bitácora.

**Prioridad:** Futura

---

## HU-07.10 — Reembolsar una devolución

> **Fuera de la primera versión.** No se manejan devoluciones.

**Como** supervisor,
**quiero** registrar cómo se reembolsa el dinero de una devolución,
**para** que la caja y los reportes reflejen la salida de dinero.

**Criterios de aceptación:**
1. **Dado** que se registra una devolución (HU-06.16), **entonces** el sistema pide el método de reembolso.
2. El reembolso queda asociado a la nota de crédito, la caja y el usuario, y se registra en la bitácora.

**Prioridad:** Futura
