# Historias de usuario — EP-05: Gestión de clientes

## Contexto

Esta épica registra y consulta los datos de facturación de los clientes. En Guatemala la factura puede emitirse a un **NIT**, a un **CUI** (cuando el comprador no tiene NIT) o a **Consumidor Final (CF)**. Desde un total de **Q2,500.00** la SAT exige identificar al comprador con NIT o CUI.

El certificador FEL ofrece la consulta del nombre por NIT y por CUI. El correo del cliente es opcional y nunca interrumpe la venta. Los cajeros pueden registrar y editar los datos de los clientes.

**Roles involucrados:** Cajero, Supervisor, Administrador.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional.

---

## Resumen de historias

| ID | Historia | Prioridad |
|----|----------|-----------|
| HU-05.01 | Facturar como Consumidor Final | Alta |
| HU-05.02 | Buscar cliente por NIT | Alta |
| HU-05.03 | Validar el formato del NIT | Alta |
| HU-05.04 | Consultar el nombre del contribuyente por NIT | Alta |
| HU-05.05 | Registrar un cliente nuevo | Alta |
| HU-05.06 | Facturar con CUI | Alta |
| HU-05.07 | Validar el límite para Consumidor Final | Alta |
| HU-05.08 | Cambiar el cliente de una venta antes de cobrar | Alta |
| HU-05.09 | Editar los datos de un cliente | Alta |
| HU-05.10 | Consultar la lista de clientes | Media |
| HU-05.11 | Registrar correo para envío de factura | Media |

---

## HU-05.01 — Facturar como Consumidor Final

**Como** cajero,
**quiero** facturar a Consumidor Final con una sola acción,
**para** atender rápido a los clientes que no piden factura con NIT.

**Criterios de aceptación:**
1. Cada venta nueva tiene como cliente predeterminado "Consumidor Final" (NIT "CF").
2. Puedo confirmar CF con una sola tecla o un solo clic.
3. La factura muestra "CF" como NIT y "Consumidor Final" como nombre.

**Prioridad:** Alta

---

## HU-05.02 — Buscar cliente por NIT

**Como** cajero,
**quiero** escribir el NIT del cliente y que el sistema cargue sus datos,
**para** no volver a escribirlos en cada compra.

**Criterios de aceptación:**
1. **Dado** que escribo un NIT registrado, **entonces** el sistema carga el nombre y la dirección del cliente.
2. **Dado** que el NIT no está registrado, **entonces** el sistema lo consulta automáticamente (HU-05.04) o me permite registrarlo (HU-05.05).
3. La búsqueda acepta el NIT con o sin guion.

**Prioridad:** Alta

---

## HU-05.03 — Validar el formato del NIT

**Como** cajero,
**quiero** que el sistema revise si el NIT está bien escrito,
**para** evitar que el certificador rechace la factura.

**Criterios de aceptación:**
1. El sistema valida el dígito verificador del NIT según el algoritmo de la SAT (módulo 11, donde el último carácter puede ser "K").
2. **Dado** que el NIT no es válido, **entonces** el sistema muestra un aviso y no permite usarlo.
3. El sistema guarda el NIT en un formato uniforme (sin guiones ni espacios, en mayúsculas).

**Prioridad:** Alta

---

## HU-05.04 — Consultar el nombre del contribuyente por NIT

**Como** cajero,
**quiero** que el sistema obtenga el nombre del contribuyente a partir del NIT,
**para** facturar con el nombre correcto sin preguntarle al cliente cómo se escribe.

**Criterios de aceptación:**
1. **Dado** que ingreso un NIT válido que no está registrado, **entonces** el sistema consulta el nombre en el servicio del certificador FEL.
2. **Dado** que la consulta es exitosa, **entonces** el sistema muestra el nombre y guarda al cliente para futuras compras.
3. **Dado** que el servicio no responde o el NIT no existe, **entonces** el sistema me avisa y me permite escribir el nombre manualmente o facturar como CF.
4. La consulta no debe tardar tanto como para detener la venta; si supera un tiempo límite, se trata como no disponible.

**Prioridad:** Alta

---

## HU-05.05 — Registrar un cliente nuevo

