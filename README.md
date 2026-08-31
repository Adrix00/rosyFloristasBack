# rosyFloristasBack
Backend with a monolithic architecture for the Rosy Floristas website

## Flujo de desarrollo

Cada funcionalidad de `docs/features/` se implementa en su propia rama, siguiendo el orden de
implementación acordado en `docs/features/` (ver `.claude/dev-plan.md`, no versionado).

Reglas:

1. **Una rama `feature/` por funcionalidad**, creada desde `main` actualizado:
   `git checkout main && git pull && git checkout -b feature/<nombre-funcionalidad>`.
2. **No se crea la siguiente rama `feature/` hasta que la anterior esté mergeada en `main`.**
   Trabajo secuencial, no en paralelo: evita conflictos entre funcionalidades que comparten
   entidades de dominio o migraciones.
3. Dentro de cada rama, el orden de implementación por capas es el fijado en `CLAUDE.md`
   (REST API → DTOs de petición → DTOs de respuesta → Commands → Queries → Input Ports →
   Services → Output Ports → Domain → Persistence → Tests). No se saltan pasos.
4. `mvn verify` debe pasar antes de abrir el PR de la rama.
5. Antes de tocar código, se lee el documento de la funcionalidad en `docs/features/` y los ADR
   de `docs/architecture/ADR/` que le apliquen (ver tabla de referencia en `CLAUDE.md`).
