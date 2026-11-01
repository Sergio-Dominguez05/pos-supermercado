# Historias de usuario — EP-01: Seguridad y usuarios

## Contexto

Esta épica controla el acceso al sistema mediante usuarios, roles y permisos, para que cada persona solo pueda hacer lo que le corresponde y quede identificado quién hizo cada operación.

**Roles del sistema:**

| Rol | Descripción | Acceso |
|-----|-------------|--------|
| Administrador | Configura el sistema y administra usuarios, roles, permisos y el catálogo de productos. | Usuario y contraseña |
| Supervisor | Supervisa la operación de las cajas, anula facturas y consulta reportes. | Usuario y contraseña |
| Cajero | Registra ventas y cobros en la caja asignada. | Usuario y PIN corto |

**Decisiones de alcance:**
- En la primera versión ninguna operación requiere autorización de un supervisor una vez iniciada la sesión. Cada usuario hace solo lo que permite su rol.
- Solo el Administrador puede restablecer contraseñas y PIN.
- No hay cierre de sesión automático por inactividad.
- El bloqueo por intentos fallidos queda para una versión futura.
- Los ID de historias eliminadas no se reutilizan.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-01.01 | Iniciar sesión con contraseña | Alta |
| HU-01.02 | Iniciar sesión con PIN (cajeros) | Alta |
| HU-01.03 | *(Eliminada: cierre de sesión automático por inactividad)* | — |
| HU-01.04 | Bloqueo por intentos fallidos | Futura |
| HU-01.05 | Crear usuario | Alta |
| HU-01.06 | Consultar usuarios | Alta |
| HU-01.07 | Editar usuario | Alta |
| HU-01.08 | Activar y desactivar usuario | Alta |
| HU-01.09 | Asignar rol a un usuario | Alta |
| HU-01.10 | Configurar permisos por rol | Media |
| HU-01.11 | Restringir acciones según permisos | Alta |
| HU-01.12 | Autorización de supervisor | Futura |
| HU-01.13 | Cambiar contraseña o PIN propio | Alta |
| HU-01.14 | Restablecer contraseña o PIN de un usuario | Alta |
| HU-01.15 | Cambio obligatorio de contraseña o PIN temporal | Media |
| HU-01.16 | Cerrar sesión | Alta |

---

## HU-01.01 — Iniciar sesión con contraseña

**Como** administrador o supervisor,
**quiero** iniciar sesión con mi nombre de usuario y contraseña,
**para** acceder a las funciones que me corresponden según mi rol.

**Criterios de aceptación:**
1. **Dado** que tengo un usuario activo con rol Administrador o Supervisor, **cuando** ingreso mi usuario y contraseña correctos, **entonces** el sistema me da acceso y muestra las opciones de mi rol.
2. **Dado** que ingreso un usuario o contraseña incorrectos, **cuando** intento iniciar sesión, **entonces** el sistema muestra un mensaje genérico ("Usuario o contraseña incorrectos") sin indicar cuál de los dos falló.
3. **Dado** que mi usuario está desactivado, **cuando** intento iniciar sesión, **entonces** el sistema me niega el acceso.
4. La contraseña no se muestra en pantalla mientras se escribe.
5. Cada inicio de sesión, exitoso o fallido, queda registrado en la bitácora (EP-11).

**Prioridad:** Alta

---

## HU-01.02 — Iniciar sesión con PIN (cajeros)

**Como** cajero,
**quiero** iniciar sesión con mi usuario y un PIN corto,
**para** empezar a trabajar rápido en el cambio de turno.

**Criterios de aceptación:**
1. **Dado** que tengo un usuario activo con rol Cajero, **cuando** ingreso mi usuario y mi PIN correctos, **entonces** el sistema me da acceso a la pantalla de venta.
2. El PIN es numérico, de 4 a 6 dígitos.
3. El PIN se puede ingresar con el teclado numérico y no se muestra en pantalla mientras se escribe.
4. **Dado** que el usuario o el PIN son incorrectos, **entonces** el sistema muestra un mensaje genérico ("Usuario o PIN incorrectos").
5. **Dado** que mi usuario está desactivado, **entonces** el sistema me niega el acceso.
6. Los PIN se almacenan cifrados con un algoritmo de hash seguro, igual que las contraseñas.
7. Cada inicio de sesión, exitoso o fallido, queda registrado en la bitácora.

