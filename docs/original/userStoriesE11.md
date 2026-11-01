# Historias de usuario — EP-11: Auditoría (bitácora)

## Contexto

Esta épica registra automáticamente las acciones importantes que realizan los usuarios dentro del sistema. La bitácora da trazabilidad a las operaciones y facilita la investigación de errores o irregularidades. Sus registros **no se pueden modificar ni eliminar**.

**Roles involucrados:** Supervisor, Administrador y el propio sistema.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional.

---

## Resumen de historias

| ID       | Historia                                         | Prioridad |
| -------- | ------------------------------------------------ | --------- |
| HU-11.01 | Registrar automáticamente las acciones sensibles | Alta      |
| HU-11.02 | Guardar la información completa de cada evento   | Alta      |
| HU-11.03 | Registrar los cambios con valor anterior y nuevo | Alta      |
| HU-11.04 | Registrar los intentos no autorizados            | Alta      |
| HU-11.05 | Proteger la bitácora contra cambios              | Alta      |
| HU-11.06 | Consultar la bitácora                            | Media     |
| HU-11.07 | Filtrar la bitácora                              | Media     |
| HU-11.08 | Ver el detalle de un evento                      | Media     |
| HU-11.09 | Exportar la bitácora                             | Baja      |
| HU-11.10 | Conservar la bitácora                            | Media     |

---

## HU-11.01 — Registrar automáticamente las acciones sensibles

**Como** administrador,
**quiero** que el sistema registre automáticamente las acciones sensibles,
**para** saber quién hizo cada operación sin depender de que alguien lo anote.

**Criterios de aceptación:**

1. Se registran como mínimo estos eventos:
   - **Sesión:** inicio de sesión exitoso y fallido, y cierre de sesión.
   - **Usuarios y permisos:** creación, edición, activación, desactivación, cambio de rol, cambios de permisos y restablecimiento de contraseñas y PIN.
   - **Ventas:** eliminación de líneas y cancelación de ventas.
   - **Facturación:** anulaciones, reimpresiones y rechazos del certificador.
   - **Pagos:** pagos con tarjeta eliminados antes de finalizar la venta.
   - **Caja:** apertura y cierre de turnos, incluidos los cierres hechos por un supervisor.
   - **Configuración:** cualquier cambio en los parámetros del sistema.
   - **Catálogo:** creación de productos, cambios de descripción y precio, activación, desactivación y cargas desde archivo.
2. El registro se hace en el momento en que ocurre la acción, sin intervención del usuario.
3. **Dado** que la acción falla, **entonces** también se registra el intento con su resultado.

**Prioridad:** Alta

---

## HU-11.02 — Guardar la información completa de cada evento

**Como** supervisor,
**quiero** que cada registro tenga toda la información del evento,
**para** entender exactamente qué pasó.

**Criterios de aceptación:**

1. Cada registro guarda: fecha y hora, usuario, rol, caja o terminal, tipo de acción, módulo, resultado (exitoso o fallido) y descripción.
2. Cuando aplica, guarda también la referencia al documento afectado (venta, factura, cierre, usuario, producto).
3. La fecha y hora se toman del servidor, no del equipo de la caja, para evitar manipulaciones.
4. Los registros nunca guardan contraseñas, PIN ni números completos de tarjeta.

**Prioridad:** Alta

---

## HU-11.03 — Registrar los cambios con valor anterior y nuevo

**Como** administrador,
**quiero** que en los cambios de datos se guarde el valor anterior y el nuevo,
**para** saber exactamente qué se modificó.

**Criterios de aceptación:**

1. **Dado** que se modifica un usuario, un cliente, un producto, un permiso o un parámetro de configuración, **entonces** el registro guarda el valor anterior y el nuevo de cada campo modificado.
2. Los datos sensibles (como credenciales de conexión) se registran como "modificado", sin mostrar su valor.

**Prioridad:** Alta

---

## HU-11.04 — Registrar los intentos no autorizados

**Como** administrador,
**quiero** que se registren los intentos de hacer acciones sin permiso,
**para** detectar posibles abusos o problemas de configuración.

**Criterios de aceptación:**

1. **Dado** que un usuario intenta ejecutar una acción para la que no tiene permiso, **entonces** el intento queda registrado con el usuario y la acción.

**Prioridad:** Alta

---

## HU-11.05 — Proteger la bitácora contra cambios

**Como** administrador,
**quiero** que nadie pueda modificar ni borrar los registros de la bitácora,
**para** que sea una fuente confiable en caso de investigaciones.

**Criterios de aceptación:**

1. El sistema no ofrece ninguna opción para editar ni eliminar registros, para ningún rol, incluido el administrador.
2. La base de datos impide la modificación o eliminación de registros desde la aplicación.
3. El sistema permite verificar que no falten registros ni hayan sido alterados (por ejemplo, con numeración consecutiva o encadenamiento de registros).

**Prioridad:** Alta

---

## HU-11.06 — Consultar la bitácora

**Como** supervisor,
**quiero** consultar los registros de la bitácora,
**para** investigar una operación o un reclamo.

**Criterios de aceptación:**

1. La lista muestra: fecha y hora, usuario, caja, módulo, acción y resultado.
2. Los registros se muestran del más reciente al más antiguo, con paginación.
3. Solo los roles con permiso pueden consultar la bitácora (EP-01).
4. La consulta de la bitácora también queda registrada.

**Prioridad:** Media

---

## HU-11.07 — Filtrar la bitácora

**Como** supervisor,
**quiero** filtrar los registros de la bitácora,
**para** encontrar rápidamente los eventos que me interesan.

**Criterios de aceptación:**

1. Puedo filtrar por rango de fechas, usuario, caja, módulo, tipo de acción y resultado.
2. Puedo buscar por número de documento (venta, factura o cierre).
3. Puedo combinar varios filtros a la vez.

**Prioridad:** Media

---

## HU-11.08 — Ver el detalle de un evento

**Como** supervisor,
**quiero** ver el detalle completo de un evento,
**para** entender el contexto de la acción.

**Criterios de aceptación:**

1. El detalle muestra toda la información guardada, incluidos los valores anterior y nuevo.
2. Desde el detalle puedo abrir el documento relacionado (venta, factura, cierre), según mis permisos.

**Prioridad:** Media

---

## HU-11.09 — Exportar la bitácora

**Como** administrador,
**quiero** exportar los registros de la bitácora,
**para** entregarlos a auditores o conservarlos fuera del sistema.

**Criterios de aceptación:**

1. Puedo exportar los registros filtrados a Excel o a CSV.
2. La exportación queda registrada en la bitácora.
3. Solo el rol Administrador puede exportar la bitácora.

**Prioridad:** Baja

---

## HU-11.10 — Conservar la bitácora

**Como** administrador,
**quiero** que la bitácora se conserve durante el tiempo definido,
**para** cumplir con las políticas del negocio y con los plazos de conservación tributaria.

**Criterios de aceptación:**

1. Los registros se conservan como mínimo durante el plazo acordado con el cliente.
2. Los registros antiguos pueden archivarse sin perder la posibilidad de consultarlos.
3. El sistema nunca elimina registros automáticamente antes del plazo.

**Prioridad:** Media

---

## Pendientes por confirmar con el cliente

- Plazo de conservación de la bitácora.
  Mínimo de 1 año en base de datos activa con opción de archivo histórico anual

- Si los supervisores pueden consultar toda la bitácora o solo la de sus cajas.
  Únicamente a eventos y movimientos ocurridos en sus cajas asignadas.

- Si se necesita exportar la bitácora para auditorías externas.
  Permitida en formato CSV únicamente para el rol Administrador.
