# Configuración recomendada de GitHub

## A. Crear el repositorio

- Nombre sugerido: `pos-supermercado`.
- Visibilidad: **privada** si la documentación es interna del curso o del cliente. Hacerlo público solo con autorización del equipo y tras revisar información sensible.
- Rama por defecto: `main`.
- Subir esta plantilla como commit inicial en `main`.
- Crear `develop` desde `main`, después del primer commit.

## B. Configuración general

En **Settings → General / Pull Requests**:

- Activar **Allow squash merging** para mantener el historial limpio.
- Opcionalmente desactivar merge commits y rebase merging, si todo el equipo acepta squash.
- Activar **Automatically delete head branches** para limpiar ramas tras cada PR.
- Activar Issues y Projects según disponibilidad.
- No habilitar Pages ni publicar el sistema por defecto.

## C. Protecciones de ramas (si el plan lo permite)

En **Settings → Branches** o **Settings → Rules → Rulesets**, según la interfaz:

- Para `main`: exigir Pull Request antes de integrar, bloquear force push y borrado, al menos una aprobación si hay otro revisor disponible, y checks de CI después de comprobar sus nombres reales.
- Para `develop`: exigir Pull Request, prohibir force push y borrado, exigir checks de CI una vez que existan.
- No activar exigencia de checks que todavía no existan o fallen por ausencia del Hello World.
- Nota: el **plan GitHub Free** permite protecciones para repositorios públicos; la protección de repositorios privados puede requerir GitHub Pro/Team u otro plan compatible. Si no aparece, documentar y aplicar estas reglas por acuerdo del equipo.

## D. Labels sugeridas para Issues

- Tipo: `user-story`, `bug`, `technical`, `documentation`.
- Prioridad: `priority:high`, `priority:medium`, `priority:low`.
- Estado de bloqueo: `blocked`.
- Módulos: `frontend`, `backend`, `database`, `fel`, `security`.
- Alcance: `v1`, `future`.

Evitar duplicar muchos estados de GitHub Projects con labels; elegir una fuente de verdad para el estado.

## E. GitHub Project / Sprint

Crear un Project con campos **Status**, **Sprint/Iteration**, **Priority**, **Epic** y **Owner**. Vista Board por Status y vista Table filtrada por Sprint. Cargar primero las historias V1 priorizadas, asignar cada una a un Issue y enlazarla al archivo original.

Propuesta de estados: **Backlog → Ready → In Progress → In Review → Done**. Cada sprint se define según capacidad real, no por un número arbitrario de HUs.

## F. Entrega

Cada cierre de sprint puede incluir resumen de objetivos, historias aceptadas, demostración, problemas y próximos pasos. Para una entrega estable, integrar `develop` en `main` por PR y etiquetar el commit, por ejemplo `v0.1.0`.
