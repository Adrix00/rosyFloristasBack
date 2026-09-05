# Informe de revisión — `attribute` (attribute-definition)

> Fecha: 2026-09-03 · Rama: mergeado en `main` (`c51716f`) · Spec: secciones de `product.md` (3.5/4/7/8/9), `order.md`, `00-security-validation-integrity.md`, `database/README.md`

## 1. Veredicto

El módulo cumple la especificación en lo esencial: inmutabilidad de `attributeKey`/`dataType`, borrado que deja huérfano el JSONB, `GET` público, escrituras `ADMIN`, y el 409 por clave duplicada protegido contra carreras por la constraint traducida. La arquitectura es limpia y fiel al módulo de referencia. Los problemas reales están en la higiene de logs del adaptador (inyección de log, CWE-117) y en los tests: la puerta de autorización no está protegida por ningún test y el único test de ordenación es vacuo.

## 2. Fortalezas

- Cumplimiento de la regla 3.5: `attributeKey` y `dataType` son `final` en el agregado y no aparecen en `UpdateAttributeDefinitionRequest`; el borrado no toca el JSONB de productos.
- Sin TOCTOU en la creación: el chequeo `findByKey` previo es solo UX; la `uq_product_attribute_definitions_key` es la garantía real y se traduce a 409 `ATTRIBUTE_DEFINITION_ALREADY_EXISTS` sin filtrar el nombre de la constraint (ADR-012).
- Capas correctas: dominio sin Spring/JPA/JDBC, un servicio por caso de uso, puerto de capacidades con justificación explícita, JPA para escrituras/lookups y JDBC para el listado ordenado (ADR-002/003).
- `ProductAttributeValidator` reutiliza el puerto en lugar de duplicar lógica; `SearchProductsService` exige `filterable=true` para los filtros `attr.{clave}`.
- Test de integración contra PostgreSQL real que ejercita la traducción de la constraint.

## 3. Hallazgos

| # | Severidad | Archivo | Hallazgo |
|---|---|---|---|
| 1 | 🟡 **Medio** | `AttributeDefinitionPersistenceAdapter.java:75,78,89,122` | **`attributeKey` llega crudo a los logs (inyección de log, CWE-117).** Texto controlado por la petición se registra sin sanitizar en `findByKey`, `save` y en el mensaje de la violación de constraint; el módulo de referencia (`CategoryPersistenceAdapter`) envuelve con `Encode.forJava(...)` inline. Además, `GlobalExceptionHandler.handleConflict` loguea `exception.getMessage()` sin sanitizar (vía secundaria del mismo taint). |
| 2 | 🟡 **Medio** | `AttributeDefinitionControllerTest.java` (solo cubre POST) | **La autorización no está protegida por ningún test.** `@PreAuthorize("hasRole('ADMIN')")` en los servicios no lo ejercita ningún test: los unitarios son sin Spring y el de controlador mockea los casos de uso. Si alguien borra la anotación, toda la suite sigue en verde y un anónimo puede crear definiciones. |
| 3 | 🟡 **Medio** | `AttributeDefinitionPersistenceAdapterTest.java:74-95` | **Test de ordenación vacuo.** `listsOrderedByPositionThenLabel` solo asevera `isNotEmpty()`; la ordenación `ORDER BY position, label` no se comprueba. Si el SQL cambia, el test sigue pasando. |
| 4 | 🟢 **Bajo** | `AttributeDefinitionController.java:77,94,111` | **Javadoc obsoleto:** afirma que el rol "no se aplica", pero los servicios sí llevan `@PreAuthorize` y `SecurityConfig` tiene `@EnableMethodSecurity`. |
| 5 | 🟢 **Bajo** | `AttributeErrorCode.java:7` | **Constante muerta:** `ATTRIBUTE_VALIDATION_FAILED` no se referencia en producción; el 422 lo deriva `GlobalExceptionHandler.validationCodeFor` del nombre del paquete del DTO. Si el paquete cambia, enum y wire divergen. |
| 6 | 🟢 **Bajo** | `docs/features/product.md` §9 | **Códigos de error no documentados:** `ATTRIBUTE_DEFINITION_NOT_FOUND` (404) y `ATTRIBUTE_DEFINITION_ALREADY_EXISTS` (409) no aparecen en la tabla de errores de product.md. |
| 7 | 🟢 **Bajo** | `AttributeDefinitionController.java:102,117` (transversal) | **UUID malformado en ruta → 500** en vez de 400/404: `MethodArgumentTypeMismatchException` no tiene handler y cae en `handleUnexpected`. Patrón transversal preexistente (category lo comparte). |

## 4. Recomendaciones

1. **Sanitizar logs** (#1): `Encode.forJava(attributeKey)` inline en las 4 llamadas del adaptador y en la salida de `CreateAttributeDefinitionService`.
2. **Proteger la autorización con tests** (#2): test de integración (patrón de `AdminAuthorizationIntegrationTest`) que verifique 401 anónimo en POST y 200 anónimo en GET, más tests de controlador para PUT 200/404 y DELETE 204/404.
3. **Arreglar el test de ordenación** (#3): aseverar el orden real (dos posiciones distintas y dos etiquetas en la misma posición).
4. **Opcional:** corregir Javadoc (#4), decidir el destino de la constante muerta (#5), documentar los códigos de error (#6), handler transversal para `MethodArgumentTypeMismatchException` (#7).
