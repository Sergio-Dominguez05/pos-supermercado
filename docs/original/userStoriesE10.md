# Historias de usuario — EP-10: Reportes

## Contexto

Esta épica permite consultar información de ventas, ingresos, IVA y cajas para la toma de decisiones. Los reportes de existencias e inventario **no se incluyen**, porque el sistema no controla inventario.

**Roles involucrados:** Supervisor, Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID       | Historia                                | Prioridad |
| -------- | --------------------------------------- | --------- |
| HU-10.01 | Aplicar filtros comunes a los reportes  | Alta      |
| HU-10.02 | Reporte de ventas por período           | Alta      |
| HU-10.03 | Reporte de ventas por cajero            | Alta      |
| HU-10.04 | Reporte de ventas por caja              | Media     |
| HU-10.05 | Reporte de productos más vendidos       | Media     |
| HU-10.06 | Reporte de ingresos por tipo de tarjeta | Alta      |
| HU-10.07 | Reporte de IVA cobrado                  | Alta      |
| HU-10.08 | Reporte de cierres de caja              | Alta      |
| HU-10.09 | Reporte de facturas anuladas            | Alta      |
| HU-10.10 | Reporte de descuentos aplicados         | Futura    |
| HU-10.11 | Reporte de ventas por hora              | Baja      |
| HU-10.12 | Panel resumen del día                   | Media     |
| HU-10.13 | Exportar reportes a PDF                 | Alta      |
| HU-10.14 | Exportar reportes a Excel               | Alta      |
| HU-10.15 | Restringir el acceso a los reportes     | Alta      |

---

## HU-10.01 — Aplicar filtros comunes a los reportes

**Como** supervisor,
**quiero** filtrar los reportes de forma consistente,
**para** obtener rápidamente la información que necesito.

**Criterios de aceptación:**

1. Todos los reportes permiten filtrar por rango de fechas.
2. Cuando aplica, se puede filtrar también por caja, cajero y tipo de tarjeta (débito o crédito).
3. Hay rangos rápidos: hoy, ayer, esta semana, este mes y mes anterior.
4. Los filtros aplicados se muestran en el encabezado del reporte y en los archivos exportados.

**Prioridad:** Alta

---

## HU-10.02 — Reporte de ventas por período

**Como** administrador,
**quiero** ver las ventas de un período,
**para** conocer el desempeño del supermercado.

**Criterios de aceptación:**

1. El reporte muestra: cantidad de ventas, Subtotal, IVA, TOTAL vendido y ticket promedio.
2. Puedo agrupar los resultados por día, semana o mes.
3. Las facturas anuladas no se suman a las ventas.

**Prioridad:** Alta

---

## HU-10.03 — Reporte de ventas por cajero

**Como** supervisor,
**quiero** ver las ventas realizadas por cada cajero,
**para** evaluar su desempeño y detectar comportamientos inusuales.

**Criterios de aceptación:**

1. El reporte muestra por cajero: cantidad de ventas, total vendido, ventas canceladas, líneas eliminadas y facturas anuladas.
2. Puedo ver el detalle de las ventas de un cajero específico.

**Prioridad:** Alta

---

## HU-10.04 — Reporte de ventas por caja

**Como** supervisor,
**quiero** ver las ventas de cada caja,
**para** saber cuáles se usan más y planificar el personal.

**Criterios de aceptación:**

1. El reporte muestra por caja: cantidad de ventas, total vendido y cantidad de turnos.
2. Puedo comparar las cajas en el mismo período.

**Prioridad:** Media

---

## HU-10.05 — Reporte de productos más vendidos

**Como** administrador,
**quiero** ver los productos más vendidos,
**para** apoyar decisiones de compras y promociones.

**Criterios de aceptación:**

1. El reporte muestra por producto: código, descripción, cantidad vendida y monto vendido.
2. Puedo ordenarlo por cantidad o por monto.
3. Puedo limitar el resultado (por ejemplo, los 20 o los 50 más vendidos).
4. Las facturas anuladas no se cuentan.

**Prioridad:** Media

---

## HU-10.06 — Reporte de ingresos por tipo de tarjeta

**Como** administrador,
**quiero** ver los ingresos agrupados por tipo de tarjeta,
**para** conciliarlos con los depósitos del banco.

**Criterios de aceptación:**

1. El reporte muestra el total recibido con tarjeta de débito y con tarjeta de crédito, y la cantidad de transacciones de cada una.
2. Los pagos de facturas anuladas se muestran aparte y no se suman al total.
3. Puedo ver el detalle de las transacciones de un método, con sus referencias o números de autorización.

**Prioridad:** Alta

---

## HU-10.07 — Reporte de IVA cobrado

**Como** administrador,
**quiero** ver el IVA cobrado en un período,
**para** apoyar la declaración mensual de impuestos ante la SAT.

**Criterios de aceptación:**

