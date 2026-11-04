# Entorno de desarrollo — Sprint 0

Esta guía documenta **la base de desarrollo**, no las HU funcionales. Se entrega para integrarse sobre el monorepo existente, sin alterar sus ramas, reglas de protección, Issues ni Project.

## Requisitos

- Node.js **24** y npm (Vite 8 también es compatible con Node 22.12+).
- Java **21** (`java -version` y `JAVA_HOME` configurado).
- Docker Desktop con Docker Compose.
- Git (para el flujo con ramas y Pull Requests).

No hace falta instalar Maven globalmente: `backend/mvnw.cmd` en Windows y `backend/mvnw` en Linux/macOS descargan la versión fijada cuando se usan por primera vez.

## Comandos desde la raíz del repositorio

```powershell
npm install        # crea .env de ejemplo si no existía e instala dependencias frontend
npm run dev        # levanta PostgreSQL y ejecuta Spring Boot + Vite
```

El arranque de Maven puede descargar dependencias la primera vez. **Docker Desktop tiene que estar abierto**; si falta Docker, `npm run dev` se detiene con un error explícito.

Direcciones:

- Frontend: <http://localhost:5173/sistema>
- Backend: <http://localhost:8080/api/sistema/info>
- Health: <http://localhost:8080/actuator/health>
- PostgreSQL local (desde el equipo): `localhost:5433`, base `pos_db`.

### Comandos adicionales

| Comando | Función |
|---|---|
| `npm run db:up` | Crear/iniciar PostgreSQL y esperar estado saludable |
| `npm run db:down` | Detener contenedor conservando datos |
| `npm run db:logs` | Ver logs de PostgreSQL |
| `npm run dev:frontend` | Iniciar solamente Vite |
| `npm run dev:backend` | Iniciar solamente Spring Boot (requiere BD) |
| `npm run build` | Compilar ambas aplicaciones |
| `npm run test` | Pruebas Vitest y Maven, incluidas las de Testcontainers (requiere Docker) |
| `npm run lint` | Análisis estático del frontend |

`npm run build` no necesita arrancar PostgreSQL; `npm run test:backend` sí requiere Docker para Testcontainers.

## Variables de entorno

`npm install` crea automáticamente un `.env` local desde `.env.example` si aún no existe. El archivo `.env` se ignora en Git.

- Docker Compose lee `.env` al levantar PostgreSQL.
- `scripts/maven.mjs` carga las variables del mismo `.env` y las pasa al backend.
- El frontend utiliza el proxy `/api → localhost:8080` de Vite, sin credenciales ni URLs absolutas de API.
- Cambiar `POS_DB_PORT` o los datos de acceso se refleja en Docker y en Spring Boot. **Si ya había un volumen de PostgreSQL, cambiar contraseñas no actualiza los roles existentes automáticamente**; consulte los logs y migre los valores manualmente. No use `docker compose down -v` con datos que quiera conservar.
- Los valores de `.env.example` son exclusivamente de desarrollo, **nunca producción**.

### Primer inicio de sesión

Si todavía no hay ningún Administrador, el backend crea al arrancar el usuario **`admin`**. Su contraseña temporal es `POS_ADMIN_PASSWORD_INICIAL` (al menos 8 caracteres, con letras y números); en desarrollo, si el `.env` no la define, se usa el valor de `.env.example`. Entrar en <http://localhost:5173/login>.

- La variable solo se usa la primera vez; cambiarla después no modifica la contraseña de `admin`.
- En producción no tiene valor por defecto: si falta y no hay Administrador, el backend lo advierte en el log y nadie puede entrar.
- Con `admin`, la opción **Usuarios** (`/usuarios`, HU-01.06) muestra la lista con búsqueda y filtros; desde ahí, **Nuevo usuario** (HU-01.05) crea Administradores y Supervisores con contraseña temporal y Cajeros con PIN temporal. Los nombres de usuario no distinguen mayúsculas.

## Convenciones de GitHub

1. Descargar o extraer el proyecto base **dentro del clon de `pos-supermercado`**; no copiar `.git` del ZIP anterior.
2. Trabajar desde la rama `chore/sprint-0-entorno` creada a partir de `develop`.
3. Ejecutar `npm run build` y `npm run test`, con Docker disponible.
4. Abrir un PR hacia `develop` y revisar los checks antes de integrar.
5. En el Issue #2 marcar únicamente las tareas que se hayan probado en el equipo.

## Alcance y seguridad

El adaptador FEL está simulado para pruebas y no emite DTE reales. La pantalla `/sistema` verifica comunicación con PostgreSQL; no representa una implementación funcional de ventas, usuarios o facturación. Los controles de seguridad y validaciones de negocio se agregan en sus sprints correspondientes. Nunca subir contraseñas, tokens, clientes reales o credenciales FEL al repositorio.