**Como** cajero,
**quiero** registrar un cliente nuevo durante la venta,
**para** emitirle la factura con sus datos.

**Criterios de aceptación:**
1. Los datos obligatorios son NIT (o CUI) y nombre. La dirección y el correo son opcionales; si no se ingresa la dirección se usa "Ciudad".
2. **Dado** que el NIT ya está registrado, **entonces** el sistema no crea un duplicado y carga el cliente existente.
3. Puedo registrar al cliente sin salir de la pantalla de venta.
4. El registro queda en la bitácora con el usuario que lo hizo.

**Prioridad:** Alta

---

## HU-05.06 — Facturar con CUI

**Como** cajero,
**quiero** facturar con el CUI del comprador cuando no tiene NIT,
**para** cumplir con la SAT en compras que superan el límite de Consumidor Final.

**Criterios de aceptación:**
1. Puedo elegir el tipo de identificación: NIT o CUI.
2. El sistema valida que el CUI tenga 13 dígitos numéricos.
3. El sistema consulta el nombre por CUI en el servicio del certificador FEL. **Dado** que la consulta falla, **entonces** puedo escribir el nombre manualmente.
4. La factura se emite con el CUI en el formato que exige el certificador.

**Prioridad:** Alta

---

## HU-05.07 — Validar el límite para Consumidor Final

**Como** administrador,
**quiero** que el sistema no permita facturar como CF cuando el total supera el límite de la SAT,
**para** evitar facturas que incumplen la normativa.

**Criterios de aceptación:**
1. **Dado** que el total de la venta es igual o mayor a Q2,500.00 (límite configurable en EP-12), **cuando** el cliente es CF, **entonces** el sistema exige un NIT o un CUI antes de cobrar.
2. El aviso explica claramente por qué se piden los datos.
3. **Dado** que se cambia el total de la venta por debajo del límite, **entonces** se puede volver a facturar como CF.

**Prioridad:** Alta

---

## HU-05.08 — Cambiar el cliente de una venta antes de cobrar

**Como** cajero,
**quiero** cambiar el cliente asignado a una venta antes de cobrar,
**para** corregir un error sin cancelar la venta.

**Criterios de aceptación:**
1. Puedo cambiar el cliente en cualquier momento antes de finalizar el cobro.
2. Una vez emitida la factura, el cliente ya no se puede cambiar. Para corregirlo hay que anular la factura (EP-08).

**Prioridad:** Alta

---

## HU-05.09 — Editar los datos de un cliente

**Como** cajero,
**quiero** corregir los datos de un cliente registrado,
**para** que las próximas facturas salgan con la información correcta.

**Criterios de aceptación:**
1. Puedo modificar el nombre, la dirección y el correo del cliente, incluso durante la venta.
2. El NIT o CUI no se puede modificar; si está mal, se registra un cliente nuevo.
3. Los cambios no alteran las facturas ya emitidas.
4. Los cambios quedan registrados en la bitácora, con el valor anterior y el nuevo.

**Prioridad:** Alta

---

## HU-05.10 — Consultar la lista de clientes

**Como** supervisor,
**quiero** consultar los clientes registrados,
**para** revisar o corregir su información.

**Criterios de aceptación:**
1. La lista muestra NIT o CUI, nombre, dirección, correo y fecha de la última compra.
2. Puedo buscar por NIT, CUI o nombre.
3. La lista tiene paginación para manejar muchos clientes.

**Prioridad:** Media

---

## HU-05.11 — Registrar correo para envío de factura

**Como** cajero,
**quiero** registrar el correo del cliente,
**para** que reciba su factura electrónica sin necesidad del ticket impreso.

**Criterios de aceptación:**
1. Puedo agregar o cambiar el correo del cliente durante la venta.
2. El correo es opcional: **dado** que el cliente no lo proporciona, **entonces** la venta continúa sin pedirlo de nuevo ni mostrar avisos.
3. **Dado** que se ingresa un correo, **entonces** el sistema valida su formato.
4. **Dado** que el cliente tiene correo registrado, **entonces** el sistema ofrece enviarle la factura (EP-08).

**Prioridad:** Media

