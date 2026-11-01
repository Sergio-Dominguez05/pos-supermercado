# Historias de usuario — EP-08: Comprobantes y facturación electrónica (FEL)

## Contexto

Esta épica emite, anula y reimprime las facturas electrónicas de cada venta bajo el régimen **FEL (Factura Electrónica en Línea)** de la SAT de Guatemala. Las facturas se certifican a través de un **certificador autorizado** por la SAT. Incluye un modo de contingencia para seguir facturando cuando no hay conexión con el certificador.

**Roles involucrados:** Cajero, Supervisor, Administrador y el propio sistema.

**Prioridades:** Alta = indispensable para la primera versión · Media = importante, puede llegar después del lanzamiento · Baja = opcional · Futura = fuera de la primera versión.

---

## Resumen de historias

| ID       | Historia                                    | Prioridad |
| -------- | ------------------------------------------- | --------- |
| HU-08.01 | Conectar con el certificador FEL            | Alta      |
| HU-08.02 | Generar el documento de la factura          | Alta      |
| HU-08.03 | Certificar la factura al finalizar la venta | Alta      |
| HU-08.04 | Evitar facturas duplicadas                  | Alta      |
| HU-08.05 | Imprimir la factura                         | Alta      |
| HU-08.06 | Manejar rechazos del certificador           | Alta      |
| HU-08.07 | Emitir comprobantes en contingencia         | Alta      |
| HU-08.08 | Certificar los comprobantes en contingencia | Alta      |
| HU-08.09 | Avisar contingencias pendientes             | Alta      |
| HU-08.10 | Anular una factura                          | Alta      |
| HU-08.11 | Emitir nota de crédito por devolución       | Futura    |
| HU-08.12 | Reimprimir una factura                      | Alta      |
| HU-08.13 | Consultar el historial de facturas          | Media     |
| HU-08.14 | Consultar el detalle de una factura         | Media     |
| HU-08.15 | Enviar la factura por correo                | Media     |

---

## HU-08.01 — Conectar con el certificador FEL

**Como** administrador,
**quiero** que el POS se conecte con el certificador FEL usando los datos configurados,
**para** certificar las facturas ante la SAT.

**Criterios de aceptación:**

1. El POS usa las credenciales y los datos del emisor definidos en la configuración (EP-12).
2. La comunicación con el certificador se hace por un canal cifrado.
3. El sistema permite probar la conexión sin emitir facturas reales (ambiente de pruebas del certificador).
4. Las credenciales se almacenan cifradas y no se muestran en pantalla.

**Prioridad:** Alta

---

## HU-08.02 — Generar el documento de la factura

**Como** sistema POS,
**quiero** generar el documento de cada factura con el formato que exige la SAT,
**para** que el certificador lo acepte.

**Criterios de aceptación:**

1. El documento incluye: datos del emisor (NIT, nombre, establecimiento), datos del receptor (NIT, CUI o CF, y nombre), fecha de emisión, moneda (GTQ), líneas con cantidad, descripción, precio, base imponible e IVA (hasta 6 decimales), y los totales (2 decimales).
2. El documento se genera con el formato y la versión vigentes del esquema FEL que pide el certificador.
3. Los montos son exactamente los calculados por el POS (HU-04.09).
4. El tipo de documento es Factura (FACT).

**Prioridad:** Alta

---

## HU-08.03 — Certificar la factura al finalizar la venta

**Como** cajero,
**quiero** que la factura se certifique automáticamente al terminar el cobro,
**para** entregarle al cliente su factura válida sin pasos adicionales.

**Criterios de aceptación:**

1. **Dado** que se finaliza el cobro (HU-07.08), **entonces** el sistema envía la factura al certificador.
2. **Dado** que el certificador la acepta, **entonces** el sistema guarda el número de autorización (UUID), la serie, el número y la fecha de certificación.
3. La certificación no debe demorar la atención; si supera el tiempo límite configurado, se pasa a contingencia (HU-08.07).
4. Cada factura queda asociada a su venta, caja, turno y usuario.

**Prioridad:** Alta

---

## HU-08.04 — Evitar facturas duplicadas

**Como** administrador,
**quiero** que una venta nunca genere dos facturas certificadas,
**para** no declarar ventas de más ante la SAT.

**Criterios de aceptación:**

