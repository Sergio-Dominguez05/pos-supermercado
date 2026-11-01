# Scrum adaptado para el curso

## Enfoque

Se utilizarán sprints cortos con planificación, revisión y retrospectiva breves. Los roles y ceremonias se adaptarán a la disponibilidad del equipo; no es necesario replicar Scrum literalmente.

## Sprint 0: preparación técnica (esta entrega)

**Objetivo:** tener un repositorio colaborativo organizado y documentado para iniciar el desarrollo incremental.

**Checklist:**

- [x] Arquitectura y alcance originales almacenados en `docs/original/`.
- [x] README descriptivo y diagramas C4.
- [x] Convenciones de ramas, commits, PR y Definition of Done.
- [x] Plantillas de GitHub Issues y PR.
- [x] Estructura reservada para backend/frontend.
- [ ] Crear repositorio remoto y rama `develop` en GitHub.
- [ ] Configurar protección de ramas (según plan de GitHub).
- [ ] Inicializar y verificar el Hello World de Spring Boot, React y PostgreSQL.
- [ ] Instalar las dependencias y completar CI de compilación/pruebas.
- [ ] Crear Issues y acordar las iteraciones reales con el equipo.

## Propuesta de secuencia

La arquitectura incluye una propuesta de sprints 1–8 para las **115 HU de la V1**. Se utilizará como referencia y no como compromiso cerrado. Consultar [sección 10 del documento original](../original/Architecture.md).

## Definition of Ready (mínima)

Una HU puede entrar a sprint si tiene objetivo claro, criterios de aceptación comprobables, alcance V1/futuro definido, dependencias conocidas y responsable asignado.

## Definition of Done (DoD)

- [ ] Cumple criterios de aceptación de la HU.
- [ ] Cambios integrados por PR y revisados por otra persona si hay más de un integrante.
- [ ] Compila y las pruebas pertinentes pasan.
- [ ] Si cambia persistencia, migración Flyway versionada.
- [ ] Permisos verificados en backend cuando corresponda.
- [ ] Las operaciones sensibles se registran en bitácora cuando corresponda.
- [ ] Se actualiza documentación si cambia configuración, API o arquitectura.
- [ ] La funcionalidad puede demostrarse en la revisión del sprint.

## Reuniones ligeras

- Planificación: acordar objetivo y elegir HU considerando capacidad.
- Check-ins breves y asíncronos cuando convenga.
- Revisión: demostración de software funcionando.
- Retrospectiva: una mejora concreta para el próximo sprint.
