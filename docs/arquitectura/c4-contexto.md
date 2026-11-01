# C4 · Nivel 1: contexto del sistema

```mermaid
C4Context
    title Contexto - POS de supermercado con FEL
    Person(cajero, "Cajero", "Opera ventas y caja")
    Person(admin, "Administrador", "Administra y consulta reportes")
    System(pos, "Sistema POS", "Ventas, caja y facturacion del supermercado")
    System_Ext(fel, "Certificador FEL", "Servicio de certificacion electronica")
    Rel(cajero, pos, "Opera ventas y cobros")
    Rel(admin, pos, "Administra usuarios, catalogo y reportes")
    Rel(pos, fel, "Envia DTE y consulta estados", "HTTPS")
```

**Límite del sistema:** el POS pertenece al supermercado. El certificador es un sistema externo y solo está disponible si hay conexión a internet. No incluye integración directa con un procesador bancario ni con inventario externo en la V1.
