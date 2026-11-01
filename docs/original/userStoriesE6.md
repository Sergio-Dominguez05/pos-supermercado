# Historias de usuario — EP-06: Ventas (POS)

## Contexto

Esta épica cubre la pantalla principal de venta, donde el cajero registra los productos y finaliza la venta para pasar al cobro.

**Decisiones de alcance de la primera versión:**
- El sistema **no controla inventario** ni valida existencias.
- Todos los productos se venden **por unidad**: cada artículo tiene un precio individual. No hay venta por peso.
- **No hay descuentos ni devoluciones.**
- Ninguna operación requiere autorización de un supervisor una vez iniciada la sesión.
- Las cajas **no tienen visor** para el cliente.

**Roles involucrados:** Cajero, Supervisor, Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-06.01 | Iniciar una venta | Alta |
| HU-06.02 | Agregar producto con lector de código de barras | Alta |
| HU-06.03 | Agregar producto por búsqueda o código manual | Alta |
| HU-06.04 | Indicar la cantidad de un producto | Alta |
| HU-06.05 | Vender productos por peso con balanza | Futura |
| HU-06.06 | Ingresar el peso manualmente | Futura |
| HU-06.07 | Modificar o eliminar una línea | Alta |
| HU-06.08 | Ver totales en tiempo real | Alta |
| HU-06.09 | Mantener el precio durante la venta | Alta |
| HU-06.10 | Aplicar descuento a una línea | Futura |
| HU-06.11 | Aplicar descuento al total | Futura |
| HU-06.12 | Suspender una venta | Media |
| HU-06.13 | Retomar una venta suspendida | Media |
| HU-06.14 | Cancelar una venta en curso | Alta |
| HU-06.15 | Finalizar la venta y pasar al cobro | Alta |
| HU-06.16 | Realizar una devolución | Futura |
| HU-06.17 | Operar con atajos de teclado | Alta |
| HU-06.18 | Mostrar la venta al cliente en un visor | Futura |

---

## HU-06.01 — Iniciar una venta

**Como** cajero,
**quiero** que siempre haya una venta lista para empezar a escanear,
**para** atender al siguiente cliente sin pasos adicionales.

**Criterios de aceptación:**
1. **Dado** que tengo una caja abierta (EP-09), **entonces** la pantalla de venta está lista para recibir productos.
2. **Dado** que no tengo una caja abierta, **entonces** el sistema no permite vender y me pide abrir caja.
3. Al terminar una venta, el sistema queda listo para la siguiente de forma automática.
4. Cada venta tiene un número interno único.

**Prioridad:** Alta

---

## HU-06.02 — Agregar producto con lector de código de barras

**Como** cajero,
**quiero** agregar productos escaneando su código de barras,
**para** registrar la compra lo más rápido posible.

**Criterios de aceptación:**
1. **Dado** que escaneo un producto activo, **entonces** se agrega a la venta con cantidad 1 y su precio vigente.
2. **Dado** que escaneo un producto que ya está en la venta, **entonces** se suma 1 a la cantidad de esa línea; no se crea una línea nueva.
3. El sistema da una confirmación visual y sonora al agregar el producto.
4. **Dado** que el código no existe o el producto está inactivo, **entonces** se aplica el comportamiento de EP-03 (HU-03.08 y HU-03.09).

**Prioridad:** Alta

---

## HU-06.03 — Agregar producto por búsqueda o código manual

**Como** cajero,
**quiero** agregar productos escribiendo su código o buscándolos por descripción,
**para** venderlos cuando el código de barras no se puede leer.

**Criterios de aceptación:**
1. Puedo escribir el código de barras y presionar Enter para agregar el producto.
2. Puedo abrir la búsqueda por descripción (HU-03.07), seleccionar el producto y agregarlo a la venta.
3. **Dado** que el producto ya está en la venta, **entonces** se suma a la cantidad de su línea, igual que al escanearlo.
4. Todo el proceso se puede hacer solo con el teclado.

**Prioridad:** Alta

---

## HU-06.04 — Indicar la cantidad de un producto

**Como** cajero,
**quiero** indicar la cantidad antes o después de agregar un producto,
**para** registrar varias unidades sin escanear cada una.

