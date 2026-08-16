# Rosy Floristas - Architecture Handbook

> Versión: 2.0
> Documento **compartido** entre `rosyFloristasFront` y `rosyFloristasBack`.
> Cubre visión de negocio y contexto de sistema. El detalle interno de cada repositorio
> vive en su propia documentación (enlazada, no duplicada aquí).

## Índice

1. Visión
2. Actores y contexto del sistema
3. Capacidades de negocio
4. Topología de despliegue (objetivo)
5. Principios arquitectónicos del backend
6. Alcance de este documento

---

# 1. Visión

Rosy Floristas es una tienda de floristería online.

- Catálogo de productos consultable por cualquier usuario, sin necesidad de registro.
- Compra posible como **invitado** o como **cliente registrado**.
- El cliente registrado guarda historial de compras, tarjetas, direcciones y teléfono.
- Carrito de compras.
- Entregas programadas según la franja horaria que elige el cliente.
- Panel de administración: gestión de pedidos (aceptar, denegar, cambiar estado),
  catálogo (categorías e imágenes), usuarios y administradores, métricas de venta, y
  registro manual de ventas realizadas en la tienda física.

---

# 2. Actores y contexto del sistema

- **Cliente invitado** — compra sin registrarse.
- **Cliente registrado** — histórico de compras y datos guardados (tarjetas, direcciones, teléfono).
- **Administrador** — panel de gestión; rutas protegidas por rol y segundo factor
  (TOTP / Google Authenticator).
- **Operador de tienda física** — registra en el sistema ventas hechas en tienda.

```text
                ┌────────────────────────┐
                │   rosyFloristasFront    │
                │  (React) — tienda +     │
                │  panel admin (mismo     │
                │  repo, rutas por rol)   │
                └───────────┬─────────────┘
                             │ REST API (versionada)
                ┌───────────▼─────────────┐
                │   rosyFloristasBack      │
                │  (Spring Boot, DDD +     │
                │  Hexagonal)              │
                └───────────┬─────────────┘
                             │
                 ┌───────────┴───────────┐
                 │                       │
           PostgreSQL               Amazon S3
```

El panel de administración **no** es una aplicación separada: es parte de
`rosyFloristasFront`, con acceso restringido por rol y 2FA.

---

# 3. Capacidades de negocio

Con módulo backend ya identificado (esqueleto o implementado — ver
[PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md) para el estado real de cada uno):

| Capacidad | Módulo |
|---|---|
| Catálogo (categorías, productos, imágenes) | `category`, `product`, `image` |
| Carrito | `cart` |
| Cuenta y acceso (login, roles, 2FA admin) | `auth` |
| Datos de cliente (histórico, tarjetas, direcciones, teléfono) | `customer` |
| Pedidos y entrega por franja horaria | `order` |
| Pago | `payment` |
| Notificaciones | `notification` |

**Gaps abiertos** — capacidades de negocio ya definidas, sin módulo asignado todavía:

- **Métricas / reporting**: ventas de invitados vs. registrados; conteo de ventas por
  día/semana/mes/año; beneficio bruto (ventas − gastos de proveedor); comparativas
  interanuales (mismo mes, año anterior) e intermensuales, tanto de ventas como de
  gastos; clasificación de ventas y gastos por tipo de producto/proveedor.
- **Gastos y proveedores**: no existe concepto de dominio hoy; es prerequisito del
  módulo de métricas.

Estos dos gaps quedan pendientes de decisión arquitectónica (nuevo módulo vs.
extensión de uno existente) antes de implementarse.

---

# 4. Topología de despliegue (objetivo)

```text
Internet
   │
Cloudflare
   │
 HTTPS
   │
 Nginx
 ├───────────────┐
 │               │
React        Spring Boot
 │               │
 └──────┬────────┘
        │
   PostgreSQL
        │
    Amazon S3
```

> Topología **objetivo**, aún no desplegada. A día de hoy solo se está construyendo
> el backend (`rosyFloristasBack`).

---

# 5. Principios arquitectónicos del backend

Aplican únicamente a `rosyFloristasBack`. Detalle completo y vinculante:
[00-project-principles.md](00-project-principles.md) y las
[ADRs](ADR/ADR-001-use-case-first.md).

- Monolito Modular + DDD + Arquitectura Hexagonal (Ports & Adapters).
- Use Case First: cada acción de negocio es un caso de uso independiente.
- El Dominio nunca depende de Spring, JPA, JDBC ni HTTP.
- `Category` es el módulo de referencia; todo módulo nuevo replica su estructura.

---

# 6. Alcance de este documento

Este documento cubre **visión de negocio y contexto de sistema compartido** entre
frontend y backend. No duplica documentación interna de ningún repositorio:

- Convenciones internas del backend (capas, ports, naming, persistencia, testing):
  `docs/architecture/*` de este repositorio.
- Convenciones internas del frontend: viven en `rosyFloristasFront`.
