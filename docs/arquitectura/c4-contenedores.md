# C4 · Nivel 2: contenedores

```mermaid
C4Container
    title Contenedores - POS de supermercado
    Person(cajero, "Cajero y administrador", "Usan el sistema en la LAN")
    System_Ext(fel, "Certificador FEL", "Certificacion de DTE")
    System_Boundary(pos, "Sistema POS") {
        Container(web, "Aplicacion web", "React 19, TypeScript, Vite", "SPA en navegador de caja o administracion")
        Container(api, "API backend", "Java 21, Spring Boot 4.1", "Monolito modular, API REST y tareas programadas")
        ContainerDb(db, "Base de datos", "PostgreSQL 16", "Datos operativos, ventas y facturas")
    }
    Rel(cajero, web, "Utiliza")
    Rel(web, api, "Consume", "HTTPS/JSON en LAN")
    Rel(api, db, "Lee y escribe", "SQL")
    Rel(api, fel, "Certifica DTE", "HTTPS")
```

**Elementos físicos adicionales:** se prevé comunicación backend → impresoras térmicas de red (TCP 9100). La disponibilidad de impresoras USB queda por confirmar. Los lectores de código de barras funcionan como teclado del navegador.