**Criterios de aceptación:**
1. Puedo escribir la cantidad seguida de un multiplicador (por ejemplo, "6*") y luego escanear el producto para agregar 6 unidades.
2. Puedo modificar la cantidad de una línea ya agregada.
3. La cantidad debe ser un número entero mayor que cero.
4. **Dado** que ingreso una cantidad inusualmente alta (mayor al límite configurado), **entonces** el sistema me pide confirmar la cantidad para evitar errores de digitación.

**Prioridad:** Alta

---

## HU-06.05 — Vender productos por peso con balanza

> **Fuera de la primera versión.** Requiere agregar al catálogo el tipo de venta (unidad o peso) y la integración con balanzas.

**Como** cajero,
**quiero** que el peso de los productos a granel se tome de la balanza,
**para** cobrar exactamente lo que pesa el producto sin escribirlo.

**Criterios de aceptación:**
1. **Dado** que agrego un producto por peso, **entonces** el sistema lee el peso de la balanza conectada y lo usa como cantidad.
2. **Dado** que la balanza marca cero o un peso inestable, **entonces** el sistema no agrega el producto y me avisa.
3. La línea muestra el peso, el precio por unidad de medida y el subtotal.

**Prioridad:** Futura

---

## HU-06.06 — Ingresar el peso manualmente

> **Fuera de la primera versión.** Depende de HU-06.05.

**Como** cajero,
**quiero** escribir el peso de un producto cuando la balanza no funciona,
**para** no detener la venta.

**Criterios de aceptación:**
1. Puedo ingresar el peso con hasta 3 decimales.
2. El ingreso manual queda registrado en la bitácora.

**Prioridad:** Futura

---

## HU-06.07 — Modificar o eliminar una línea

**Como** cajero,
**quiero** modificar o eliminar productos de la venta antes de cobrar,
**para** corregir errores o quitar lo que el cliente ya no quiere.

**Criterios de aceptación:**
1. Puedo seleccionar una línea y cambiar su cantidad o eliminarla.
2. Eliminar una línea no requiere autorización de un supervisor.
3. Las líneas eliminadas quedan registradas en la bitácora con el producto, la cantidad y el usuario.
4. Los totales se recalculan de inmediato.

**Prioridad:** Alta

---

## HU-06.08 — Ver totales en tiempo real

**Como** cajero,
**quiero** ver el total de la venta mientras agrego productos,
**para** informar al cliente cuánto lleva en todo momento.

**Criterios de aceptación:**
1. La pantalla muestra en todo momento la cantidad de artículos y la sección de totales en este orden: **Subtotal**, **IVA** y **TOTAL**.
2. El TOTAL se muestra en letra grande y fácil de leer.
3. Los cálculos y el redondeo siguen las reglas de EP-04.

**Prioridad:** Alta

---

## HU-06.09 — Mantener el precio durante la venta

**Como** cajero,
**quiero** que los precios de los productos agregados no cambien mientras atiendo al cliente,
**para** cobrar lo mismo que le informé.

**Criterios de aceptación:**
1. Cada línea guarda el precio vigente en el momento en que se agregó.
2. **Dado** que un administrador cambia el precio de un producto durante la venta (HU-03.02), **entonces** los precios de las líneas ya agregadas no cambian.
3. Los productos que se agreguen después del cambio usan el precio nuevo.

**Prioridad:** Alta

---

## HU-06.10 — Aplicar descuento a una línea

> **Fuera de la primera versión.** No se manejan descuentos.

**Como** cajero,
**quiero** aplicar un descuento a un producto específico,
**para** respetar rebajas autorizadas.

**Criterios de aceptación:**
1. Puedo aplicar el descuento como porcentaje o como monto fijo.
2. El descuento no puede hacer que la línea quede en cero o en negativo.
3. Debo indicar un motivo, y el descuento queda registrado en la bitácora.

**Prioridad:** Futura

---

## HU-06.11 — Aplicar descuento al total

> **Fuera de la primera versión.** No se manejan descuentos.

**Como** cajero,
**quiero** aplicar un descuento a toda la venta,
**para** respetar descuentos generales autorizados.

**Criterios de aceptación:**
1. Puedo aplicar el descuento como porcentaje o como monto fijo.
2. El descuento se distribuye entre las líneas para calcular correctamente el IVA (HU-04.06).
3. Debo indicar un motivo, y el descuento queda registrado en la bitácora.

**Prioridad:** Futura

