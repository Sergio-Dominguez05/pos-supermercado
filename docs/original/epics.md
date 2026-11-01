# Épicas — Sistema de Punto de Venta (POS) para Supermercado

## Contexto general

Sistema de Punto de Venta para un supermercado en Guatemala. Registra ventas de forma rápida, controla los cobros con tarjeta y los turnos de caja, y emite facturas electrónicas bajo el régimen FEL de la SAT.

**Decisiones de alcance acordadas para la primera versión:**

- **Catálogo:** el POS tiene un **catálogo local propio**, almacenado en una base de datos **PostgreSQL**. Cada producto tiene **código de barras, descripción y precio**. Cada producto tiene un solo código de barras. Solo el Administrador crea productos y cambia precios.
- **Inventario:** el POS **no controla inventario** ni valida existencias. La integración con un sistema externo de inventario queda para una versión futura.
- **Venta por unidad:** cada artículo tiene un precio individual. No hay productos por peso.
- **IVA:** el único impuesto es el **IVA del 12 %**, incluido en el precio. Ningún producto está exento. Los cálculos por línea usan hasta 6 decimales y solo los totales finales se redondean a 2 decimales (al medio hacia arriba). El ticket muestra los totales como **Subtotal, IVA, TOTAL**.
- **Pagos:** **solo con tarjeta** (débito o crédito), en quetzales, con una sola tarjeta por venta. Cada caja tiene su propia terminal bancaria, que no está integrada: el cajero registra el número de autorización.
- **Sin descuentos ni devoluciones.**
- **Clientes:** NIT, CUI o Consumidor Final. Desde **Q2,500.00** se exige NIT o CUI. El certificador FEL ofrece la consulta de nombre por NIT y por CUI. El correo es opcional.
- **Acceso:** Administrador y Supervisor entran con contraseña; los cajeros con un **PIN corto**. Solo el Administrador restablece contraseñas y PIN.
- **Sin autorizaciones de supervisor:** una vez iniciada la sesión, cada usuario hace lo que permite su rol.
- **Sin** bloqueo por inactividad, bloqueo por intentos fallidos, visor para el cliente ni integración con terminales bancarias.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de épicas

| ID | Épica | Prioridad |
|----|-------|-----------|
| EP-01 | Seguridad y usuarios | Alta |
| EP-02 | Integración con sistema de inventario | Futura |
| EP-03 | Catálogo local de productos | Alta |
| EP-04 | Cálculo de IVA | Alta |
| EP-05 | Gestión de clientes | Alta |
| EP-06 | Ventas (POS) | Alta |
| EP-07 | Pagos | Alta |
| EP-08 | Comprobantes y facturación electrónica (FEL) | Alta |
| EP-09 | Control de caja | Alta |
| EP-10 | Reportes | Media |
| EP-11 | Auditoría (bitácora) | Media |
| EP-12 | Configuración | Alta |
| EP-13 | Sucursales | Baja |

---

## EP-01 — Seguridad y usuarios

**Descripción:** Controlar el acceso al sistema mediante usuarios, roles y permisos, para que cada persona solo pueda hacer lo que le corresponde.

**Objetivo de negocio:** Proteger la información y las operaciones sensibles (anulaciones, cierres de caja, precios) e identificar quién hizo cada operación.

**Incluye:**
- Inicio de sesión con usuario y contraseña (Administrador y Supervisor) y con usuario y PIN corto (Cajero).
- Cierre de sesión.
- Crear, editar, activar y desactivar usuarios.
- Roles predefinidos: Administrador, Supervisor y Cajero.
- Permisos por rol.
- Cambio de contraseña o PIN propio, y restablecimiento por parte del Administrador.

**No incluye:**
- Inicio de sesión con servicios externos (Google, Microsoft, etc.).
- Cierre de sesión automático por inactividad.
- Bloqueo por intentos fallidos (versión futura).
- Autorización de un supervisor dentro de la sesión de otro usuario (versión futura).

**Criterios de aceptación de alto nivel:**
- Ningún usuario puede operar sin iniciar sesión.
- Un usuario sin permiso no puede ejecutar acciones restringidas a su rol.
- Las contraseñas y los PIN se almacenan cifrados.

**Dependencias:** Ninguna (es base para las demás épicas).

---

## EP-02 — Integración con sistema de inventario (versión futura)

> **Fuera de la primera versión.** En la primera entrega el catálogo se administra localmente (EP-03). Esta épica se desarrollará cuando se integre el sistema externo de inventario.

**Descripción:** Conectarse con el sistema externo de inventario para obtener el catálogo de productos y sus precios.

**Objetivo de negocio:** Usar una sola fuente de verdad para productos y precios, sin duplicar su administración.