**Prioridad:** Alta

---

## HU-01.04 — Bloqueo por intentos fallidos

> **Fuera de la primera versión.**

**Como** administrador,
**quiero** que un usuario se bloquee después de varios intentos fallidos de inicio de sesión,
**para** proteger las cuentas contra intentos de adivinar contraseñas o PIN.

**Criterios de aceptación:**
1. **Dado** que un usuario falla el inicio de sesión el número de veces configurado de forma consecutiva, **entonces** el sistema bloquea ese usuario.
2. **Dado** que un usuario está bloqueado, **cuando** intenta iniciar sesión, **entonces** el sistema le indica que debe contactar a un administrador.
3. Solo un administrador puede desbloquear al usuario.
4. Un inicio de sesión exitoso reinicia el contador de intentos fallidos.
5. Los bloqueos y desbloqueos quedan registrados en la bitácora.

**Prioridad:** Futura

---

## HU-01.05 — Crear usuario

**Como** administrador,
**quiero** crear usuarios nuevos,
**para** que el personal del supermercado pueda usar el sistema.

**Criterios de aceptación:**
1. **Dado** que estoy en la gestión de usuarios, **cuando** creo un usuario, **entonces** debo ingresar: nombre completo, nombre de usuario y rol.
2. **Dado** que el rol es Administrador o Supervisor, **entonces** asigno una contraseña temporal de al menos 8 caracteres, con letras y números.
3. **Dado** que el rol es Cajero, **entonces** asigno un PIN temporal numérico de 4 a 6 dígitos.
4. **Dado** que el nombre de usuario ya existe, **cuando** intento guardarlo, **entonces** el sistema no lo permite y muestra un mensaje.
5. Las contraseñas y los PIN se almacenan cifrados con un algoritmo de hash seguro y nunca en texto plano.
6. El usuario nuevo queda activo por defecto.
7. La creación queda registrada en la bitácora, con el usuario que la realizó.

**Prioridad:** Alta

---

## HU-01.06 — Consultar usuarios

**Como** administrador,
**quiero** ver la lista de usuarios del sistema,
**para** saber quién tiene acceso y con qué rol.

**Criterios de aceptación:**
1. La lista muestra: nombre completo, nombre de usuario, rol, estado (activo o inactivo) y fecha del último acceso.
2. Puedo buscar usuarios por nombre o nombre de usuario.
3. Puedo filtrar por rol y por estado.
4. La lista nunca muestra contraseñas ni PIN.

**Prioridad:** Alta

---

## HU-01.07 — Editar usuario

**Como** administrador,
**quiero** modificar los datos de un usuario,
**para** mantener su información actualizada.

**Criterios de aceptación:**
1. **Dado** que selecciono un usuario, **cuando** lo edito, **entonces** puedo cambiar su nombre completo y su rol.
2. El nombre de usuario no se puede modificar después de creado, para mantener la trazabilidad en la bitácora.
3. **Dado** que cambio el rol de Cajero a Administrador o Supervisor, o al revés, **entonces** el sistema me pide asignar una contraseña o un PIN temporal, según el nuevo rol.
4. **Dado** que modifico el rol de un usuario con sesión activa, **entonces** los nuevos permisos se aplican a partir de su siguiente inicio de sesión.
5. Cada cambio queda registrado en la bitácora, con el valor anterior y el nuevo.

**Prioridad:** Alta

---

## HU-01.08 — Activar y desactivar usuario

**Como** administrador,
**quiero** desactivar a los usuarios que ya no trabajan en el supermercado y reactivarlos si es necesario,
**para** quitarles el acceso sin perder el historial de sus operaciones.

