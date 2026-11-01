# Historias de usuario — EP-03: Catálogo local de productos

## Contexto

En la primera versión el POS tiene un **catálogo propio** que se administra desde el mismo sistema. Cada producto tiene:

| Campo | Descripción |
|-------|-------------|
| Código | Código de barras del producto. Cada producto tiene un solo código de barras y cada código pertenece a un solo producto. |
| Descripción | Nombre del artículo tal como aparece en la factura. |
| Precio | Precio de venta en quetzales, con el IVA del 12 % incluido. |

El catálogo se almacena en una **base de datos local PostgreSQL**. El sistema **no controla inventario** y todos los productos se venden **por unidad**: cada artículo tiene un precio individual y no hay productos por peso. La integración con el sistema externo de inventario (EP-02) queda para una versión futura.

**Roles involucrados:** Administrador, Supervisor, Cajero.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-03.01 | Registrar un producto | Alta |
| HU-03.02 | Editar la descripción y el precio de un producto | Alta |
| HU-03.03 | Activar y desactivar un producto | Alta |
| HU-03.04 | Consultar la lista de productos | Alta |
| HU-03.05 | Cargar el catálogo desde un archivo | Baja |
| HU-03.06 | Buscar producto por código | Alta |
| HU-03.07 | Buscar producto por descripción | Alta |
| HU-03.08 | Manejar producto no encontrado | Alta |
| HU-03.09 | Excluir productos inactivos de la venta | Alta |
| HU-03.10 | Consultar precio sin vender | Media |
| HU-03.11 | Consultar el historial de precios | Media |
| HU-03.12 | Exportar el catálogo | Baja |

---

## HU-03.01 — Registrar un producto

**Como** administrador,
**quiero** registrar los productos que vende el supermercado,
**para** que los cajeros puedan venderlos y facturarlos.

**Criterios de aceptación:**
1. Para registrar un producto debo ingresar: código de barras, descripción y precio.
2. **Dado** que el código de barras ya está asignado a otro producto, **entonces** el sistema no permite guardar el producto y muestra un mensaje.
3. Puedo ingresar el código escaneándolo con el lector de código de barras.
4. El precio debe ser mayor que cero y tener como máximo 2 decimales.
5. La descripción es obligatoria y tiene una longitud máxima definida (por ejemplo, 100 caracteres).
6. El producto nuevo queda activo y disponible para la venta de inmediato.
7. El registro queda en la bitácora con el usuario que lo hizo.

**Prioridad:** Alta

---

## HU-03.02 — Editar la descripción y el precio de un producto

**Como** administrador,
**quiero** modificar la descripción y el precio de un producto,
**para** mantener el catálogo actualizado.

**Criterios de aceptación:**
1. Puedo modificar la descripción y el precio.
2. El código no se puede modificar después de creado, para mantener la trazabilidad de las ventas. Si está mal, se desactiva el producto y se registra uno nuevo.
3. **Dado** que cambio el precio, **entonces** el nuevo precio se aplica a los productos que se agreguen a una venta a partir de ese momento.
4. **Dado** que hay ventas en curso con ese producto, **entonces** las líneas ya agregadas conservan el precio anterior (HU-06.09).
5. Los cambios no afectan las facturas ya emitidas.
6. Cada cambio queda registrado en la bitácora con el valor anterior y el nuevo.

**Prioridad:** Alta

---

## HU-03.03 — Activar y desactivar un producto

**Como** administrador,
**quiero** desactivar los productos que ya no se venden y reactivarlos si vuelven,
**para** que no se vendan por error sin perder su historial.

**Criterios de aceptación:**
1. **Dado** que desactivo un producto, **entonces** no se puede agregar a nuevas ventas.
2. Los productos no se eliminan del sistema, para conservar las ventas y facturas en las que aparecen.
3. Puedo reactivar un producto desactivado.
4. Los cambios de estado quedan registrados en la bitácora.

**Prioridad:** Alta

---

## HU-03.04 — Consultar la lista de productos

**Como** supervisor,
**quiero** ver la lista de productos del catálogo,
**para** revisar sus descripciones y precios.

**Criterios de aceptación:**
1. La lista muestra: código, descripción, precio, estado y fecha de la última modificación.
2. Puedo buscar por código o descripción.
3. Puedo filtrar por estado (activo o inactivo).
4. Puedo ordenar por descripción, precio o fecha de modificación.
5. La lista tiene paginación para manejar miles de productos.
6. Solo los roles con permiso pueden crear o editar productos desde esta pantalla; los demás solo consultan.

**Prioridad:** Alta

---

## HU-03.05 — Cargar el catálogo desde un archivo

> La carga inicial del catálogo puede hacerse directamente en la base de datos PostgreSQL. Esta historia facilita cargas posteriores sin acceso técnico a la base de datos.