**Incluye:**
- Conexión con el sistema externo (API u otro mecanismo acordado con su equipo).
- Obtención de los datos de cada producto.
- Sincronización periódica automática y sincronización manual bajo demanda.
- Manejo de errores cuando el sistema externo no responde.
- Registro de cada sincronización.

**No incluye:**
- Crear, editar o eliminar productos en el sistema externo.
- Enviar ventas al sistema de inventario.
- Consultar o validar existencias.

**Criterios de aceptación de alto nivel:**
- Los cambios de precio del sistema externo se reflejan en el POS después de la siguiente sincronización.
- Si la sincronización falla, el POS sigue operando con la última copia válida y avisa del fallo.

**Dependencias:** EP-03, EP-12. Requiere coordinación con el equipo del sistema de inventario.

---

## EP-03 — Catálogo local de productos

**Descripción:** Administrar dentro del POS el catálogo de productos que se venden, con su código de barras, descripción y precio, y buscarlos rápidamente durante la venta.

**Objetivo de negocio:** Tener en un solo lugar los productos y precios vigentes para cobrar correctamente.

**Incluye:**
- Catálogo almacenado en una base de datos local PostgreSQL.
- Registro de productos con código de barras (único por producto), descripción y precio con IVA.
- Edición de la descripción y el precio, solo por el Administrador.
- Activación y desactivación de productos.
- Consulta y búsqueda de productos por código o descripción.
- Carga masiva desde un archivo (Excel o CSV), como opción adicional a la carga directa en la base de datos.
- Exclusión de productos inactivos en la venta.
- Registro de los cambios de precio en la bitácora.

**No incluye:**
- Control de existencias ni de inventario.
- Integración con el sistema externo de inventario (EP-02, versión futura).
- Productos que se venden por peso.
- Varios códigos de barras por producto.
- Categorías, proveedores o costos.

**Criterios de aceptación de alto nivel:**
- La búsqueda por código de barras responde de forma inmediata durante la venta.
- Un cambio de precio se aplica a las ventas nuevas desde el momento en que se guarda.
- Solo el Administrador puede crear productos o cambiar precios.

**Dependencias:** EP-01, EP-11.

---

## EP-04 — Cálculo de IVA

**Descripción:** Calcular el IVA del 12 % de cada venta y mostrarlo desglosado en los comprobantes.

**Objetivo de negocio:** Cumplir con la SAT y entregar facturas con montos de impuesto correctos.

**Incluye:**
- Aplicación del IVA del 12 % a todos los productos (ninguno está exento).
- Cálculo del IVA incluido en el precio de venta (precio con IVA → base imponible + IVA).
- Cálculos por línea con hasta 6 decimales, sin redondeos intermedios.
- Redondeo de los totales finales a 2 decimales, al medio hacia arriba.
- Totales mostrados como Subtotal, IVA y TOTAL.

**No incluye:**
- Otros impuestos (específicos, retenciones, exenciones).
- Configuración de varias tasas de impuesto.
- IVA sobre descuentos, devoluciones o productos por peso (versión futura).

**Criterios de aceptación de alto nivel:**
- Subtotal más IVA coincide exactamente con el TOTAL cobrado.
- Los montos de IVA en el comprobante coinciden con los enviados al certificador FEL.

**Dependencias:** EP-03, EP-12.

---

## EP-05 — Gestión de clientes

**Descripción:** Registrar y consultar los datos de facturación de los clientes.

**Objetivo de negocio:** Facturar rápido con los datos correctos del comprador.

**Incluye:**
- Registro de clientes con NIT o CUI, nombre, dirección y correo opcional.
- Consulta del nombre del contribuyente por NIT y por CUI en el servicio del certificador FEL.
- Búsqueda de clientes por NIT, CUI o nombre.
- Opción de Consumidor Final (CF).
- Validación del límite de Q2,500.00 para facturar como CF.
- Registro y edición de clientes por parte de los cajeros.

**No incluye:**
- Programas de lealtad o puntos.
- Ventas a crédito o cuentas por cobrar.

**Criterios de aceptación de alto nivel:**
- El cajero puede asignar un cliente o CF a la venta en pocos pasos.
- El sistema impide facturar como CF cuando el total es de Q2,500.00 o más.
- No dar un correo nunca interrumpe la venta.

**Dependencias:** EP-08 (servicio de consulta del certificador), EP-12 (límite de CF).

---

## EP-06 — Ventas (POS)

**Descripción:** Pantalla principal de venta, donde el cajero registra los productos y finaliza la venta.

**Objetivo de negocio:** Atender rápido a los clientes y reducir errores al cobrar.

