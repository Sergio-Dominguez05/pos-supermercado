# Publicar el repositorio en GitHub

## 1. Crear el repositorio remoto

En GitHub, elegir **New repository**, nombre sugerido `pos-supermercado` y visibilidad privada si se trata de documentación interna. No marcar **Add README**, **Add .gitignore** ni **Choose a license**, porque ya hay archivos locales y no se ha escogido una licencia.

## 2. Descargar y extraer esta plantilla

Extraer `pos-supermercado-repositorio.zip` y abrir una terminal **PowerShell** dentro de la carpeta `pos-supermercado`.

## 3. Enlazar Git con GitHub

Reemplazar `TU_USUARIO` por el dueño real del repositorio. Se requiere Git instalado y la autenticación de GitHub configurada.

```powershell
git init -b main
git add .
git commit -m "chore: preparar repositorio y documentacion inicial"
git remote add origin https://github.com/TU_USUARIO/pos-supermercado.git
git push -u origin main

git switch -c develop
git push -u origin develop
```

Si el equipo ya tiene un repositorio creado **con commits**, primero clonarlo y copiar/combinar los archivos en una rama de trabajo. No ejecutar `git init` o `git push` a ciegas sobre ese repositorio.

## 4. Configurar la rama por defecto y opciones

En GitHub → **Settings → General → Default branch**, definir `develop` como default si se desea que los PR nuevos apunten a integración por defecto; mantener `main` como rama de producción/entregas. Esto es opcional; también puede permanecer `main` como default y elegir `develop` en cada PR. Este proyecto recomienda **`main` como rama por defecto**, para hacer explícito el target `develop` durante cada PR.

Después seguir [la guía de GitHub](docs/proceso/repositorio-github.md) para reviews, borrado automático de ramas, permisos y proyectos.
