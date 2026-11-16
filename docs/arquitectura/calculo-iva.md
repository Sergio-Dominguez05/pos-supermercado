# Cálculo de IVA y reglas de redondeo

Reglas del módulo `impuestos` (EP-04). Están implementadas en `backend/src/main/java/gt/supermercado/pos/impuestos/dominio/` y cubiertas por las pruebas del mismo paquete. Resumen de [Architecture.md §7.1](../original/Architecture.md).

## Tasa

- Todos los productos están gravados con el **IVA del 12 %**; no hay productos exentos (HU-04.01). La tasa es la constante `TasaIva.VIGENTE`; ninguna pantalla la puede cambiar.
- La tasa se guarda con 4 decimales (`0.1200`). Cada venta guardará la tasa con la que se calculó (`venta.tasa_iva`, HU-06.01), para que las ventas pasadas no cambien si la tasa cambia.

## Aritmética

- Todo se calcula con `BigDecimal` (en la base, `NUMERIC`). **Nunca** con `float` ni `double` (HU-04.02 crit. 4).
- Los precios del catálogo **incluyen** el IVA. El sistema separa la base imponible y el IVA sin cambiar lo que paga el cliente.

## Pasos del cálculo

| Paso | Fórmula | Decimales | Redondeo | Clase / método |
|------|---------|-----------|----------|----------------|
| Subtotal de la línea | `precio unitario × cantidad` | exacto, se guarda con 6 | ninguno | `CalculadoraIva.linea` |
| Base imponible de la línea | `subtotal / (1 + tasa)` | 6 | al medio hacia arriba | `CalculadoraIva.desglosar` |
| IVA de la línea | `subtotal − base` | 6 | ninguno (resta exacta) | `CalculadoraIva.desglosar` |
| Sumas de las líneas | suma de subtotales, de IVA | 6 | **ninguno** | `CalculadoraIva.totales` |
| **TOTAL** | suma de subtotales | 2 | al medio hacia arriba | `CalculadoraIva.redondearTotal` |
| **IVA** | suma del IVA de las líneas | 2 | al medio hacia arriba | `CalculadoraIva.redondearTotal` |
| **Subtotal** | `TOTAL − IVA` | 2 | ninguno (resta exacta) | `CalculadoraIva.totales` |

Ejemplos: Q11.20 → base Q10.00 + IVA Q1.20; Q5.00 → base Q4.464286 + IVA Q0.535714.

## Reglas de redondeo (HU-04.05)

1. **Los valores por línea y las sumas intermedias tienen hasta 6 decimales y no se redondean a 2** (crit. 1). La única operación que redondea antes del final es la división de la base imponible, porque no siempre da un resultado exacto; se redondea en el sexto decimal.
2. **Solo los resultados finales —Subtotal, IVA y TOTAL— se redondean a 2 decimales** (crit. 2).
3. **Al medio hacia arriba** (`RoundingMode.HALF_UP`, crit. 3): si el primer decimal que se elimina es 5 o más, el decimal anterior sube en 1.
   - 10.715 → **10.72**
   - 10.714 → **10.71**
   - 10.725 → **10.73**: siempre hacia arriba, no "al par" como en el redondeo bancario.
   - 99.995 → **100.00**
4. El Subtotal se obtiene como `TOTAL − IVA`, así **Subtotal + IVA = TOTAL** siempre (HU-04.04 crit. 5). Puede diferir en un centavo de la suma de las bases redondeada; la decisión **D3** (Architecture.md §12.1) acepta esa diferencia en la primera entrega.

Por qué no se redondea cada línea: con 7 productos de Q5.00, el IVA correcto es 7 × 0.535714 = 3.749998 → **Q3.75**. Redondeando cada línea primero daría 7 × 0.54 = Q3.78, tres centavos de más, y el certificador FEL rechazaría la factura por diferencias.

## Pruebas

| Regla | Prueba |
|-------|--------|
| Tasa del 12 % y su conservación por venta | `TasaIvaTest` |
| Desglose desde el precio con IVA | `CalculadoraIvaTest` |
| Cálculo por línea con 6 decimales | `LineaCalculadaTest` |
| Totales y Subtotal + IVA = TOTAL | `TotalesTest` |
| Reglas de redondeo y sus ejemplos | `RedondeoTest` |