**Incluye:**
- Agregar productos por lector de código de barras, búsqueda o código manual.
- Al escanear un producto que ya está en la venta, sumar la cantidad en la misma línea.
- Modificar cantidades y eliminar líneas antes de cobrar.
- Cálculo en tiempo real de Subtotal, IVA y TOTAL.
- Suspender una venta y retomarla después.
- Cancelar una venta en curso.
- Uso con teclado y atajos para agilizar la operación.

**No incluye:**
- Validación de existencias.
- Venta de productos por peso.
- Descuentos y devoluciones (versión futura).
- Visor para el cliente (versión futura).
- Promociones automáticas (2x1, combos).

**Criterios de aceptación de alto nivel:**
- Una venta típica se puede completar usando solo el lector y el teclado.
- Solo se puede vender si hay una caja abierta.
- Ninguna operación de venta requiere autorización de un supervisor.

**Dependencias:** EP-01, EP-03, EP-04, EP-09.

---

## EP-07 — Pagos

**Descripción:** Registrar el cobro con tarjeta de cada venta.

**Objetivo de negocio:** Dejar registrado cómo se pagó cada venta para poder cuadrar con el banco.

**Incluye:**
- Pago con tarjeta de débito o crédito, procesado en la terminal bancaria externa.
- Registro del tipo de tarjeta, el número de autorización y, opcionalmente, los últimos 4 dígitos.
- Validación de que el total pagado cubra el total de la venta.

**No incluye:**
- Pago dividido entre varias tarjetas (versión futura).
- Pagos en efectivo, transferencias, vales o dólares.
- Cajón de dinero y cálculo de vuelto.
- Integración directa con terminales bancarias.
- Reembolsos.
- Ventas a crédito.

**Criterios de aceptación de alto nivel:**
- No se puede finalizar una venta si el monto pagado es menor al total.
- Cada pago queda asociado a la venta, la caja y el usuario que lo registró.
- El sistema nunca guarda el número completo de la tarjeta.

**Dependencias:** EP-06, EP-09.

---

## EP-08 — Comprobantes y facturación electrónica (FEL)

**Descripción:** Emitir, anular y reimprimir las facturas electrónicas de cada venta mediante un certificador autorizado por la SAT.

**Objetivo de negocio:** Cumplir con la obligación de facturación electrónica en Guatemala y entregar el comprobante al cliente.

**Incluye:**
- Integración con un certificador FEL autorizado por la SAT.
- Emisión de factura electrónica al finalizar cada venta, sin facturas duplicadas.
- Impresión del comprobante en impresora térmica con los datos que exige la SAT y los totales como Subtotal, IVA y TOTAL.
- Envío de la factura por correo cuando el cliente lo proporcionó.
- Anulación de facturas por parte de un Supervisor o Administrador.
- Reimpresión de facturas emitidas.
- **Modo de contingencia:** si no hay conexión con el certificador, emitir comprobantes en contingencia y certificarlos cuando se restablezca la conexión, según la normativa de la SAT.
- Consulta del historial de facturas emitidas.

**No incluye:**
- Notas de crédito por devoluciones (versión futura).
- Otros tipos de documentos FEL (facturas cambiarias, de exportación, etc.).

**Criterios de aceptación de alto nivel:**
- Toda venta finalizada genera una factura certificada o un comprobante en contingencia pendiente de certificar.
- Las facturas anuladas quedan registradas en el certificador y en el POS.
- Los comprobantes en contingencia se certifican automáticamente cuando vuelve la conexión.

**Dependencias:** EP-04, EP-05, EP-06, EP-07, EP-12. Requiere elegir y contratar un certificador FEL.

---

## EP-09 — Control de caja

**Descripción:** Administrar el ciclo de cada caja: apertura del turno, consulta de lo vendido y cierre.

**Objetivo de negocio:** Controlar lo cobrado en cada turno y detectar pagos con tarjeta mal registrados.

**Incluye:**
- Apertura del turno de caja.
- Control de que una caja solo puede tener un turno abierto a la vez.
- Resumen del turno: ventas, totales por tipo de tarjeta y anulaciones.
- Cierre de caja individual, comparando el total registrado con el cierre de lote de la terminal bancaria propia de la caja.
- Justificación de diferencias.
- Comprobante impreso del cierre.
- Cierre ciego opcional.

**No incluye:**
- Fondo inicial, entradas y retiros de efectivo, y arqueo por denominación (no se maneja efectivo).
- Conciliación bancaria.

**Criterios de aceptación de alto nivel:**
- No se pueden registrar ventas en una caja cerrada.
- El cierre muestra el total registrado, el total del cierre de lote y la diferencia.
- Un cierre confirmado no se puede modificar.

