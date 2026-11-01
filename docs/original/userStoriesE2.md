# Historias de usuario — EP-02: Integración con sistema de inventario (versión futura)

> **Fuera de la primera versión.** En la primera entrega el catálogo de productos se administra localmente en el POS (EP-03). Estas historias se conservan como referencia para la versión en la que se integre el sistema externo de inventario. Al desarrollarlas habrá que definir cómo se migra el catálogo local al sistema externo y si la edición de productos en el POS se desactiva.

## Contexto

Esta épica conecta el POS con el sistema externo de inventario para obtener el catálogo de productos y sus precios. La integración es **de solo lectura**: el POS no crea ni modifica productos, no envía ventas y no consulta existencias.

**Roles involucrados:** Administrador, Supervisor y el propio sistema (procesos automáticos).

**Prioridades:** Futura = fuera de la primera versión. Todas las historias de esta épica tienen esta prioridad.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-02.01 | Conectar con el sistema de inventario | Futura |
| HU-02.02 | Probar la conexión | Futura |
| HU-02.03 | Sincronización inicial del catálogo | Futura |
| HU-02.04 | Sincronización automática periódica | Futura |
| HU-02.05 | Sincronización manual | Futura |
| HU-02.06 | Obtener los datos de cada producto | Futura |
| HU-02.07 | Actualizar precios y estados | Futura |
| HU-02.08 | Validar los datos recibidos | Futura |
| HU-02.09 | Manejar fallos de conexión | Futura |
| HU-02.10 | Avisar fallos de sincronización | Futura |
| HU-02.11 | Consultar historial de sincronizaciones | Futura |

---

## HU-02.01 — Conectar con el sistema de inventario

**Como** administrador,
**quiero** que el POS se conecte con el sistema de inventario usando los datos de conexión configurados,
**para** obtener el catálogo de productos sin administrarlo dos veces.

**Criterios de aceptación:**
1. El POS usa los datos de conexión definidos en la configuración (EP-12): dirección del servicio, credenciales o llave de acceso.
2. La comunicación con el sistema externo se hace por un canal cifrado (por ejemplo, HTTPS).
3. Las credenciales de conexión se almacenan cifradas y no se muestran en pantalla.
4. El POS solo realiza operaciones de lectura sobre el sistema externo.

**Prioridad:** Futura

---

## HU-02.02 — Probar la conexión

**Como** administrador,
**quiero** probar la conexión con el sistema de inventario,
**para** confirmar que los datos de conexión son correctos antes de sincronizar.

**Criterios de aceptación:**
1. **Dado** que estoy en la configuración de la integración, **cuando** selecciono "Probar conexión", **entonces** el sistema intenta conectarse y muestra si fue exitosa.
2. **Dado** que la conexión falla, **entonces** el sistema muestra un mensaje comprensible con la causa (credenciales inválidas, servicio no disponible, tiempo de espera agotado).
3. La prueba no modifica el catálogo local.

**Prioridad:** Futura

---

## HU-02.03 — Sincronización inicial del catálogo

**Como** administrador,
**quiero** descargar el catálogo completo la primera vez que se configura el sistema,
**para** que las cajas puedan empezar a vender.

**Criterios de aceptación:**
1. **Dado** que el catálogo local está vacío, **cuando** se configura la conexión por primera vez, **entonces** el sistema descarga todos los productos del sistema de inventario.
2. El sistema muestra el avance de la descarga y la cantidad de productos obtenidos.
3. **Dado** que la descarga se interrumpe, **entonces** el sistema permite reintentarla sin duplicar productos.
4. No se pueden registrar ventas hasta que exista al menos una sincronización exitosa.

**Prioridad:** Futura

---

## HU-02.04 — Sincronización automática periódica

**Como** supervisor,
**quiero** que el catálogo se actualice automáticamente cada cierto tiempo,
**para** que las cajas siempre usen los precios vigentes sin intervención manual.

**Criterios de aceptación:**
1. El sistema sincroniza el catálogo automáticamente con la frecuencia configurada (EP-12).
2. La sincronización se ejecuta en segundo plano y no interrumpe las ventas en curso.
3. Si el sistema externo lo permite, se descargan solo los productos que cambiaron desde la última sincronización.
4. Cada sincronización queda registrada en el historial (HU-02.11).

**Prioridad:** Futura

---

## HU-02.05 — Sincronización manual

**Como** supervisor,
**quiero** sincronizar el catálogo en el momento que lo necesite,
**para** aplicar de inmediato un cambio de precios sin esperar a la siguiente sincronización automática.

