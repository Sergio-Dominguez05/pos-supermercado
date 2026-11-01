# Historias de usuario — EP-13: Sucursales (opcional)

## Contexto

Esta épica permite que el sistema opere en varias tiendas del supermercado y centralice la información de ventas. Es **opcional**: se desarrolla solo si el cliente confirma que tiene o tendrá más de una sucursal. Cada sucursal corresponde a un **establecimiento** inscrito en la SAT.

**Roles involucrados:** Administrador, Supervisor, Cajero.

**Prioridades:** Alta = indispensable si se incluye la épica · Media = importante, puede llegar después · Baja = opcional.

---

## Resumen de historias

| ID       | Historia                                       | Prioridad |
| -------- | ---------------------------------------------- | --------- |
| HU-13.01 | Registrar una sucursal                         | Alta      |
| HU-13.02 | Editar y desactivar una sucursal               | Alta      |
| HU-13.03 | Asociar la sucursal con su establecimiento SAT | Alta      |
| HU-13.04 | Asignar cajas a una sucursal                   | Alta      |
| HU-13.05 | Asignar usuarios a sucursales                  | Alta      |
| HU-13.06 | Operar en la sucursal asignada                 | Alta      |
| HU-13.07 | Asociar ventas y facturas a la sucursal        | Alta      |
| HU-13.08 | Configurar parámetros por sucursal             | Media     |
| HU-13.09 | Consultar reportes por sucursal                | Alta      |
| HU-13.10 | Consultar reportes consolidados                | Alta      |
| HU-13.11 | Comparar sucursales                            | Baja      |
| HU-13.12 | Usar precios por sucursal                      | Media     |

---

## HU-13.01 — Registrar una sucursal

**Como** administrador,
**quiero** registrar las sucursales del supermercado,
**para** operar el sistema en todas las tiendas.

**Criterios de aceptación:**

1. Puedo registrar: código interno, nombre, dirección y teléfono de la sucursal.
2. El código y el nombre de la sucursal no se pueden repetir.
3. La sucursal nueva queda activa por defecto.
4. El registro queda en la bitácora.

**Prioridad:** Alta

---

## HU-13.02 — Editar y desactivar una sucursal

**Como** administrador,
**quiero** modificar o desactivar una sucursal,
**para** mantener la información al día cuando una tienda cambia o cierra.

**Criterios de aceptación:**

1. Puedo modificar el nombre, la dirección y el teléfono.
2. **Dado** que desactivo una sucursal, **entonces** sus cajas no pueden abrir turnos nuevos.
3. No puedo desactivar una sucursal que tiene turnos de caja abiertos.
4. Las sucursales no se eliminan, para conservar su historial de ventas.
5. Los cambios quedan registrados en la bitácora.

**Prioridad:** Alta

---

## HU-13.03 — Asociar la sucursal con su establecimiento SAT

**Como** administrador,
**quiero** asociar cada sucursal con el establecimiento con el que está inscrita en la SAT,
**para** que sus facturas se emitan con el código de establecimiento correcto.

**Criterios de aceptación:**

1. Cada sucursal tiene un código de establecimiento, nombre del establecimiento y dirección, según la inscripción en la SAT (HU-12.02).
2. Dos sucursales no pueden tener el mismo código de establecimiento.
3. Una sucursal sin establecimiento configurado no puede emitir facturas.

**Prioridad:** Alta

---

## HU-13.04 — Asignar cajas a una sucursal

**Como** administrador,
**quiero** asignar cada caja a una sucursal,
**para** saber en qué tienda se hizo cada venta.

**Criterios de aceptación:**

1. Cada caja pertenece a una sola sucursal.
2. Al registrar una caja (HU-12.05), debo seleccionar su sucursal.
3. No puedo cambiar de sucursal una caja que tiene un turno abierto.

**Prioridad:** Alta

---

## HU-13.05 — Asignar usuarios a sucursales

**Como** administrador,
**quiero** indicar en qué sucursales puede trabajar cada usuario,
**para** limitar su acceso a las tiendas que le corresponden.

**Criterios de aceptación:**

1. Puedo asignar uno o varios usuarios a una o varias sucursales.
2. Los administradores tienen acceso a todas las sucursales.
3. Los cambios de asignación quedan registrados en la bitácora.

**Prioridad:** Alta

---

## HU-13.06 — Operar en la sucursal asignada

**Como** cajero,
**quiero** que el sistema reconozca automáticamente la sucursal donde estoy trabajando,
**para** no tener que seleccionarla en cada venta.

