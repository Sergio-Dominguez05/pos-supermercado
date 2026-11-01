# 🤝 Guía de contribución

## 1. Ramas

| Rama | Uso | Se crea desde | Pull request hacia |
|---|---|---|---|
| `main` | Entregas estables | — | — |
| `develop` | Integración del sprint | `main` (inicialmente) | `main` |
| `feature/HU-XX.XX-descripcion` | Historia de usuario | `develop` | `develop` |
| `fix/descripcion` | Corrección de errores | `develop` | `develop` |
| `docs/descripcion` | Documentación | `develop` | `develop` |
| `chore/descripcion` | Configuración | `develop` | `develop` |

No usar espacios ni caracteres especiales en las ramas. Se recomienda minúsculas y guiones. Las ramas de trabajo se borran al integrar su PR.

## 2. Flujo normal

```bash
git switch develop
git pull origin develop
git switch -c feature/HU-03.06-buscar-por-codigo
# implementar y probar
git add .
git commit -m "feat(catalogo): buscar producto por codigo (HU-03.06)"
git push -u origin feature/HU-03.06-buscar-por-codigo
```

Crear el PR desde GitHub hacia **`develop`** e indicar qué Issue cierra. Para referenciarlo usar `Closes #123` **solo cuando la HU esté completa** (no inventar números).

Si la rama queda desactualizada, sincronizar con `develop` antes del merge. Evitar force-push sobre ramas compartidas.

## 3. Commits

Convención liviana basada en Conventional Commits:

- `feat(modulo): descripcion` — nueva funcionalidad.
- `fix(modulo): descripcion` — corrección.
- `docs: descripcion` — documentación.
- `test(modulo): descripcion` — pruebas.
- `chore: descripcion` — mantenimiento/configuración.
- `refactor(modulo): descripcion` — refactor sin cambio funcional.

Siempre que aplique, agregar el ID al final, por ejemplo: `feat(ventas): iniciar venta (HU-06.01)`.

## 4. Pull requests

Una HU por rama siempre que sea posible. Un PR debe incluir:

1. Objetivo y HU relacionada.
2. Cambios introducidos.
3. Evidencia de pruebas (o explicación de por qué no aplica).
4. Capturas, si hay cambios visuales.
5. Riesgos, migraciones o decisiones pendientes.

Si el equipo tiene más de un integrante, **otra persona revisa y aprueba** el PR. Cuando haya CI real de aplicación, sus checks deberán pasar antes del merge. Evitar mezclar refactors grandes con cambios funcionales.

## 5. Seguridad y datos

- Nunca subir archivos `.env`, contraseñas, tokens FEL, credenciales SAT, certificados, dumps de base de datos ni datos reales de clientes o tarjetas.
- Los ejemplos, pruebas y capturas deben utilizar datos ficticios.
- No guardar números completos de tarjeta ni datos confidenciales en Issues, PRs, logs o commits.

## 6. Definition of Done

Ver [Scrum adaptado](docs/proceso/scrum.md). Una historia no está «Hecha» por tener código escrito: debe cumplir sus criterios de aceptación y las pruebas pertinentes.