1. El reporte muestra: total de base imponible, total de IVA y total vendido.
2. Las facturas anuladas se excluyen.
3. Puedo ver el detalle por día y por factura.
4. Los totales coinciden con las facturas certificadas ante la SAT.

**Prioridad:** Alta

---

## HU-10.08 — Reporte de cierres de caja

**Como** supervisor,
**quiero** ver los cierres de caja de un período,
**para** controlar los faltantes y sobrantes.

**Criterios de aceptación:**

1. El reporte muestra por cierre: fecha, caja, cajero, total registrado con tarjeta, total del cierre de lote, diferencia y justificación.
2. Muestra el total de faltantes y sobrantes del período.
3. Puedo filtrar solo los cierres con diferencias.

**Prioridad:** Alta

---

## HU-10.09 — Reporte de facturas anuladas

**Como** supervisor,
**quiero** ver las facturas anuladas,
**para** controlar estas operaciones sensibles.

**Criterios de aceptación:**

1. El reporte muestra: fecha de emisión, fecha de anulación, serie y número, monto, motivo, cajero que emitió la factura y usuario que la anuló.
2. Puedo filtrar por fecha, cajero y usuario que anuló.

**Prioridad:** Alta

---

## HU-10.10 — Reporte de descuentos aplicados

> **Fuera de la primera versión.** No se manejan descuentos.

**Como** supervisor,
**quiero** ver los descuentos aplicados,
**para** controlar que se usen correctamente.

**Criterios de aceptación:**

1. El reporte muestra: fecha, factura, producto (si es por línea), monto o porcentaje, motivo y cajero.
2. Muestra el total descontado en el período.
3. Puedo agrupar por cajero o por motivo.

**Prioridad:** Futura

---

## HU-10.11 — Reporte de ventas por hora

**Como** administrador,
**quiero** ver las ventas por hora del día,
**para** identificar las horas de mayor movimiento y asignar más cajeros.

**Criterios de aceptación:**

1. El reporte muestra la cantidad de ventas y el total vendido por cada hora.
2. Puedo verlo para un día específico o como promedio de un período.
3. Se muestra con una gráfica de barras.

**Prioridad:** Baja

---

## HU-10.12 — Panel resumen del día

**Como** administrador,
**quiero** ver un resumen de la operación del día al entrar al sistema,
**para** conocer rápidamente cómo van las ventas.

**Criterios de aceptación:**

1. El panel muestra: total vendido hoy, cantidad de ventas, ticket promedio, cajas abiertas y ventas por tipo de tarjeta.
2. Muestra alertas relevantes: comprobantes en contingencia pendientes, códigos no encontrados en caja y cierres con diferencias.
3. La información se actualiza periódicamente sin recargar la pantalla.

**Prioridad:** Media

---

## HU-10.13 — Exportar reportes a PDF

**Como** supervisor,
**quiero** exportar los reportes a PDF,
**para** imprimirlos o compartirlos.

**Criterios de aceptación:**

1. Todos los reportes tienen la opción de exportar a PDF.
2. El PDF incluye: nombre del reporte, datos de la empresa, filtros aplicados, fecha y hora de generación y usuario que lo generó.
3. El PDF respeta el mismo orden y los mismos totales que se ven en pantalla.

**Prioridad:** Alta

---

## HU-10.14 — Exportar reportes a Excel

**Como** administrador,
**quiero** exportar los reportes a Excel,
**para** analizarlos o enviarlos al contador.

**Criterios de aceptación:**

1. Todos los reportes tienen la opción de exportar a Excel (.xlsx).
2. Los montos se exportan como números y las fechas como fechas, para que se puedan usar en fórmulas.
3. El archivo incluye los filtros aplicados en el encabezado.

**Prioridad:** Alta

---

## HU-10.15 — Restringir el acceso a los reportes

**Como** administrador,
**quiero** controlar qué roles pueden ver cada reporte,
**para** proteger la información financiera del negocio.

**Criterios de aceptación:**

1. El acceso a cada reporte depende de los permisos del rol (EP-01).
2. Por defecto, los cajeros no tienen acceso a los reportes.
3. La generación y exportación de reportes queda registrada en la bitácora.

**Prioridad:** Alta

---

## Pendientes por confirmar con el cliente

- Qué reportes usa hoy el negocio y cuáles son los más importantes.
  Reporte diario de ventas por caja, resumen mensual de IVA y listado de productos más vendidos.

- Qué información necesita el contador para la declaración de IVA y en qué formato.
  Exportación mensual en Excel/PDF con base imponible, IVA desglosado y correlativo de facturas.

- Si los supervisores deben ver todos los reportes o solo algunos.
  Supervisores solo ven reportes operativos de su sucursal/turno; Administrador tiene acceso total.

- Si se desea el panel resumen del día en la primera versión.
  Se incluye un tablero (dashboard) básico con el total vendido y facturas emitidas en el día.