**Dependencias:** EP-01, EP-07.

---

## EP-10 — Reportes

**Descripción:** Consultar información de ventas, ingresos, impuestos y cajas para la toma de decisiones.

**Objetivo de negocio:** Tener información confiable y centralizada sobre la operación de ventas.

**Incluye:**
- Ventas por período, por cajero y por caja.
- Productos más vendidos y cantidades vendidas.
- Ingresos por tipo de tarjeta.
- Reporte de IVA cobrado por período.
- Historial de cierres de caja con sus diferencias.
- Facturas anuladas.
- Filtros por rango de fechas y exportación a PDF y Excel.

**No incluye:**
- Reportes de existencias o de inventario (el sistema no controla inventario).
- Reportes de descuentos y devoluciones (versión futura).
- Análisis de costos o márgenes de ganancia.

**Criterios de aceptación de alto nivel:**
- Los totales de los reportes coinciden con las ventas y los cierres registrados.
- Solo los roles autorizados pueden ver los reportes.

**Dependencias:** EP-06, EP-07, EP-08, EP-09.

---

## EP-11 — Auditoría (bitácora)

**Descripción:** Registrar las acciones importantes que realizan los usuarios dentro del sistema.

**Objetivo de negocio:** Dar trazabilidad a las operaciones y facilitar la investigación de errores o irregularidades.

**Incluye:**
- Registro de eventos: inicio y cierre de sesión, cambios de usuarios y permisos, restablecimiento de contraseñas y PIN, líneas eliminadas, ventas canceladas, anulaciones, reimpresiones, apertura y cierre de turnos, cambios de configuración y cambios en el catálogo de productos y sus precios.
- Cada registro con fecha y hora, usuario, acción, caja y detalle.
- Consulta y filtrado de la bitácora por fecha, usuario y tipo de acción.

**No incluye:**
- Edición o eliminación de registros de la bitácora.

**Criterios de aceptación de alto nivel:**
- Toda acción sensible queda registrada automáticamente.
- Ningún usuario, incluido el administrador, puede modificar o borrar registros de la bitácora.

**Dependencias:** EP-01.

---

## EP-12 — Configuración

**Descripción:** Administrar los parámetros generales del sistema.

**Objetivo de negocio:** Adaptar el sistema al negocio sin cambiar el código.

**Incluye:**
- Datos del emisor: razón social, nombre comercial, NIT, dirección y establecimiento registrado en la SAT.
- Datos de conexión con el certificador FEL.
- Registro de las cajas (terminales) y sus impresoras.
- Límite para facturar como Consumidor Final (valor inicial Q2,500.00).
- Configuración de impresoras y formato del comprobante (logo, mensajes).
- Parámetros de caja y venta: cierre ciego y cantidad máxima por línea.

**No incluye:**
- Configuración de tasas de impuesto (el IVA es fijo en 12 %).
- Métodos de pago configurables (por ahora solo tarjeta).
- Conexión con el sistema de inventario, balanzas, límites de descuento, parámetros de bloqueo y autorizaciones de supervisor (versión futura).

**Criterios de aceptación de alto nivel:**
- Solo el rol Administrador puede modificar la configuración.
- Los cambios se aplican sin reinstalar el sistema y quedan registrados en la bitácora.

**Dependencias:** EP-01.

---

## EP-13 — Sucursales (opcional)

**Descripción:** Permitir que el sistema opere en varias tiendas del supermercado.

**Objetivo de negocio:** Centralizar la información de ventas de todas las sucursales.

**Incluye:**
- Registro de sucursales y de sus cajas.
- Asociación de cada sucursal con su establecimiento registrado en la SAT.
- Asignación de usuarios a una o varias sucursales.
- Reportes por sucursal y consolidados.

**No incluye:**
- Precios distintos por sucursal, salvo que se confirme la necesidad (HU-13.12).

**Criterios de aceptación de alto nivel:**
- Cada venta y cada factura quedan asociadas a su sucursal y establecimiento.
- Un usuario solo opera en las sucursales que tiene asignadas.

**Dependencias:** EP-01, EP-09, EP-10, EP-12.

---

## Pendientes por confirmar con el cliente

- Certificador FEL que se va a contratar y su documentación técnica.
- Longitud máxima de la descripción de productos que admite el certificador.
- Plazos vigentes de la SAT para certificar comprobantes en contingencia y para anular facturas.
- Si el certificador envía las facturas por correo o lo debe hacer el POS.
- Si se requiere código QR en el ticket.
- Series de facturación que se usarán por caja o por establecimiento.
- Si el módulo de sucursales entra en la primera versión.