**Como** administrador,
**quiero** cargar muchos productos a la vez desde un archivo de Excel o CSV,
**para** no registrar uno por uno los miles de productos del supermercado.

**Criterios de aceptación:**
1. El sistema ofrece una plantilla con las columnas: código, descripción y precio.
2. **Dado** que subo el archivo, **entonces** el sistema muestra una vista previa con los productos nuevos, los que se actualizarán y los que tienen errores.
3. **Dado** que un código ya existe, **entonces** se actualizan su descripción y su precio.
4. Las filas con errores (código vacío, precio inválido, códigos repetidos dentro del archivo) no se cargan y se muestran con su motivo.
5. La carga solo se aplica después de que yo la confirme.
6. La carga queda registrada en la bitácora con la cantidad de productos creados y actualizados.

**Prioridad:** Baja

---

## HU-03.06 — Buscar producto por código

**Como** cajero,
**quiero** encontrar un producto escaneando o escribiendo su código,
**para** agregarlo a la venta en el menor tiempo posible.

**Criterios de aceptación:**
1. **Dado** que escaneo un código registrado, **entonces** el sistema encuentra el producto de inmediato (en menos de un segundo).
2. El sistema funciona con lectores de código de barras que se comportan como teclado.
3. **Dado** que el código de barras está dañado, **entonces** puedo escribir sus números manualmente.
4. La búsqueda por código es exacta.

**Prioridad:** Alta

---

## HU-03.07 — Buscar producto por descripción

**Como** cajero,
**quiero** buscar un producto escribiendo parte de su descripción,
**para** encontrarlo cuando no tengo el código.

**Criterios de aceptación:**
1. **Dado** que escribo al menos 3 caracteres, **entonces** el sistema muestra los productos activos cuya descripción contiene ese texto.
2. La búsqueda no distingue mayúsculas, minúsculas ni tildes.
3. Los resultados muestran código, descripción y precio.
4. Puedo moverme por los resultados y seleccionar uno con el teclado.

**Prioridad:** Alta

---

## HU-03.08 — Manejar producto no encontrado

**Como** cajero,
**quiero** que el sistema me avise claramente cuando un código no existe,
**para** no agregar productos por error y saber cómo continuar.

**Criterios de aceptación:**
1. **Dado** que escaneo o escribo un código que no existe en el catálogo, **entonces** el sistema muestra un aviso visible y sonoro.
2. El aviso ofrece buscar el producto por descripción.
3. El sistema no agrega ningún producto a la venta.
4. El código no encontrado queda registrado para que un administrador lo registre en el catálogo.

**Prioridad:** Alta

---

## HU-03.09 — Excluir productos inactivos de la venta

**Como** supervisor,
**quiero** que los productos desactivados no se puedan vender,
**para** respetar las bajas del catálogo.

**Criterios de aceptación:**
1. Los productos inactivos no aparecen en la búsqueda por descripción durante la venta.
2. **Dado** que escaneo un producto inactivo, **entonces** el sistema indica que no está disponible para la venta y no lo agrega.
3. Los productos inactivos se siguen mostrando correctamente en las ventas anteriores y se pueden devolver.

**Prioridad:** Alta

---

## HU-03.10 — Consultar precio sin vender

**Como** cajero,
**quiero** consultar el precio de un producto sin agregarlo a la venta,
**para** responder a un cliente que pregunta cuánto cuesta.

**Criterios de aceptación:**
1. **Dado** que activo la opción "Consultar precio", **cuando** escaneo o busco un producto, **entonces** el sistema muestra su descripción y precio.
2. El producto consultado no se agrega a la venta en curso.
3. Puedo volver a la venta con una sola tecla.

**Prioridad:** Media

---

## HU-03.11 — Consultar el historial de precios

**Como** supervisor,
**quiero** ver los cambios de precio de un producto,
**para** resolver reclamos de clientes sobre el precio que se les cobró.

**Criterios de aceptación:**
1. Desde el detalle del producto puedo ver sus cambios de precio: fecha y hora, precio anterior, precio nuevo y usuario que hizo el cambio.
2. Los cambios se muestran del más reciente al más antiguo.
3. La información proviene de la bitácora (EP-11) y no se puede modificar.

**Prioridad:** Media

---

## HU-03.12 — Exportar el catálogo

**Como** administrador,
**quiero** exportar el catálogo completo a Excel o CSV,
**para** revisarlo fuera del sistema o tenerlo listo para la futura integración con el sistema de inventario.

**Criterios de aceptación:**
1. El archivo incluye código, descripción, precio, estado y fecha de la última modificación.
2. El formato del archivo es compatible con la plantilla de carga (HU-03.05).
3. La exportación queda registrada en la bitácora.

**Prioridad:** Baja

---

## Pendientes por confirmar con el cliente

- Longitud máxima de la descripción que admite el certificador FEL.
