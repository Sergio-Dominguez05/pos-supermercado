# Historias de usuario — EP-12: Configuración

## Contexto

Esta épica administra los parámetros generales del sistema, para adaptarlo al negocio sin cambiar el código. Solo el rol **Administrador** puede modificar la configuración, y todos los cambios quedan registrados en la bitácora (EP-11).

**Roles involucrados:** Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-12.01 | Configurar los datos del emisor | Alta |
| HU-12.02 | Configurar el establecimiento registrado en la SAT | Alta |
| HU-12.03 | Configurar la conexión con el certificador FEL | Alta |
| HU-12.04 | Configurar la conexión con el sistema de inventario | Futura |
| HU-12.05 | Registrar las cajas | Alta |
| HU-12.06 | Configurar los métodos de pago | Futura |
| HU-12.07 | Configurar el límite para Consumidor Final | Alta |
| HU-12.08 | Configurar las impresoras | Alta |
| HU-12.09 | Personalizar el formato del comprobante | Media |
| HU-12.10 | Configurar la balanza | Futura |
| HU-12.11 | Configurar los límites de descuento por rol | Futura |
| HU-12.12 | Configurar los parámetros de seguridad | Futura |
| HU-12.13 | Configurar los parámetros de caja y venta | Media |
| HU-12.14 | Configurar las autorizaciones requeridas | Futura |
| HU-12.15 | Proteger los datos sensibles de la configuración | Alta |

---

## HU-12.01 — Configurar los datos del emisor

**Como** administrador,
**quiero** registrar los datos fiscales de la empresa,
**para** que aparezcan correctamente en las facturas.

**Criterios de aceptación:**
1. Puedo registrar: NIT, razón social, nombre comercial, dirección fiscal, teléfono y correo.
2. El sistema valida el formato del NIT (HU-05.03).
3. Los datos se usan automáticamente en las facturas (EP-08).
4. Los cambios no afectan las facturas ya emitidas.

**Prioridad:** Alta

---

## HU-12.02 — Configurar el establecimiento registrado en la SAT

**Como** administrador,
**quiero** registrar los datos del establecimiento tal como están inscritos en la SAT,
**para** que las facturas se emitan a nombre del establecimiento correcto.

**Criterios de aceptación:**
1. Puedo registrar: código de establecimiento, nombre del establecimiento y dirección.
2. Los datos se incluyen en cada factura.
3. Si se usa el módulo de sucursales (EP-13), cada sucursal tiene su propio establecimiento.

**Prioridad:** Alta

---

## HU-12.03 — Configurar la conexión con el certificador FEL

**Como** administrador,
**quiero** registrar los datos de conexión con el certificador FEL,
**para** que el sistema pueda certificar las facturas.

**Criterios de aceptación:**
1. Puedo registrar: certificador, dirección del servicio, usuario, llave o token y ambiente (pruebas o producción).
2. Puedo probar la conexión antes de guardar.
3. Cambiar de ambiente de pruebas a producción requiere una confirmación explícita.
4. Puedo configurar el tiempo límite de respuesta antes de pasar a contingencia.

**Prioridad:** Alta

---

## HU-12.04 — Configurar la conexión con el sistema de inventario

> **Fuera de la primera versión.** Se desarrollará junto con EP-02.

**Como** administrador,
**quiero** registrar los datos de conexión con el sistema de inventario y la frecuencia de sincronización,
**para** mantener el catálogo actualizado.

**Criterios de aceptación:**
1. Puedo registrar: dirección del servicio y credenciales de acceso.
2. Puedo definir la frecuencia de sincronización automática (por ejemplo, cada 15 minutos).
3. Puedo definir el tiempo de espera entre reintentos cuando falla la sincronización.
4. Puedo definir el tiempo máximo sin actualizar el catálogo antes de mostrar un aviso.
5. Puedo probar la conexión antes de guardar (HU-02.02).

**Prioridad:** Futura

---

## HU-12.05 — Registrar las cajas

**Como** administrador,
**quiero** registrar las cajas del supermercado,
**para** que cada venta, turno y factura quede asociado a la caja donde se hizo.

**Criterios de aceptación:**
1. Puedo crear, editar, activar y desactivar cajas.
2. Cada caja tiene: número o nombre, serie de facturación (si aplica) e impresora asignada.
3. No se puede desactivar una caja con un turno abierto.
4. Cada equipo físico queda vinculado a una caja registrada.

**Prioridad:** Alta

---

## HU-12.06 — Configurar los métodos de pago

> **Fuera de la primera versión.** Por ahora el único método de pago es tarjeta (débito o crédito) y no es configurable.

**Como** administrador,
**quiero** definir los métodos de pago que acepta el supermercado,
**para** que los cajeros solo puedan usar los autorizados.

**Criterios de aceptación:**
1. Puedo activar o desactivar los métodos de pago: efectivo, tarjeta de débito, tarjeta de crédito y transferencia.
2. Para cada método puedo indicar si requiere referencia o número de autorización.

**Prioridad:** Futura

---

## HU-12.07 — Configurar el límite para Consumidor Final

**Como** administrador,
**quiero** definir el monto máximo para facturar como Consumidor Final,
**para** actualizarlo si la SAT lo cambia sin modificar el sistema.