**Criterios de aceptación:**
1. **Dado** que tengo el permiso correspondiente, **cuando** selecciono "Sincronizar ahora", **entonces** el sistema inicia la sincronización.
2. **Dado** que ya hay una sincronización en curso, **entonces** el sistema no inicia otra y me avisa.
3. Al terminar, el sistema muestra el resultado: productos nuevos, actualizados y desactivados.
4. La sincronización manual queda registrada en la bitácora con el usuario que la ejecutó.

**Prioridad:** Futura

---

## HU-02.06 — Obtener los datos de cada producto

**Como** sistema POS,
**quiero** recibir los datos necesarios de cada producto,
**para** poder venderlo y facturarlo correctamente.

**Criterios de aceptación:**
1. Por cada producto se obtienen: código interno, código o códigos de barras, descripción, precio de venta con IVA, unidad de medida y estado (activo o inactivo).
2. El sistema identifica si un producto se vende por unidad o por peso.
3. Un producto puede tener más de un código de barras.
4. La correspondencia entre los campos del sistema externo y los del POS queda documentada.

**Prioridad:** Futura

---

## HU-02.07 — Actualizar precios y estados

**Como** supervisor,
**quiero** que los cambios de precio y de estado del sistema de inventario se reflejen en el POS,
**para** cobrar siempre el precio correcto y no vender productos dados de baja.

**Criterios de aceptación:**
1. **Dado** que un producto cambió de precio en el sistema de inventario, **cuando** se sincroniza, **entonces** el catálogo local muestra el nuevo precio.
2. **Dado** que un producto fue desactivado en el sistema de inventario, **cuando** se sincroniza, **entonces** queda inactivo en el POS y no se puede vender.
3. **Dado** que hay una venta en curso, **cuando** se actualiza un precio, **entonces** los productos que ya estaban en esa venta conservan el precio con el que se agregaron.
4. Los productos no se eliminan del catálogo local, para conservar el historial de ventas.

**Prioridad:** Futura

---

## HU-02.08 — Validar los datos recibidos

**Como** supervisor,
**quiero** que el sistema revise los datos que llegan del sistema de inventario,
**para** evitar cobrar precios incorrectos por errores en la información.

**Criterios de aceptación:**
1. **Dado** que un producto llega sin precio, con precio negativo o en cero, **entonces** el sistema no lo actualiza y lo registra como error.
2. **Dado** que dos productos distintos llegan con el mismo código de barras, **entonces** el sistema registra el conflicto y avisa.
3. **Dado** que un producto llega sin descripción o sin código, **entonces** se rechaza y se registra como error.
4. Los errores de un producto no detienen la sincronización del resto del catálogo.

**Prioridad:** Futura

---

## HU-02.09 — Manejar fallos de conexión

**Como** cajero,
**quiero** seguir vendiendo aunque el sistema de inventario no responda,
**para** no detener la atención a los clientes.

**Criterios de aceptación:**
1. **Dado** que la sincronización falla, **entonces** el POS sigue usando la última copia válida del catálogo local (EP-03).
2. **Dado** que una sincronización falla a la mitad, **entonces** el catálogo local no queda con datos incompletos o mezclados.
3. El sistema reintenta automáticamente la sincronización después de un tiempo configurable.
4. El fallo queda registrado en el historial de sincronizaciones.

**Prioridad:** Futura

---

## HU-02.10 — Avisar fallos de sincronización

**Como** supervisor,
**quiero** recibir un aviso cuando la sincronización falla,
**para** contactar al equipo del sistema de inventario a tiempo.

**Criterios de aceptación:**
1. **Dado** que una sincronización falla, **entonces** el sistema muestra una alerta a los usuarios con rol Supervisor o Administrador.
2. **Dado** que las sincronizaciones fallan de forma consecutiva durante más tiempo del configurado, **entonces** la alerta se vuelve más visible (por ejemplo, un aviso permanente en pantalla).
3. La alerta desaparece cuando hay una sincronización exitosa.
4. Los cajeros no ven alertas técnicas que no pueden resolver.

**Prioridad:** Futura

---

## HU-02.11 — Consultar historial de sincronizaciones

**Como** administrador,
**quiero** ver el historial de las sincronizaciones,
**para** revisar cuándo se actualizó el catálogo y diagnosticar problemas.

**Criterios de aceptación:**
1. Por cada sincronización se muestra: fecha y hora de inicio y fin, tipo (automática o manual), usuario si fue manual, resultado, y cantidad de productos nuevos, actualizados, desactivados y con error.
2. Puedo ver el detalle de los productos con error y su motivo.
3. Puedo filtrar el historial por fecha y por resultado.

**Prioridad:** Futura