---

## HU-06.12 — Suspender una venta

**Como** cajero,
**quiero** dejar una venta en espera,
**para** atender a otro cliente mientras el primero busca un producto o su tarjeta.

**Criterios de aceptación:**
1. Puedo suspender la venta en curso; el sistema la guarda con todas sus líneas y queda listo para una nueva venta.
2. Puedo tener varias ventas suspendidas en mi caja.
3. Las ventas suspendidas no generan factura ni afectan la caja.
4. **Dado** que intento cerrar la caja con ventas suspendidas, **entonces** el sistema me pide retomarlas o cancelarlas antes.

**Prioridad:** Media

---

## HU-06.13 — Retomar una venta suspendida

**Como** cajero,
**quiero** recuperar una venta que dejé en espera,
**para** terminar de atender al cliente sin volver a escanear sus productos.

**Criterios de aceptación:**
1. Veo la lista de ventas suspendidas con su hora, cantidad de artículos y total.
2. **Dado** que selecciono una, **entonces** se carga como venta en curso con todas sus líneas.
3. Solo puedo retomar ventas suspendidas de mi propia caja.
4. Las líneas retomadas mantienen el precio con el que se agregaron.

**Prioridad:** Media

---

## HU-06.14 — Cancelar una venta en curso

**Como** cajero,
**quiero** cancelar una venta que todavía no se ha cobrado,
**para** descartarla cuando el cliente decide no comprar o su tarjeta es rechazada.

**Criterios de aceptación:**
1. El sistema pide confirmación antes de cancelar.
2. Cancelar no requiere autorización de un supervisor.
3. Una venta cancelada no genera factura ni afecta la caja.
4. La cancelación queda registrada en la bitácora con el detalle de los productos.

**Prioridad:** Alta

---

## HU-06.15 — Finalizar la venta y pasar al cobro

**Como** cajero,
**quiero** indicar que terminé de registrar los productos,
**para** pasar al cobro con tarjeta.

**Criterios de aceptación:**
1. **Dado** que la venta tiene al menos un producto, **cuando** selecciono "Cobrar", **entonces** el sistema abre la pantalla de pago con tarjeta (EP-07).
2. **Dado** que la venta no tiene productos, **entonces** no puedo pasar al cobro.
3. Antes de cobrar, el sistema valida que el cliente asignado cumpla las reglas de EP-05 (por ejemplo, el límite de Q2,500.00 para Consumidor Final).
4. Desde la pantalla de pago puedo regresar a la venta para agregar o quitar productos.

**Prioridad:** Alta

---

## HU-06.16 — Realizar una devolución

> **Fuera de la primera versión.** No se manejan devoluciones.

**Como** supervisor,
**quiero** registrar la devolución de productos de una venta ya facturada,
**para** reembolsar al cliente y emitir la nota de crédito correspondiente.

**Criterios de aceptación:**
1. Busco la venta original por número de factura, número de venta o fecha.
2. Selecciono los productos y las cantidades a devolver; no se puede devolver más de lo vendido.
3. La devolución genera una nota de crédito (EP-08) y un reembolso (EP-07).
4. La devolución queda registrada en la bitácora.

**Prioridad:** Futura

---

## HU-06.17 — Operar con atajos de teclado

**Como** cajero,
**quiero** usar atajos de teclado para las acciones frecuentes,
**para** trabajar más rápido sin usar el mouse.

**Criterios de aceptación:**
1. Hay atajos para: buscar producto, cambiar cantidad, eliminar línea, asignar cliente, consultar precio, suspender venta, retomar venta, cancelar venta y cobrar.
2. Los atajos están visibles en pantalla o en una ayuda rápida.
3. Una venta típica se puede completar usando solo el lector de código de barras y el teclado.

**Prioridad:** Alta

---

## HU-06.18 — Mostrar la venta al cliente en un visor

> **Fuera de la primera versión.** Las cajas no tendrán visor por ahora.

**Como** cliente del supermercado,
**quiero** ver en una pantalla los productos que me registran y el total,
**para** confirmar que me cobran correctamente.

**Criterios de aceptación:**
1. **Dado** que la caja tiene un visor o segunda pantalla, **entonces** se muestran el último producto agregado, su precio y el total acumulado.
2. El visor no muestra información interna.

**Prioridad:** Futura