**Criterios de aceptación:**
1. Puedo registrar el monto límite en quetzales. El valor inicial es Q2,500.00.
2. El valor se aplica de inmediato en la validación de las ventas (HU-05.07).
3. El cambio queda registrado en la bitácora con el valor anterior y el nuevo.

**Prioridad:** Alta

---

## HU-12.08 — Configurar las impresoras

**Como** administrador,
**quiero** configurar la impresora de cada caja,
**para** que los tickets se impriman correctamente.

**Criterios de aceptación:**
1. Puedo seleccionar la impresora y el ancho de papel (por ejemplo, 58 mm u 80 mm).
2. Puedo imprimir una página de prueba.
3. Puedo definir la cantidad de copias por tipo de documento (factura y cierre de caja).

**Prioridad:** Alta

---

## HU-12.09 — Personalizar el formato del comprobante

**Como** administrador,
**quiero** personalizar el ticket con el logo y mensajes del supermercado,
**para** reforzar la imagen del negocio.

**Criterios de aceptación:**
1. Puedo subir el logo del supermercado en blanco y negro.
2. Puedo agregar mensajes en el encabezado y en el pie (por ejemplo, "Gracias por su compra").
3. Puedo ver una vista previa del ticket antes de guardar.
4. La personalización no puede quitar los datos que exige la SAT ni cambiar el formato de totales (Subtotal, IVA, TOTAL).

**Prioridad:** Media

---

## HU-12.10 — Configurar la balanza

> **Fuera de la primera versión.** Se desarrollará junto con la venta por peso (HU-06.05).

**Como** administrador,
**quiero** configurar la balanza de cada caja,
**para** que el sistema lea correctamente el peso de los productos.

**Criterios de aceptación:**
1. Puedo seleccionar el modelo o protocolo de la balanza y el puerto de conexión.
2. Puedo definir la unidad de peso (kilogramos o libras).
3. Puedo probar la lectura de peso desde la configuración.
4. Puedo configurar el formato de las etiquetas de balanza con peso o precio incluido en el código de barras, si se usan.

**Prioridad:** Futura

---

## HU-12.11 — Configurar los límites de descuento por rol

> **Fuera de la primera versión.** No se manejan descuentos.

**Como** administrador,
**quiero** definir el descuento máximo que puede aplicar cada rol sin autorización,
**para** controlar las rebajas.

**Criterios de aceptación:**
1. Puedo definir para cada rol el porcentaje máximo de descuento por línea y por total.
2. Un límite de 0 % indica que el rol siempre necesita autorización para aplicar descuentos.
3. Puedo administrar la lista de motivos de descuento.

**Prioridad:** Futura

---

## HU-12.12 — Configurar los parámetros de seguridad

> **Fuera de la primera versión.** No habrá bloqueo por inactividad, y el bloqueo por intentos fallidos (HU-01.04) queda para una versión futura.

**Como** administrador,
**quiero** definir los parámetros de seguridad de las sesiones,
**para** ajustarlos a las políticas del supermercado.

**Criterios de aceptación:**
1. Puedo definir el número de intentos fallidos antes de bloquear un usuario (HU-01.04).
2. El valor tiene mínimos y máximos razonables para no debilitar la seguridad.

**Prioridad:** Futura

---

## HU-12.13 — Configurar los parámetros de caja y venta

**Como** administrador,
**quiero** definir los parámetros de control de caja y de venta,
**para** adaptarlos a la forma de trabajar del supermercado.

**Criterios de aceptación:**
1. Puedo activar o desactivar el cierre ciego (HU-09.11).
2. Puedo definir la cantidad máxima por línea antes de pedir confirmación al cajero (HU-06.04).

**Prioridad:** Media

---

## HU-12.14 — Configurar las autorizaciones requeridas

> **Fuera de la primera versión.** Por ahora ninguna operación requiere autorización de un supervisor una vez iniciada la sesión.

**Como** administrador,
**quiero** elegir qué operaciones requieren autorización de un supervisor,
**para** equilibrar el control con la rapidez de atención.

**Criterios de aceptación:**
1. Puedo activar o desactivar la autorización de un supervisor para: eliminar líneas y cancelar ventas.

**Prioridad:** Futura

---

## HU-12.15 — Proteger los datos sensibles de la configuración

**Como** administrador,
**quiero** que las credenciales y llaves de conexión estén protegidas,
**para** evitar su uso indebido.

**Criterios de aceptación:**
1. Las credenciales del certificador FEL se almacenan cifradas (y las del sistema de inventario, cuando se integre).
2. Las credenciales nunca se muestran completas en pantalla después de guardarlas.
3. Solo el rol Administrador puede ver y modificar la configuración.
4. Todos los cambios de configuración quedan registrados en la bitácora (HU-11.03).

**Prioridad:** Alta

---

## Pendientes por confirmar con el cliente

- Datos fiscales de la empresa y del establecimiento inscrito en la SAT.
- Cantidad de cajas y el equipo de cada una (impresora y lector de código de barras).
- Ancho de papel de las impresoras térmicas.
- Logo y mensajes que desean en el ticket.