1. Cada venta envía al certificador un identificador único, de modo que un reintento no genere una segunda factura.
2. **Dado** que la respuesta del certificador se pierde, **cuando** se reintenta, **entonces** el sistema consulta si la factura ya fue certificada antes de enviarla de nuevo.
3. El sistema no permite volver a facturar una venta que ya tiene factura certificada.

**Prioridad:** Alta

---

## HU-08.05 — Imprimir la factura

**Como** cajero,
**quiero** que la factura se imprima automáticamente en la impresora térmica,
**para** entregársela al cliente.

**Criterios de aceptación:**

1. El ticket incluye los datos que exige la SAT para la representación impresa: datos del emisor, del receptor, número de autorización, serie, número, fecha de certificación, detalle de productos, totales y datos del certificador.
2. La sección de totales se muestra en este orden: **Subtotal**, **IVA** y **TOTAL** (HU-04.04).
3. El ticket incluye también el pago con tarjeta: tipo de tarjeta, últimos 4 dígitos (si se registraron) y número de autorización.
4. El ticket puede incluir el logo y los mensajes configurados (EP-12).
5. El ticket incluye un código QR si el certificador o la SAT lo requieren.
6. **Dado** que la impresora falla, **entonces** la factura sigue siendo válida y se puede reimprimir (HU-08.12).

**Prioridad:** Alta

---

## HU-08.06 — Manejar rechazos del certificador

**Como** cajero,
**quiero** que el sistema me diga claramente por qué el certificador rechazó una factura,
**para** corregir el problema y terminar la venta.

**Criterios de aceptación:**

1. **Dado** que el certificador rechaza la factura, **entonces** el sistema muestra el motivo en lenguaje comprensible (por ejemplo, "NIT del receptor no válido").
2. **Dado** que el error es corregible en caja (por ejemplo, el NIT del cliente), **entonces** puedo corregirlo y reenviar la factura sin volver a cobrar.
3. **Dado** que el error no es corregible en caja, **entonces** la venta queda pendiente de facturar y se avisa a un supervisor.
4. Todos los rechazos quedan registrados con la respuesta completa del certificador.

**Prioridad:** Alta

---

## HU-08.07 — Emitir comprobantes en contingencia

**Como** cajero,
**quiero** seguir entregando comprobantes cuando no hay conexión con el certificador,
**para** no detener las ventas.

**Criterios de aceptación:**

1. **Dado** que el certificador no responde o no hay internet, **entonces** el sistema emite un comprobante en contingencia según la normativa de la SAT (con su número de acceso de contingencia).
2. El ticket de contingencia indica claramente que la factura está pendiente de certificar.
3. El cajero ve un aviso de que el sistema está operando en contingencia.
4. Los comprobantes en contingencia quedan guardados para su certificación posterior.

**Prioridad:** Alta

---

## HU-08.08 — Certificar los comprobantes en contingencia

**Como** supervisor,
**quiero** que los comprobantes emitidos en contingencia se certifiquen automáticamente cuando vuelva la conexión,
**para** cumplir con el plazo que da la SAT.

**Criterios de aceptación:**

1. **Dado** que se restablece la conexión, **entonces** el sistema envía automáticamente los comprobantes pendientes, en orden de emisión.
2. **Dado** que un comprobante se certifica, **entonces** se guarda su número de autorización y se vincula a la venta.
3. **Dado** que uno falla, **entonces** se registra el error y se sigue con los demás.
4. Puedo lanzar la certificación de pendientes de forma manual.

**Prioridad:** Alta

---

## HU-08.09 — Avisar contingencias pendientes

**Como** supervisor,
**quiero** ver cuántos comprobantes siguen pendientes de certificar,
**para** actuar antes de que venza el plazo de la SAT.

**Criterios de aceptación:**

1. El sistema muestra a supervisores y administradores la cantidad de comprobantes pendientes y el más antiguo.
2. **Dado** que un comprobante se acerca al plazo máximo de certificación, **entonces** el sistema muestra una alerta destacada.
3. Puedo ver la lista de pendientes con el motivo del último intento fallido.

**Prioridad:** Alta

---

## HU-08.10 — Anular una factura

**Como** supervisor,
**quiero** anular una factura emitida por error,
**para** que no se considere una venta válida ante la SAT.

**Criterios de aceptación:**

