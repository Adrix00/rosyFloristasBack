# Informe de revisión — `inventory`

> Fecha: 2026-09-03 · Rama: mergeado en `main` (PR #11, `73bad0c`+`3e069bb`) · Spec: `inventory.md` · ADRs: 013, 014, 009, 012

## 1. Veredicto

El módulo está bien construido en lo estructural (DDD/hexagonal impecable, ADR-002/003/012 respetados, tests de buena calidad), pero la **reactivación de inventario** — caso de negocio documentado en product.md 3.7 — está rota de punta a punta: falla siempre con 500 por un problema transaccional, y aunque se arreglara eso, la cantidad que escribe rompe el invariante de reconciliación que ADR-013 existe para proteger. Son dos bugs independientes en el mismo flujo, y ningún test los cubre.

## 2. Fortalezas

- El `UPDATE` condicional de la regla 3.1 está implementado exactamente como el spec (`stock = stock - ? WHERE id = ? AND stock IS NOT NULL AND stock >= ? RETURNING stock`), con `resulting_stock` tomado del `RETURNING`, nunca calculado aparte; un test lo protege con un valor deliberadamente "imposible".
- `saveAndFlush` + traducción de `ux_stock_movements_initial` a `InventoryAlreadyInitializedException` dentro del propio stack frame del adapter es un detalle fino y bien documentado.
- `GenerateInventoryAlertsService` deliberadamente sin `@Transactional` para que un duplicado (resultado rutinario) no tumbe el lote — decisión correcta y explicada.
- Tests de integración con Testcontainers que protegen garantías reales de BD (índice único parcial, `UPDATE` condicional, filtros de la consulta `LOW_STOCK`); los tests de servicio leen como especificaciones ejecutables.
- Sin PII en logs, `Encode.forJava` inline en parámetros de query controlados por el usuario, CSRF cubierto en los tests de controlador.

## 3. Hallazgos

| # | Severidad | Archivo | Hallazgo |
|---|---|---|---|
| 1 | 🔴 **Crítico** | `ProductInventoryPersistenceAdapter.java:50-60` | **La reactivación de inventario falla siempre con 500.** El intento de `INITIAL` viola `ux_stock_movements_initial`; la excepción traducida atraviesa el proxy transaccional de `RegisterStockMovementService` y marca rollback-only la transacción externa de `ChangeInventoryModeService`; el fallback `reactivate()` corre dentro de esa transacción condenada → "current transaction is aborted" o `UnexpectedRollbackException`. El test de integración pasa solo porque llama al adapter directamente, sin la transacción externa del servicio — enmascara el bug. |
| 2 | 🔴 **Alto** | `RegisterStockMovementService.java:117-128` | **La cantidad del `ADJUSTMENT` de reactivación rompe el invariante `stock = SUM(movements)`.** Escribe la cantidad absoluta, no el delta respecto a la suma de movimientos previa. `INITIAL 10` → desactivar → reactivar con 7 → `ADJUSTMENT +7` → `SUM = 17`, `stock = 7` → alerta `RECONCILIATION_MISMATCH` falsa cada día (bucle infinito de falsas alarmas). La cantidad correcta es `newStock - SUM(movimientos previos)`. |
| 3 | 🟡 **Medio** | `RegisterWasteService.java:18-19`, `RegisterAdjustmentService.java:17-18`, `ResolveInventoryAlertService.java:24-25`, `DismissInventoryAlertService.java:24-25` | **`adminUserId`/`resolvedByAdminId` siempre `null` con justificación obsoleta.** Los Javadocs dicen "no auth module exists yet (pending feature/auth)", pero `feature/auth` ya está mergeado. inventory.md §6 exige `adminUserName`/`resolvedByAdminName`; la API devuelve siempre `null`. Desviación del spec con justificación factualmente incorrecta. |
| 4 | 🟡 **Medio** | `ProductInventoryPersistenceAdapter.java:69-78` | **TOCTOU en `adjustStock`.** Lee el stock actual para convertir el valor absoluto en delta; el `UPDATE` condicional no predica sobre el valor leído y el `@Version` de `products` (ADR-009) no protege este camino (los `UPDATE` JDBC de stock no tocan `version`). Dos ajustes concurrentes (stock 5 → piden 10 y 12) producen stock 17, que no es lo que pidió ninguno, en silencio. |
| 5 | 🟢 **Bajo** | `StockMovementController.java:62,83,100`, `InventoryAlertController.java:59,89,107` | **Javadoc "ADMIN — unenforced" obsoleto:** los endpoints SÍ están protegidos con `@PreAuthorize` + `@EnableMethodSecurity`. |
| 6 | 🟢 **Bajo** | `InventoryAlertPort.java` (`findOpen`), `InventoryAlertPersistenceAdapter.java:93-99`, `InventoryAlertJdbcRepository.java:41-46`, `InventoryAlertRowMapper.java` | **Código muerto:** `findOpen` no tiene ningún llamador (la tarea diaria usa `ux_inventory_alerts_open`). El spec §8 lo lista, pero es flexibilidad muerta. |
| 7 | 🟢 **Bajo** | `RegisterStockMovementService.java:86-107` | **Override innecesario de `execute(UUID, ...)`** con justificación técnicamente incorrecta: la auto-invocación del método `default` ya corre dentro de la transacción thread-bound iniciada por el proxy. |
| 8 | 🟢 **Bajo** | `RegisterStockMovementService.java:118` | **Log de entrada de `reactivate` omite parámetros** (`adminUserId`, `note`), contra la regla de logging de CLAUDE.md. |
| 9 | 🟢 **Bajo** | `docs/database/README.md:462-465` | **Inconsistencia de documentación:** el README exige que "ambas" consultas de reconciliación devuelvan cero filas, pero la segunda devolvería filas para todo producto desactivado (el historial queda intacto). El código implementa solo la primera, que es lo correcto; es el README el que contradice a los specs. |

## 4. Recomendaciones

1. **Bloquea el merge:** #1 (reactivación 500) — p. ej. `REQUIRES_NEW` para el intento, o comprobar la existencia de `INITIAL` antes de intentarlo, o `INSERT ... ON CONFLICT DO NOTHING` y ramificar por filas afectadas.
2. **Antes de producción:** #2 (delta correcto en el `ADJUSTMENT` de reactivación) y #4 (predicado `WHERE stock = :leído` o paso por `@Version`).
3. **Tests que faltan:** un test que pase por `ChangeInventoryModeService` con transacción real (habría cazado #1) y una aserción del invariante `stock = SUM(movements)` tras reactivar (habría cazado #2).
4. **Opcional:** #3 (resolver el principal del `SecurityContext` y propagar el nombre del admin), #5–#9.
