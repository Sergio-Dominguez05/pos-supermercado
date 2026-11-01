# C4 · Nivel 3: componentes principales del backend

Representación conceptual y simplificada de los componentes/módulos más relevantes del contenedor **API backend** (Spring Boot). Los módulos están propuestos, no implementados todavía.

```mermaid
flowchart LR
    WEB["Aplicacion React"] --> REST["Controladores REST / API"]
    subgraph API["Backend Spring Boot - monolito modular"]
        REST --> SEG["seguridad"]
        REST --> CAT["catalogo"]
        REST --> VTA["ventas"]
        REST --> CJA["caja"]
        REST --> REP["reportes"]
        VTA --> CAT
        VTA --> IVA["impuestos"]
        VTA --> CLI["clientes"]
        VTA --> CJA
        PAG["pagos"] --> VTA
        PAG --> FAC["facturacion"]
        FAC --> IVA
        FAC --> CFG["configuracion"]
        FAC --> IMP["impresion"]
        SEG --> AUD["bitacora"]
        CAT --> AUD
        CFG --> AUD
    end
    API --> DB[("PostgreSQL")]
    FAC --> FEL["Certificador FEL externo"]
```

**Regla de dependencia:** los módulos se comunican mediante servicios de aplicación públicos y no acceden directamente a repositorios internos de otro módulo. Ver la sección 4 del documento arquitectónico original.