**Criterios de aceptación:**
1. **Dado** que desactivo un usuario, **entonces** este no puede iniciar sesión.
2. Los usuarios no se eliminan del sistema. Sus ventas, cierres y registros de bitácora se conservan.
3. **Dado** que el usuario tiene una caja abierta, **cuando** intento desactivarlo, **entonces** el sistema me avisa que primero se debe cerrar la caja.
4. No puedo desactivar mi propio usuario.
5. El sistema no permite desactivar al último administrador activo.
6. La activación y la desactivación quedan registradas en la bitácora.

**Prioridad:** Alta

---

## HU-01.09 — Asignar rol a un usuario

**Como** administrador,
**quiero** asignar un rol a cada usuario,
**para** que tenga solo los permisos que necesita para su trabajo.

**Criterios de aceptación:**
1. Cada usuario tiene exactamente un rol: Administrador, Supervisor o Cajero.
2. **Dado** que asigno un rol, **entonces** el usuario obtiene los permisos definidos para ese rol.
3. No puedo quitarme el rol de Administrador a mí mismo si soy el último administrador activo.
4. El cambio de rol queda registrado en la bitácora.

**Prioridad:** Alta

---

## HU-01.10 — Configurar permisos por rol

**Como** administrador,
**quiero** definir qué acciones puede realizar cada rol,
**para** ajustar los accesos a la forma de trabajar del supermercado.

**Criterios de aceptación:**
1. El sistema muestra una lista de permisos agrupados por módulo (catálogo de productos, ventas, pagos, facturación, caja, reportes, configuración, usuarios).
2. Puedo activar o desactivar cada permiso para los roles Supervisor y Cajero.
3. Los permisos del rol Administrador no se pueden reducir, para evitar que el sistema quede sin nadie que lo administre.
4. Crear productos, cambiar precios y restablecer contraseñas o PIN son permisos exclusivos del Administrador y no se pueden asignar a otros roles.
5. El sistema incluye una configuración inicial de permisos (ver la matriz de permisos al final de este documento).
6. Los cambios de permisos quedan registrados en la bitácora.

**Prioridad:** Media

---

## HU-01.11 — Restringir acciones según permisos

**Como** administrador,
**quiero** que el sistema impida a cada usuario hacer acciones que su rol no permite,
**para** proteger las operaciones sensibles.

**Criterios de aceptación:**
1. **Dado** que un usuario no tiene permiso para una acción, **entonces** la opción no aparece en su menú o aparece deshabilitada.
2. **Dado** que un usuario intenta ejecutar una acción sin permiso por cualquier otro medio, **entonces** el sistema la rechaza y la registra en la bitácora.
3. Los permisos se validan en el servidor y no solo en la interfaz.

**Prioridad:** Alta

---

## HU-01.12 — Autorización de supervisor

> **Fuera de la primera versión.** Por ahora ninguna operación requiere autorización de un supervisor una vez iniciada la sesión.

**Como** cajero,
**quiero** que un supervisor pueda autorizar en mi caja una operación que yo no tengo permitida,
**para** resolver la situación del cliente sin cerrar mi sesión.

**Criterios de aceptación:**
1. **Dado** que intento una operación restringida, **entonces** el sistema me pide la autorización de un supervisor.
2. **Dado** que el sistema pide autorización, **cuando** un supervisor o administrador ingresa su usuario y contraseña, **entonces** la operación se ejecuta en mi sesión.
3. La autorización solo aplica a esa operación y no le da permisos permanentes al cajero.
4. **Dado** que las credenciales son incorrectas o el usuario no tiene el permiso, **entonces** la operación se rechaza.
5. La bitácora registra al cajero que solicitó, al supervisor que autorizó, la operación y la fecha y hora.

**Prioridad:** Futura

---

## HU-01.13 — Cambiar contraseña o PIN propio

**Como** usuario del sistema,
**quiero** cambiar mi contraseña (o mi PIN, si soy cajero),
**para** mantener segura mi cuenta.

