# C4 · Nivel 2: contenedores

```mermaid
C4Container
    title Contenedores - POS de supermercado
    Person(cajero, "Cajero y administrador", "Usan el sistema en la LAN")
    System_Ext(fel, "Certificador FEL", "Certificacion de DTE")
    System_Ext(smtp, "Servidor de correo", "SMTP del supermercado")
    System_Boundary(pos, "Sistema POS") {
        Container(web, "Aplicacion web", "React 19, TypeScript, Vite", "SPA en navegador de caja o administracion")
        Container(api, "API backend", "Java 21, Spring Boot 4.1", "Monolito modular, API REST y tareas programadas")
        ContainerDb(db, "Base de datos", "PostgreSQL 16", "Datos operativos, ventas y facturas")
    }
    Rel(cajero, web, "Utiliza")
    Rel(web, api, "Consume", "HTTPS/JSON en LAN")
    Rel(api, db, "Lee y escribe", "SQL")
    Rel(api, fel, "Certifica DTE", "HTTPS")
    Rel(api, smtp, "Envia facturas por correo", "SMTP con STARTTLS o SSL")
```

**Envío de facturas por correo (HU-08.15):** lo hace el POS, no el certificador (decisión de Sergio, 2026-11-20; cierra el pendiente de `epics.md` y reemplaza lo previsto en `Architecture.md` §6.4). El backend arma el PDF con el mismo contenido del ticket y adjunta el XML del DTE. Usa el servidor SMTP que configura el Administrador en Configuración > Correo, con la contraseña cifrada como la de FEL. Los envíos quedan en `tarea_pendiente` (`ENVIAR_CORREO`) y un proceso programado los reintenta sin afectar la venta.

**Elementos físicos adicionales:** se prevé comunicación backend → impresoras térmicas de red (TCP 9100). La disponibilidad de impresoras USB queda por confirmar. Los lectores de código de barras funcionan como teclado del navegador.
