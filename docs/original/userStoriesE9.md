# Historias de usuario — EP-09: Control de caja

## Contexto

Esta épica administra el ciclo de cada caja: apertura del turno, consulta de lo vendido y cierre. Como en la primera versión **solo se aceptan pagos con tarjeta**, el cierre no cuenta efectivo: compara el total registrado en el POS con el **cierre de lote** de la terminal bancaria, para detectar pagos mal registrados. **Cada caja tiene su propia terminal bancaria** y hace su cierre de forma individual.

**Fuera de la primera versión:** fondo inicial, entradas y retiros de efectivo, arqueo por denominación y aviso de exceso de efectivo. Esas historias se conservan como referencia con prioridad Futura.

**Roles involucrados:** Cajero, Supervisor, Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID       | Historia                                 | Prioridad |
| -------- | ---------------------------------------- | --------- |
| HU-09.01 | Abrir el turno de caja                   | Alta      |
| HU-09.02 | Impedir ventas sin caja abierta          | Alta      |
| HU-09.03 | Controlar un solo turno abierto por caja | Alta      |
| HU-09.04 | Registrar entrada de efectivo            | Futura    |
| HU-09.05 | Registrar retiro de efectivo             | Futura    |
| HU-09.06 | Consultar el resumen del turno           | Media     |
| HU-09.07 | Realizar el arqueo por denominación      | Futura    |
| HU-09.08 | Cerrar la caja                           | Alta      |
| HU-09.09 | Justificar diferencias en el cierre      | Alta      |
| HU-09.10 | Imprimir el comprobante de cierre        | Alta      |
| HU-09.11 | Realizar cierre ciego                    | Media     |
| HU-09.12 | Cerrar la caja de otro usuario           | Media     |
| HU-09.13 | Consultar cierres anteriores             | Media     |
| HU-09.14 | Avisar exceso de efectivo en caja        | Futura    |

---

## HU-09.01 — Abrir el turno de caja

**Como** cajero,
**quiero** abrir mi turno en la caja,
**para** empezar a vender.

**Criterios de aceptación:**

1. **Dado** que inicio sesión en una caja cerrada, **entonces** el sistema me pide abrir el turno.
2. La apertura registra: caja, usuario y fecha y hora.
3. No se registra fondo inicial, porque no se manejan pagos en efectivo.
4. La apertura queda registrada en la bitácora.

**Prioridad:** Alta

---

## HU-09.02 — Impedir ventas sin caja abierta

**Como** supervisor,
**quiero** que no se pueda vender si la caja no está abierta,
**para** que todos los cobros queden asociados a un turno.

**Criterios de aceptación:**

1. **Dado** que la caja está cerrada, **entonces** la pantalla de venta está deshabilitada.
2. Toda venta, pago y anulación queda asociada al turno de caja abierto.

**Prioridad:** Alta

---

## HU-09.03 — Controlar un solo turno abierto por caja

**Como** supervisor,
**quiero** que cada caja tenga un solo turno abierto a la vez y cada cajero opere una sola caja,
**para** que no se mezclen las ventas de distintas personas.

**Criterios de aceptación:**

1. **Dado** que una caja tiene un turno abierto, **entonces** no se puede abrir otro en esa caja.
2. **Dado** que un cajero tiene un turno abierto en una caja, **entonces** no puede abrir otro en una caja distinta.
3. **Dado** que otro usuario inicia sesión en una caja con turno abierto, **entonces** el sistema le avisa que debe cerrarse primero el turno del usuario anterior.

**Prioridad:** Alta

---

## HU-09.04 — Registrar entrada de efectivo

> **Fuera de la primera versión.** No se manejan pagos en efectivo.

**Como** cajero,
**quiero** registrar el efectivo que entra a la caja y que no corresponde a una venta,
**para** que el efectivo esperado al cierre sea correcto.

**Criterios de aceptación:**

1. Debo ingresar el monto y el motivo.
2. El monto se suma al efectivo esperado de la caja y queda registrado en la bitácora.

**Prioridad:** Futura

---

## HU-09.05 — Registrar retiro de efectivo

> **Fuera de la primera versión.** No se manejan pagos en efectivo.

**Como** supervisor,
**quiero** registrar los retiros de efectivo de una caja durante el turno,
**para** reducir el dinero acumulado y mantener cuadrado el efectivo esperado.

**Criterios de aceptación:**

1. Debo ingresar el monto y el motivo.
2. El monto se resta del efectivo esperado y queda registrado en la bitácora.

**Prioridad:** Futura

---

## HU-09.06 — Consultar el resumen del turno

**Como** supervisor,
**quiero** ver el resumen de un turno abierto,
**para** conocer cuánto se ha vendido en esa caja.

**Criterios de aceptación:**

1. El resumen muestra: cantidad de ventas, total cobrado con tarjeta de débito, total cobrado con tarjeta de crédito, total general y facturas anuladas.
2. Puedo consultar el resumen sin cerrar la caja.
3. Los cajeros solo pueden ver el resumen de su propio turno.