**Criterios de aceptación:**
1. Para cambiarla debo ingresar mi contraseña o PIN actual, el nuevo y su confirmación.
2. La nueva contraseña o PIN debe cumplir las reglas de su tipo (HU-01.05) y ser distinto del actual.
3. **Dado** que el valor actual es incorrecto o la confirmación no coincide, **entonces** el sistema no hace el cambio y muestra un mensaje.
4. El cambio queda registrado en la bitácora, sin guardar la contraseña ni el PIN.

**Prioridad:** Alta

---

## HU-01.14 — Restablecer contraseña o PIN de un usuario

**Como** administrador,
**quiero** restablecer la contraseña o el PIN de un usuario que lo olvidó,
**para** que pueda volver a acceder al sistema.

**Criterios de aceptación:**
1. **Dado** que selecciono un usuario, **cuando** restablezco su acceso, **entonces** asigno una contraseña temporal o un PIN temporal, según su rol.
2. Solo el rol Administrador puede restablecer contraseñas y PIN.
3. El administrador nunca puede ver la contraseña ni el PIN actual del usuario.
4. El restablecimiento queda registrado en la bitácora.

**Prioridad:** Alta

---

## HU-01.15 — Cambio obligatorio de contraseña o PIN temporal

**Como** administrador,
**quiero** que los usuarios cambien su contraseña o PIN temporal en su primer inicio de sesión,
**para** que solo ellos conozcan su valor definitivo.

**Criterios de aceptación:**
1. **Dado** que un usuario inicia sesión con una contraseña o PIN temporal (usuario nuevo o acceso restablecido), **entonces** el sistema le exige cambiarlo antes de continuar.
2. El usuario no puede usar ninguna función del sistema hasta completar el cambio.
3. El nuevo valor debe cumplir las reglas de su tipo y ser distinto del temporal.

**Prioridad:** Media

---

## HU-01.16 — Cerrar sesión

**Como** usuario del sistema,
**quiero** cerrar mi sesión,
**para** que nadie más pueda operar con mi usuario.

**Criterios de aceptación:**
1. **Dado** que tengo una sesión iniciada, **cuando** selecciono "Cerrar sesión", **entonces** el sistema termina mi sesión y regresa a la pantalla de inicio de sesión.
2. **Dado** que tengo una venta en curso, **cuando** intento cerrar sesión, **entonces** el sistema me avisa y me pide finalizarla, suspenderla o cancelarla antes de salir.
3. Cerrar sesión no cierra la caja. El cierre de caja es un proceso aparte (EP-09).
4. El cierre de sesión queda registrado en la bitácora.

**Prioridad:** Alta

---

## Matriz de permisos (configuración inicial)

| Permiso | Administrador | Supervisor | Cajero |
|---------|:-------------:|:----------:|:------:|
| Registrar ventas | ✔ | ✔ | ✔ |
| Suspender y cancelar ventas en curso | ✔ | ✔ | ✔ |
| Registrar pagos con tarjeta | ✔ | ✔ | ✔ |
| Registrar y editar clientes | ✔ | ✔ | ✔ |
| Reimprimir facturas | ✔ | ✔ | ✔ |
| Anular facturas | ✔ | ✔ | — |
| Abrir y cerrar su propia caja | ✔ | ✔ | ✔ |
| Cerrar la caja de otro usuario | ✔ | ✔ | — |
| Ver cierres de caja de otros usuarios | ✔ | ✔ | — |
| Consultar reportes | ✔ | ✔ | — |
| Consultar bitácora | ✔ | ✔ | — |
| Consultar el catálogo de productos | ✔ | ✔ | ✔ |
| Crear productos y cambiar precios | ✔ | — | — |
| Cargar el catálogo desde un archivo | ✔ | — | — |
| Administrar usuarios | ✔ | — | — |
| Restablecer contraseñas y PIN | ✔ | — | — |
| Configurar permisos por rol | ✔ | — | — |
| Modificar la configuración del sistema | ✔ | — | — |