**Criterios de aceptación:**

1. La sucursal se determina por la caja en la que inicio sesión.
2. **Dado** que no estoy asignado a esa sucursal, **entonces** no puedo iniciar sesión en esa caja.
3. **Dado** que soy supervisor con varias sucursales, **entonces** puedo cambiar de sucursal en las pantallas de consulta, sin cerrar sesión.
4. La pantalla muestra siempre el nombre de la sucursal actual.

**Prioridad:** Alta

---

## HU-13.07 — Asociar ventas y facturas a la sucursal

**Como** administrador,
**quiero** que cada venta, factura, turno de caja y movimiento quede asociado a su sucursal,
**para** tener la información separada por tienda.

**Criterios de aceptación:**

1. Toda venta, factura, anulación y turno de caja registra la sucursal.
2. Las facturas se emiten con el establecimiento de la sucursal (HU-13.03).

**Prioridad:** Alta

---

## HU-13.08 — Configurar parámetros por sucursal

**Como** administrador,
**quiero** definir algunos parámetros propios de cada sucursal,
**para** adaptar el sistema a cada tienda.

**Criterios de aceptación:**

1. Puedo definir por sucursal los mensajes del ticket y si se usa cierre ciego.
2. Los parámetros que no se definan por sucursal usan la configuración general.
3. Los datos fiscales del emisor (NIT y razón social) son los mismos para todas las sucursales.

**Prioridad:** Media

---

## HU-13.09 — Consultar reportes por sucursal

**Como** supervisor,
**quiero** ver los reportes de la sucursal donde trabajo,
**para** controlar la operación de mi tienda.

**Criterios de aceptación:**

1. Todos los reportes de EP-10 tienen un filtro por sucursal.
2. Un supervisor solo ve los datos de las sucursales que tiene asignadas.
3. El nombre de la sucursal aparece en el encabezado de los reportes y en los archivos exportados.

**Prioridad:** Alta

---

## HU-13.10 — Consultar reportes consolidados

**Como** administrador,
**quiero** ver los reportes con la información de todas las sucursales juntas,
**para** conocer el desempeño total del negocio.

**Criterios de aceptación:**

1. Puedo generar los reportes de EP-10 para todas las sucursales a la vez.
2. Los reportes consolidados muestran el total general y el subtotal por sucursal.
3. El reporte de IVA consolidado coincide con la suma de los reportes de cada sucursal.

**Prioridad:** Alta

---

## HU-13.11 — Comparar sucursales

**Como** administrador,
**quiero** comparar las ventas de las sucursales,
**para** identificar las tiendas con mejor y peor desempeño.

**Criterios de aceptación:**

1. El reporte muestra por sucursal: total vendido, cantidad de ventas, ticket promedio y diferencias de caja.
2. Puedo comparar el mismo período entre sucursales.
3. Se muestra con una gráfica de barras.

**Prioridad:** Baja

---

## HU-13.12 — Usar precios por sucursal

**Como** administrador,
**quiero** definir precios distintos para un producto en cada sucursal,
**para** respetar las diferencias de precio entre tiendas si existen.

**Criterios de aceptación:**

1. Por defecto, todas las sucursales usan el precio general del catálogo (EP-03).
2. Puedo definir un precio específico de un producto para una sucursal; esa sucursal usa ese precio y las demás el general.
3. Puedo quitar el precio específico para que la sucursal vuelva a usar el precio general.
4. Los cambios de precio por sucursal quedan registrados en la bitácora.
5. Cuando se integre el sistema de inventario (EP-02), los precios por sucursal vendrán de ese sistema.

**Prioridad:** Media

---

## Decisiones y confirmaciones de negocio

- **Si el supermercado tiene o planea tener más de una sucursal, y si esta épica entra en la primera versión:**
  - _Respuesta:_ No entra en la primera versión. El proyecto se lanzará únicamente para 1 sucursal (Matriz), dejando el soporte multi-sucursal para una fase futura.

- **Si se necesitan precios distintos por sucursal o todas usan el mismo precio:**
  - _Respuesta:_ Para la primera versión todos los productos usan el mismo precio general del catálogo local.

- **Si todas las sucursales usan el mismo NIT o hay varias razones sociales:**
  - _Respuesta:_ Se usará una única razón social y un solo NIT configurado en los parámetros del sistema.

- **Si los supervisores trabajan en una sola sucursal o en varias:**
  - _Respuesta:_ Cada supervisor estará asignado a una sola sucursal y solo podrá autorizar o revisar operaciones de esa ubicación.