**Prioridad:** Media

---

## HU-09.07 — Realizar el arqueo por denominación

> **Fuera de la primera versión.** No se manejan pagos en efectivo.

**Como** cajero,
**quiero** contar el efectivo ingresando la cantidad de cada billete y moneda,
**para** que el sistema calcule el total contado sin errores.

**Criterios de aceptación:**

1. El sistema muestra las denominaciones del quetzal.
2. Ingreso la cantidad de cada denominación y el sistema calcula el total contado.

**Prioridad:** Futura

---

## HU-09.08 — Cerrar la caja

**Como** cajero,
**quiero** cerrar mi caja al terminar el turno,
**para** entregar cuentas de lo cobrado.

**Criterios de aceptación:**

1. **Dado** que hay ventas suspendidas o una venta en curso, **entonces** el sistema me pide resolverlas antes de cerrar.
2. Debo ingresar el total del cierre de lote que entrega la terminal bancaria de mi caja.
3. El cierre compara el total cobrado con tarjeta registrado en el POS contra el total del cierre de lote y muestra la diferencia.
4. **Dado** que confirmo el cierre, **entonces** el turno queda cerrado y no se puede modificar.
5. El cierre registra: caja, usuario, fecha y hora de apertura y cierre, total registrado, total del cierre de lote y diferencia.
6. El cierre queda registrado en la bitácora.

**Prioridad:** Alta

---

## HU-09.09 — Justificar diferencias en el cierre

**Como** supervisor,
**quiero** que las diferencias del cierre lleven una justificación,
**para** dar seguimiento a los pagos mal registrados.

**Criterios de aceptación:**

1. **Dado** que hay una diferencia entre el total registrado y el cierre de lote, **entonces** el sistema pide un comentario antes de confirmar el cierre.
2. Las diferencias quedan disponibles en los reportes de cierres (EP-10).

**Prioridad:** Alta

---

## HU-09.10 — Imprimir el comprobante de cierre

**Como** cajero,
**quiero** imprimir el comprobante del cierre de caja,
**para** entregarlo junto con el comprobante del cierre de lote al supervisor.

**Criterios de aceptación:**

1. El comprobante incluye: caja, cajero, fechas de apertura y cierre, cantidad de ventas, total por tipo de tarjeta, facturas anuladas, total registrado, total del cierre de lote, diferencia y justificación.
2. Incluye espacio para las firmas del cajero y del supervisor.
3. Se puede reimprimir desde la consulta de cierres anteriores.

**Prioridad:** Alta

---

## HU-09.11 — Realizar cierre ciego

**Como** supervisor,
**quiero** que el cajero ingrese el total del cierre de lote sin ver el total registrado en el POS,
**para** obtener un cuadre honesto y detectar diferencias reales.

**Criterios de aceptación:**

1. **Dado** que el cierre ciego está activado en la configuración, **entonces** el cajero no ve el total registrado mientras ingresa el total del cierre de lote.
2. El total registrado y la diferencia se muestran solo después de confirmar el monto ingresado.
3. Los supervisores siempre pueden ver el total registrado.

**Prioridad:** Media

---

## HU-09.12 — Cerrar la caja de otro usuario

**Como** supervisor,
**quiero** cerrar la caja de un cajero que no la cerró,
**para** que la caja quede disponible para el siguiente turno.

**Criterios de aceptación:**

1. Puedo seleccionar un turno abierto y realizar su cierre.
2. El cierre registra al cajero del turno y al supervisor que lo cerró.
3. Requiere el permiso correspondiente y queda registrado en la bitácora.

**Prioridad:** Media

---

## HU-09.13 — Consultar cierres anteriores

**Como** supervisor,
**quiero** consultar los cierres de caja anteriores,
**para** revisar diferencias y resolver reclamos.

**Criterios de aceptación:**

1. La lista muestra: fecha, caja, cajero, total registrado, total del cierre de lote y diferencia.
2. Puedo filtrar por fecha, caja, cajero y si tuvo diferencias.
3. Puedo ver el detalle completo y reimprimir el comprobante de cierre.

**Prioridad:** Media

---

## HU-09.14 — Avisar exceso de efectivo en caja

> **Fuera de la primera versión.** No se manejan pagos en efectivo.

**Como** supervisor,
**quiero** que el sistema avise cuando una caja acumula demasiado efectivo,
**para** hacer un retiro parcial y reducir el riesgo de pérdida o robo.

**Criterios de aceptación:**

1. **Dado** que el efectivo esperado supera el monto máximo configurado, **entonces** el sistema muestra una alerta.

**Prioridad:** Futura

---

## Pendientes por confirmar con el cliente

- Si se usará cierre ciego.
  Se implementa cierre ciego, donde el cajero ingresa lo cobrado sin ver el total registrado en el sistema.

- Si existe un cierre diario general además de los cierres por turno.
  Cada turno hace su cierre individual y se habilita un resumen diario consolidado para administración.