1. Busco la factura por número, serie, número de autorización o fecha.
2. Debo indicar el motivo de la anulación.
3. La anulación se envía al certificador y queda registrada con su respuesta.
4. **Dado** que la anulación es aceptada, **entonces** la factura queda como "Anulada" y los montos se revierten en los reportes y en la caja correspondiente.
5. Solo los roles Supervisor y Administrador pueden anular facturas, desde su propia sesión. La anulación queda registrada en la bitácora.
6. El sistema respeta el plazo máximo de anulación que establezca la SAT.

**Prioridad:** Alta

---

## HU-08.11 — Emitir nota de crédito por devolución

> **Fuera de la primera versión.** No se manejan devoluciones.

**Como** supervisor,
**quiero** que cada devolución genere una nota de crédito electrónica,
**para** documentar ante la SAT la reducción de la venta.

**Criterios de aceptación:**

1. **Dado** que se registra una devolución (HU-06.16), **entonces** el sistema genera una nota de crédito (NCRE) que hace referencia a la factura original.
2. La nota de crédito incluye solo las líneas y cantidades devueltas, con los montos de la venta original (HU-04.08).
3. La nota de crédito se certifica, se imprime y se puede reimprimir igual que una factura.
4. Si no hay conexión, se aplica el mismo esquema de contingencia.

**Prioridad:** Futura

---

## HU-08.12 — Reimprimir una factura

**Como** cajero,
**quiero** reimprimir una factura ya emitida,
**para** entregar una copia si la impresión falló o el cliente la pide.

**Criterios de aceptación:**

1. Puedo reimprimir la última factura con un atajo de teclado.
2. Puedo buscar y reimprimir facturas anteriores según mis permisos.
3. La reimpresión muestra la leyenda "Copia" o "Reimpresión".
4. Cada reimpresión queda registrada en la bitácora.

**Prioridad:** Alta

---

## HU-08.13 — Consultar el historial de facturas

**Como** supervisor,
**quiero** consultar las facturas emitidas,
**para** atender reclamos de clientes y revisar la operación.

**Criterios de aceptación:**

1. La lista muestra: fecha, serie, número, número de autorización, cliente, total, estado (certificada, en contingencia, anulada) y caja.
2. Puedo filtrar por fecha, estado, caja, cajero y cliente.
3. Puedo buscar por número de factura o número de autorización.

**Prioridad:** Media

---

## HU-08.14 — Consultar el detalle de una factura

**Como** supervisor,
**quiero** ver el detalle completo de una factura,
**para** revisar sus productos, pagos y estado ante la SAT.

**Criterios de aceptación:**

1. El detalle muestra los datos del emisor y del receptor, las líneas, el Subtotal, el IVA, el TOTAL, los pagos y los datos de certificación.
2. Muestra el historial de la factura: emisión, certificación, reimpresiones, anulación y notas de crédito asociadas.
3. Desde el detalle puedo reimprimir, enviar por correo o anular, según mis permisos.

**Prioridad:** Media

---

## HU-08.15 — Enviar la factura por correo

**Como** cajero,
**quiero** enviar la factura al correo del cliente,
**para** que la tenga en formato digital.

**Criterios de aceptación:**

1. **Dado** que el cliente tiene correo registrado (HU-05.11), **entonces** la factura se envía al finalizar la venta. **Dado** que no tiene correo, **entonces** la venta termina normalmente sin enviar nada.
2. El correo incluye la representación gráfica de la factura en PDF y, si se requiere, el documento electrónico (XML).
3. **Dado** que el envío falla, **entonces** el sistema lo reintenta y registra el error, sin afectar la venta.
4. Puedo reenviar la factura desde su detalle (HU-08.14).

**Prioridad:** Media

---

## Pendientes por confirmar con el cliente

- Certificador FEL que se va a contratar y su documentación técnica.
  Se utilizará la API REST estándar del certificador autorizado

- Plazos vigentes de la SAT para certificar comprobantes en contingencia y para anular facturas.
  Contingencia máxima de 15 días calendario para enviar facturas emitidas fuera de línea; anulaciones permitidas dentro del mes de emisión.

- Si el certificador envía las facturas por correo o lo debe hacer el POS.
  El POS solicitará el envío directamente al servicio web del certificador FEL.

- Si se requiere código QR en el ticket.
  Se incluye de forma obligatoria en la impresión del ticket según norma de la SAT.

- Series de facturación que se usarán por caja o por establecimiento.
  Se asignará una serie FEL por cada caja o establecimiento configurado.
