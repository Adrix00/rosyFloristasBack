# Gestión de versiones y releases

Este documento describe el sistema de automatización de releases del proyecto: 3 workflows de
GitHub Actions que únicamente orquestan la ejecución de 5 scripts Bash versionados en
[`scripts/`](../../scripts/), más una batería de tests Bats en
[`scripts/tests/`](../../scripts/tests/) y dos jobs de calidad (ShellCheck + Bats) en `ci.yml`.
Toda la lógica (cálculo de versiones, git, Maven,
creación de PRs) vive en los scripts; los YAML se limitan a checkout, configurar Java, dar permisos
de ejecución y ejecutar el script correspondiente.

## Índice

- [Visión general del flujo](#visión-general-del-flujo)
- [Workflow 1 — Create Release](#workflow-1--create-release)
- [Workflow 2 — Release Tagging](#workflow-2--release-tagging)
- [Workflow 3 — Release Sync](#workflow-3--release-sync)
- [Scripts](#scripts)
- [Modo dry-run](#modo-dry-run)
- [Decisiones de diseño y alternativas consideradas](#decisiones-de-diseño-y-alternativas-consideradas)
- [Configuración necesaria en GitHub](#configuración-necesaria-en-github)
- [Permisos del `GITHUB_TOKEN`](#permisos-del-github_token)
- [Branch protection recomendada](#branch-protection-recomendada)
- [Instrucciones de instalación](#instrucciones-de-instalación)
- [Tests](#tests)
- [Limitaciones conocidas](#limitaciones-conocidas)

## Visión general del flujo

```
Git tags (fuente de verdad)
  │
  │  Create Release (manual, bump=minor o --version explícita)
  ▼
release/1.3.x  ── pom.xml: 1.3.0-SNAPSHOT ── tag anotado v1.3.0
  │
  │  bugfix/xxx o hotfix/xxx → PR contra release/1.3.x → merge
  ▼
release/1.3.x  ── pom.xml: 1.3.1-SNAPSHOT ── tag anotado v1.3.1   (Release Tagging)
  │
  │  (automático)
  ▼
main            ── checkout main + pull --ff-only + merge de release/1.3.x   (Release Sync)
```

Cada tag `vX.Y.Z` (anotado, no ligero) se corresponde exactamente con el commit en el que
`pom.xml` queda en `X.Y.Z-SNAPSHOT`. La fuente de verdad para calcular la siguiente versión es
siempre el último tag de Git — nunca `pom.xml` — salvo que se fuerce explícitamente una versión.
Ver la sección de [decisiones de diseño](#decisiones-de-diseño-y-alternativas-consideradas) para la
justificación completa.

## Workflow 1 — Create Release

Archivo: [`.github/workflows/create-release.yml`](../../.github/workflows/create-release.yml)
Script: [`scripts/create-release.sh`](../../scripts/create-release.sh)

- **Disparador:** `workflow_dispatch` manual, con dos inputs opcionales: `bump` (`major` | `minor`,
  sin `patch`) y `version` (string libre, p. ej. `2.0.0`). Al menos uno de los dos debe informarse.
- **Restricción "solo desde main":** no se fuerza en el YAML (el checkout usa el ref que se
  seleccione en el desplegable de GitHub), sino que `create-release.sh` comprueba explícitamente
  la rama actual y falla con un mensaje claro si no es `main`. Así toda la lógica de negocio
  (incluida esta validación) permanece en Bash, tal como se pidió.
- **Qué hace:**
  1. Aborta si el árbol de trabajo no está limpio (`ensure_clean_worktree`) y refresca ramas/tags
     desde `origin` (`sync_remote_refs`, `git fetch --tags --prune origin`).
  2. Calcula la siguiente versión:
     - Si se informó `version`, la usa tal cual (normalizada a `-SNAPSHOT` si hacía falta),
       **ignorando `bump`**.
     - Si no, busca el tag `vX.Y.Z` más alto de todo el repositorio (`version_last_tag_overall`,
       con `v0.0.0` como base si aún no existe ningún tag) y le aplica el `bump`. **La fuente de
       verdad es el tag, nunca `pom.xml`.**
  3. Comprueba que ni la rama `release/1.3.x` ni el tag `v1.3.0` existan ya en `origin` (evita
     sobrescrituras accidentales si el workflow se relanza).
  4. Crea la rama `release/1.3.x`, ejecuta `mvn versions:set -DnewVersion=1.3.0-SNAPSHOT`, comitea.
  5. Crea un **tag anotado** `v1.3.0` (`git tag -a ... -m "Release v1.3.0"`) y empuja rama + tag
     **atómicamente** (`git push --atomic`), para que un fallo de red a mitad de operación no deje
     la rama publicada sin su tag.
- **No dispara Release Sync:** en el momento de crear la rama, `release/X.Y.x` es idéntica a
  `main` (se acaba de bifurcar), así que sincronizar sería un no-op. Se documenta como decisión
  consciente, no como omisión.
- Soporta `--dry-run` (ver [Modo dry-run](#modo-dry-run)); no está expuesto como input del
  workflow todavía, solo como flag del script.

## Workflow 2 — Release Tagging

Archivo: [`.github/workflows/release-tagging.yml`](../../.github/workflows/release-tagging.yml)
Script: [`scripts/create-tag.sh`](../../scripts/create-tag.sh)

- **Disparador:** `pull_request` de tipo `closed` con `base` en `release/**`. Un `if` a nivel de
  job filtra que `merged == true` y que la rama origen (`github.head_ref`) empiece por `bugfix/`
  o `hotfix/` — así una PR cerrada sin mergear, o mergeada desde otra rama, no dispara nada.
- **Detalle importante:** el checkout usa explícitamente
  `ref: ${{ github.event.pull_request.base.ref }}` (la rama `release/X.Y.x`), no el ref por
  defecto del evento `pull_request` — ese ref sintético de merge no es fiable una vez la PR ya
  está cerrada.
- **Qué hace:**
  1. Aborta si el árbol de trabajo no está limpio, luego refresca ramas/tags desde `origin`.
  2. Busca el último tag `vX.Y.*` de esa línea de release (`git tag -l 'v1.3.*' | sort -V | tail -1`).
  3. Calcula el siguiente patch (`v1.3.4` → `v1.3.5`).
  4. `mvn versions:set` a `1.3.5-SNAPSHOT`, comitea, crea un **tag anotado**, push atómico.
  5. Dispara el Workflow 3 explícitamente: `gh workflow run release-sync.yml --ref main -f release_branch=release/1.3.x`.
- Soporta `--dry-run`. No se añadió un flag `--version` aquí (a diferencia de `create-release.sh`):
  este script lo dispara un merge de PR (un evento, no un `workflow_dispatch`), así que no hay un
  punto de entrada manual natural para forzar una versión.

## Workflow 3 — Release Sync

Archivo: [`.github/workflows/release-sync.yml`](../../.github/workflows/release-sync.yml)
Script: [`scripts/merge-main.sh`](../../scripts/merge-main.sh)

- **Disparador:** `workflow_dispatch` con un input obligatorio `release_branch` (string). Ver más
  abajo por qué se eligió esto en lugar de `on: push: tags:` o `workflow_run`.
- **Qué hace:**
  1. Aborta si el árbol de trabajo no está limpio, luego refresca ramas/tags desde `origin`.
  2. Comprueba que la rama a sincronizar existe en `origin`.
  3. **Se asegura de que `main` está al día**: `git checkout main` + `git pull --ff-only origin main`
     (falla con un error claro si por lo que sea `main` local hubiera divergido, en vez de crear un
     merge commit inesperado).
  4. `git fetch origin release/1.3.x` + `git merge --no-commit --no-ff FETCH_HEAD` sobre `main`.
     Este merge de prueba **se ejecuta siempre de verdad, incluso en `--dry-run`**, porque es la
     única forma fiable de saber si habría conflicto; siempre se deshace después con
     `git merge --abort`, así que el árbol de trabajo queda intacto pase lo que pase.
  5. Si no hay conflictos: comitea y hace `git push` directo a `main`. Si no había nada que
     sincronizar (ya estaba al día), no crea un commit vacío.
  6. Si hay conflictos: aborta el merge y crea (o reutiliza, si ya existe una abierta) una Pull
     Request `release/1.3.x → main` para resolución manual, vía `gh pr create`.
- No necesita compilar con Maven, por lo que este workflow no configura Java (pequeña
  optimización de tiempo de ejecución).
- Al estar implementado como `workflow_dispatch`, también se puede relanzar manualmente desde la
  pestaña Actions para recuperar una sincronización fallida, indicando la rama a sincronizar.
- Soporta `--dry-run` (ver [Modo dry-run](#modo-dry-run)): en el camino limpio no comitea ni
  empuja; en el camino con conflicto detecta el conflicto igualmente pero no crea la PR.

## Scripts

| Script | Rol | Se ejecuta directamente | Se usa como librería |
|---|---|---|---|
| `scripts/utils.sh` | Logging, identidad git del bot, `run_cmd` (wrapper dry-run), `ensure_clean_worktree`, `sync_remote_refs`, comprobaciones de existencia remota, commit+tag(anotado)+push atómico, disparo de Release Sync | No | Sí (por los otros 4) |
| `scripts/version.sh` | Cálculo de siguiente versión/tag/rama desde tags de Git; lectura legacy desde `pom.xml` | Sí (`scripts/version.sh <comando>`) | Sí (por create-release.sh y create-tag.sh) |
| `scripts/create-release.sh` | Orquesta el Workflow 1 completo | Sí | No |
| `scripts/create-tag.sh` | Orquesta el Workflow 2 completo | Sí | No |
| `scripts/merge-main.sh` | Orquesta el Workflow 3 completo | Sí | No |

Todos usan `set -euo pipefail`, resuelven su propio directorio vía `BASH_SOURCE` para poder
sourcearse entre sí sin depender del directorio de trabajo, y pasan `shellcheck` sin avisos.

`version.sh` funciona también como CLI independiente si se necesita en local o en un paso de
depuración:

```bash
scripts/version.sh last-tag-overall                     # v0.0.0 si no hay tags, o el más alto
scripts/version.sh next-release minor 1.2.4-SNAPSHOT     # 1.3.0-SNAPSHOT
scripts/version.sh next-patch 1.3.4-SNAPSHOT             # 1.3.5-SNAPSHOT
scripts/version.sh branch-name 1.3.0-SNAPSHOT            # release/1.3.x
scripts/version.sh tag-name 1.3.0-SNAPSHOT               # v1.3.0
scripts/version.sh ensure-snapshot 2.0.0                 # 2.0.0-SNAPSHOT
scripts/version.sh last-tag-for-branch release/1.3.x     # último vX.Y.Z de esa línea

# Legacy: ningún script de producción usa esto ya para calcular versiones
# (la fuente de verdad es Git). Se conserva por compatibilidad y depuración manual.
scripts/version.sh current                               # lee <version> de pom.xml vía Maven
```

## Modo dry-run

Los 5 scripts (a través de `create-release.sh`, `create-tag.sh` y `merge-main.sh`) aceptan un flag
`--dry-run`. Con él activo:

- **No se hace**: `git checkout -b` de la rama nueva, `mvn versions:set`, `git add`/`git commit`,
  `git tag`, `git push`, ni `gh pr create`/`gh workflow run`. Todo eso pasa por `run_cmd` en
  `utils.sh`, que en dry-run solo loguea `[dry-run] would run: ...` en vez de ejecutar.
- **Sí se hace**: todo lo que es solo lectura o necesario para que el cálculo sea correcto —
  `sync_remote_refs`, las comprobaciones `remote_branch_exists`/`remote_tag_exists`, la lectura de
  tags, y en `merge-main.sh` el propio `git merge --no-commit --no-ff` de prueba (siempre seguido
  de `git merge --abort`, tanto si hay conflicto como si no, para no dejar rastro).

Esto permite probar una release completa —incluida la detección real de conflictos de merge— sin
tocar el remoto ni el `pom.xml`. Es exactamente el mecanismo que usa la batería de tests Bats (ver
[Tests](#tests)) para validar el flujo completo sin GitHub.

No está expuesto todavía como input `workflow_dispatch` en los 3 workflows de producción — es una
extensión trivial a futuro (añadir un input `dry_run: boolean` y pasarlo como `--dry-run` al
script) si se quiere poder lanzar un dry-run manual desde la pestaña Actions.

## Decisiones de diseño y alternativas consideradas

**Los tags apuntan a un commit `-SNAPSHOT`, no a una versión "limpia".**
Esto es exactamente lo especificado: se tagea el mismo commit en el que `pom.xml` queda en
`X.Y.Z-SNAPSHOT`, sin un paso adicional que quite el sufijo. Es distinto de la convención clásica
de `maven-release-plugin` (tag en versión sin `-SNAPSHOT`, luego commit separado que abre la
siguiente `-SNAPSHOT`). Se ha implementado tal cual se pidió porque el flujo completo es
coherente con ello (cada tag es simplemente "el commit de partida de un patch", no un artefacto
publicado en un repositorio Maven) y añadir el paso extra de "des-snapshotear" no aporta valor
aquí. Si en el futuro se publican artefactos a un repositorio Maven (Nexus/Artifactory/Central),
esto merece revisarse, porque construir desde un tag produciría un artefacto `-SNAPSHOT`.

**Release Sync se dispara vía `workflow_dispatch` explícito, no vía `on: push: tags:` ni `workflow_run`.**
Dos problemas descartan las alternativas más "obvias":
- Un `git push` hecho con el `GITHUB_TOKEN` por defecto **no dispara** nuevos workflows (evita
  bucles recursivos) — es una restricción documentada de GitHub Actions. Un `on: push: tags: 'v*'`
  en `release-sync.yml` simplemente no se ejecutaría nunca tras el tag creado por
  `create-tag.sh`. Las excepciones documentadas a esta restricción son precisamente
  `workflow_dispatch` y `repository_dispatch`.
- `workflow_run` sí evita esa restricción, pero identificar "qué rama de release sincronizar" a
  partir del payload del evento es ambiguo: `github.event.workflow_run.head_branch` en un run
  disparado por un `pull_request` referencia la rama origen de la PR (`bugfix/…`/`hotfix/…`), no
  la rama base (`release/X.Y.x`) que es la que realmente se necesita sincronizar.

Por eso `create-tag.sh` dispara explícitamente `release-sync.yml` pasándole la rama como input
(`gh workflow run release-sync.yml -f release_branch=…`), lo cual además hace que Release Sync sea
re-ejecutable manualmente desde la UI de Actions para recuperación ante fallos.

**Autenticación con fallback `secrets.RELEASE_TOKEN || secrets.GITHUB_TOKEN`.**
Todos los `checkout` y las llamadas a `gh` usan este patrón. Funciona out-of-the-box con el
`GITHUB_TOKEN` por defecto; si más adelante hace falta un Personal Access Token (por ejemplo,
porque `branch protection` en `main` no permite bypassear el token por defecto — ver más abajo),
basta con añadir el secret `RELEASE_TOKEN` al repositorio, sin tocar ningún YAML.

**`fetch-depth: 0` en los tres workflows.**
`create-tag.sh` necesita ver todos los tags `vX.Y.*` de la línea de release para calcular el
siguiente patch. Un checkout superficial (`fetch-depth: 1`, el valor por defecto) no trae tags.
Se ha uniformizado a `fetch-depth: 0` en los tres workflows para evitar esta clase de bug de forma
sistemática; el coste es insignificante al tamaño actual del repositorio.

**`versions-maven-plugin` fijado con versión explícita en `pom.xml`.**
No hacía falta declararlo para que `mvn versions:set` funcione (Maven resuelve el prefijo
`versions` contra Maven Central si no está en el pom), pero dejarlo sin fijar significa que cada
ejecución podría resolver una versión distinta del plugin con el tiempo. Se ha añadido en
`<pluginManagement>` sin `<executions>` (no se ejecuta en el ciclo de vida normal, solo se invoca
explícitamente desde los scripts) para que el comportamiento sea reproducible.

**La fuente de verdad del versionado son los tags de Git, no `pom.xml` — con `version_read_current`
(lectura desde `pom.xml` vía Maven) conservada como legacy.**
`create-release.sh` calculaba antes el bump de major/minor leyendo `<version>` de `pom.xml` en
`main`. Ahora usa `version_last_tag_overall` (el tag `vX.Y.Z` más alto de todo el repositorio, con
`v0.0.0` como base si aún no existe ninguno). `create-tag.sh` ya calculaba el patch desde tags
(`version_last_tag_for_branch`) y no ha cambiado. La función `version_read_current` y el subcomando
`version.sh current` se mantienen tal cual (con un comentario `LEGACY` explicándolo) por
compatibilidad y para depuración manual, aunque ningún script de producción los invoque ya para
calcular versiones.

**El wrapper de dry-run se llama `run_cmd`, no `run`.**
Bats define su propia función `run` en el proceso de test para capturar `$status`/`$output`. Si el
wrapper de este proyecto se llamara igual y un `.bats` sourceara `utils.sh` directamente (como
hacen `utils.bats` y `version.bats`), pisaría el `run` de Bats y rompería la mecánica de los tests.
`run_cmd` evita la colisión.

**`merge-main.sh` sigue ejecutando un merge real (`git merge --no-commit --no-ff` + `git merge
--abort`) también en `--dry-run`, en vez de simular el resultado.**
Es la única forma de saber honestamente si una sincronización produciría conflicto o no; una
simulación basada solo en heurísticas de logs podría equivocarse. El `--abort` posterior (siempre,
haya o no conflicto) garantiza que el árbol de trabajo queda exactamente como estaba.

**`git pull origin main` se implementa como `git pull --ff-only origin main`.**
Añade una comprobación de seguridad barata: si por lo que sea `main` local hubiese divergido, falla
con un error claro en vez de crear un merge commit inesperado. En el camino normal (siempre debería
ser fast-forward, ya que nada más que este propio sistema escribe en `main`) el comportamiento es
idéntico al pedido.

**Concurrency por workflow, sin cancelar runs en curso.**
Los tres workflows declaran `concurrency: { cancel-in-progress: false }` para encolar en vez de
cancelar: `create-release.yml` con un grupo global (`create-release`, porque antes de que exista la
rama el único recurso en juego es "cuál es la última versión conocida", que es global);
`release-tagging.yml` y `release-sync.yml` con un grupo por rama de release
(`release-tagging-${{ github.event.pull_request.base.ref }}` / `release-sync-${{ inputs.release_branch }}`),
calculable en el momento del trigger, para que dos operaciones sobre la misma línea de release se
serialicen en vez de correr en paralelo.

**Los tests usan el `mvnw` real del proyecto y ejecutan `mvn versions:set` de verdad; solo se
mockea `gh`.**
Cada test crea su propio repositorio temporal con una copia de `mvnw` + `.mvn/` (el wrapper real) y
un `pom.xml` fixture minimal **sin `<parent>`** (solo `groupId`/`artifactId`/`version` + el
`versions-maven-plugin` pinneado a la misma versión que en producción). Se evita heredar
`spring-boot-starter-parent` en el fixture porque `versions:set` no lo necesita para nada y
forzaría resolver esa POM padre en cada máquina limpia; el plugin real que se ejerce sí es el
mismo, así que la integración con Maven queda validada de verdad. El wrapper de Maven cachea la
distribución descargada en `~/.m2/wrapper` una sola vez por máquina, así que el coste de red solo
se paga la primera vez. `gh` sí se mockea (`scripts/tests/fixtures/bin/gh`) porque no tiene sentido ni es
seguro golpear la API real de GitHub desde un test local o de CI.

## Configuración necesaria en GitHub

1. **Nada obligatorio de entrada:** el sistema funciona con el `GITHUB_TOKEN` por defecto siempre
   que la configuración de Settings → Actions → General → "Workflow permissions" del repositorio
   esté en **"Read and write permissions"** (si está en "Read repository contents permission
   only", ningún `git push` ni `gh` funcionará).
2. **Opcional (recomendado si `main`/`release/**` tienen "Require pull request before merging"
   activado sin excepciones):** crear un secret `RELEASE_TOKEN` con un Personal Access Token
   (classic o fine-grained) de una cuenta/bot con permiso de escritura en el repo, y añadir esa
   identidad a la lista de bypass de la protección de rama (ver siguiente sección). No requiere
   cambios en los workflows: el patrón `secrets.RELEASE_TOKEN || secrets.GITHUB_TOKEN` lo recoge
   automáticamente en cuanto exista.

## Permisos del `GITHUB_TOKEN`

Cada workflow declara únicamente lo que necesita (principio de mínimo privilegio):

| Workflow | `contents` | `actions` | `pull-requests` | Motivo |
|---|---|---|---|---|
| `create-release.yml` | `write` | — | — | crear rama, comitear, tagear, hacer push |
| `release-tagging.yml` | `write` | `write` | — | comitear/tagear/push + disparar `release-sync.yml` (`gh workflow run` requiere `actions: write`) |
| `release-sync.yml` | `write` | — | `write` | push directo a `main` + `gh pr create`/`gh pr list` en caso de conflicto |

## Branch protection recomendada

El sistema necesita hacer **push directo** (sin PR) a `main` y a `release/**` en los caminos
felices. Si activas "Require a pull request before merging" en esas ramas sin excepciones, esos
pushes automáticos serán rechazados. Opciones, de más simple a más robusta:

1. **No exigir PR en `main`/`release/**`, solo status checks obligatorios.** Como en este flujo
   nada más que la automatización empuja directamente a esas ramas (los humanos trabajan en
   `feature/*`, `bugfix/*`, `hotfix/*` y siempre via PR hacia `release/*`), no hace falta forzar
   PR en el propio `main`/`release/*`. Recomendado: marcar como obligatorio el check de CI
   (`Continuous Integration / build`) para que un push directo roto quede señalizado igualmente.
2. **Exigir PR pero añadir la identidad de la automatización a "Allow specified actors to bypass
   required pull requests".** Disponible en repos de organización (puede requerir plan de pago).
   Si usas el `GITHUB_TOKEN` por defecto, la identidad a añadir es la app `github-actions`; si usas
   `RELEASE_TOKEN`, la cuenta dueña del PAT.
3. **Restringir quién puede pushear** ("Restrict who can push to matching branches") a un
   conjunto reducido de personas + la identidad de la automatización, en vez de depender de "Allow
   bypass". Disponibilidad también depende del plan/tipo de repo.

Además, se recomienda proteger los tags (**Settings → Tags → New rule**, patrón `v*`)
restringiendo quién puede crear/borrar tags que empiecen por `v`, ya que este sistema trata esos
tags como el registro autoritativo de releases.

## Instrucciones de instalación

1. Mergear esta rama a `main` (los 3 workflows y los 5 scripts deben existir en `main` para poder
   dispararse — `workflow_dispatch` resuelve el workflow por su copia en la rama por defecto, y
   toda rama `release/*` nueva hereda estos ficheros porque se crea a partir de `main`).
2. En **Settings → Actions → General → Workflow permissions**, seleccionar "Read and write
   permissions" (o, si se prefiere mantenerlo en solo lectura a nivel global, usar un
   `RELEASE_TOKEN` como se explica arriba).
3. (Opcional) Crear el secret `RELEASE_TOKEN` si la protección de ramas lo requiere.
4. Configurar branch protection en `main` y `release/**` según la sección anterior.
5. (Opcional) Configurar una regla de protección de tags para `v*`.
6. Probar el flujo completo:
   - Actions → "Create Release" → Run workflow (rama `main`, `bump: minor`, dejando `version` en
     blanco) → comprobar que se crea `release/0.1.x` con tag anotado `v0.1.0`.
   - Crear una rama `bugfix/algo` desde `release/0.1.x`, abrir PR contra `release/0.1.x`, mergear
     → comprobar que aparece el tag `v0.1.1` y que se dispara "Release Sync".
   - Comprobar en Actions → "Release Sync" que el run se completó y que `main` recibió el merge.

## Tests

Batería [Bats](https://bats-core.readthedocs.io/) en [`scripts/tests/`](../../scripts/tests/),
organizada 1:1 con los scripts (dentro de `scripts/` para mantener todo lo relacionado con el
sistema de releases junto):

```
scripts/
  utils.sh
  version.sh
  create-release.sh
  create-tag.sh
  merge-main.sh
  tests/
    test_helper.bash       # setup/teardown compartidos
    version.bats
    utils.bats
    create-release.bats
    create-tag.bats
    merge-main.bats
    fixtures/
      bin/gh                # mock de gh (registra invocaciones en $MOCK_GH_LOG)
      pom.xml.template       # POM minimal sin <parent>, con versions-maven-plugin pinneado
```

Cada test crea su propio repositorio Git (con un remoto `origin` bare local) bajo el directorio
temporal del sistema, con el `mvnw`/`.mvn` reales del proyecto y el `pom.xml` fixture — nunca toca
el repositorio real ni depende de GitHub. Los scripts bajo prueba se invocan por ruta absoluta
desde el propio `scripts/`, no se copian, así que se testea el código de producción tal cual. Solo
`gh` está mockeado; Maven se ejecuta de verdad (ver la decisión de diseño correspondiente más
arriba). `teardown()` borra siempre el repositorio temporal.

**Ejecutar en local:**

```bash
brew install bats-core   # macOS
# o: sudo apt-get install -y bats   (Debian/Ubuntu)

chmod +x mvnw scripts/*.sh scripts/tests/fixtures/bin/*
bats scripts/tests/
```

Requiere Java 21 disponible (como el resto del proyecto) porque los scripts invocan `mvn
versions:set` de verdad. La primera vez que se ejecuta en una máquina sin caché de Maven, el
wrapper (`mvnw`) descarga la distribución de Maven — necesita red esa primera vez; las siguientes
ejecuciones usan la caché local (`~/.m2/wrapper`) y no dependen de Internet.

**Ejecutar en GitHub Actions:** dos jobs en [`ci.yml`](../../.github/workflows/ci.yml), en el
mismo trigger que el resto del CI (push y `pull_request` a las ramas relevantes), independientes
del job `build` existente:

- `shellcheck`: instala ShellCheck y lo corre contra `scripts/*.sh`, `scripts/tests/test_helper.bash`
  y `scripts/tests/fixtures/bin/gh`.
- `test-scripts`: configura Java 21 (con caché de Maven) e instala Bats, luego corre
  `bats scripts/tests/`.

Ambos se ejecutan automáticamente en cada Pull Request, así que cualquier cambio futuro en los
scripts de release queda validado antes de poder mergear.

## Limitaciones conocidas

- **Una sola línea de release "activa" a la vez.** Si se mantienen simultáneamente varias ramas
  `release/*` (soporte a largo plazo de versiones antiguas), Release Sync sincronizará cualquiera
  de ellas directamente a `main` sin noción de "cuál es la más reciente" — podría hacer retroceder
  la versión de `main` si una rama de release antigua tagea después de que una más nueva ya se
  haya sincronizado. Este sistema asume progresión lineal de una única línea activa, tal como se
  especificó; mantener varias líneas en paralelo requeriría lógica adicional no solicitada.
- **Sin idempotencia total ante fallos parciales.** El push de rama+tag es atómico dentro de cada
  script, pero si el workflow falla *entre* el commit del bump de versión y el tag (por ejemplo, un
  fallo de `mvn versions:set` tras haber pasado las comprobaciones), un reintento puede encontrar
  "nada que comitear" porque el pom ya está en la versión objetivo. Recuperación manual: revisar
  el estado de la rama y, si hace falta, tagear manualmente o revertir el commit incompleto.
