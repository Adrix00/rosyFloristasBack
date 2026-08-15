# Graph Report - rosyFloristasBack  (2026-08-15)

## Corpus Check
- 87 files · ~29,303 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 489 nodes · 646 edges · 48 communities (29 shown, 19 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 16 edges (avg confidence: 0.71)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d735ea00`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- graphify SKILL.md
- CLAUDE.md Project Instructions
- utils.sh
- org.springframework.stereotype.Service
- query.md Reference
- mvnw
- Release Tagging Workflow
- AppApplicationTests.java
- AppApplication
- HELP.md
- com.floristeriarosy:rosy-floristas-back
- ADR-004-reference-module-category.md
- Category
- ADR-002-jpa-and-jdbc.md
- ADR-003-capability-based-ports.md
- ADR-001-use-case-first.md
- CategoryController.java
- 00-project-principles.md
- com.tngtech.archunit.junit.AnalyzeClasses
- Gestión de versiones y releases
- test_helper.bash
- extraction-spec.md Reference
- --watch Background Watcher
- Naming Conventions
- exports.md Reference
- graph.json Output
- Architecture Overview
- Package Conventions
- REST Conventions
- Transaction Conventions
- CategoryStatus
- CategoryAlreadyExistsException
- CategoryInUseException
- CategoryNotFoundException
- 08-domain-events.md
- 09-archunit-rules.md
- 10-development-workflow.md
- README.md
- gh
- CategoryEntity.java
- CategoryProjection.java
- CategoryJdbcRepository.java
- CategoryRowMapper.java
- CategoryJpaRepository.java
- CategoryPersistenceMapper.java
- CategoryWebMapper.java

## God Nodes (most connected - your core abstractions)
1. `graphify SKILL.md` - 20 edges
2. `Gestión de versiones y releases` - 15 edges
3. `main()` - 13 edges
4. `main()` - 13 edges
5. `Category` - 13 edges
6. `Development Order` - 12 edges
7. `CLAUDE.md Project Instructions` - 11 edges
8. `main()` - 10 edges
9. `fail()` - 10 edges
10. `version.sh script` - 10 edges

## Surprising Connections (you probably didn't know these)
- `graphify Section in CLAUDE.md` --references--> `Native CLAUDE.md Integration`  [INFERRED]
  CLAUDE.md → .claude/skills/graphify/references/hooks.md
- `Post-Commit Auto-Rebuild Hook` --semantically_similar_to--> `--watch Background Watcher`  [INFERRED] [semantically similar]
  .claude/skills/graphify/references/hooks.md → .claude/skills/graphify/references/add-watch.md
- `build_merge Replace-on-Re-extract` --semantically_similar_to--> `Graph Shrink Guard (#479)`  [INFERRED] [semantically similar]
  .claude/skills/graphify/references/update.md → .claude/skills/graphify/SKILL.md
- `graphify clone Command` --references--> `graphify SKILL.md`  [EXTRACTED]
  .claude/skills/graphify/references/github-and-merge.md → .claude/skills/graphify/SKILL.md
- `Semantic Extraction Cache` --shares_data_with--> `extraction-spec.md Reference`  [EXTRACTED]
  .claude/skills/graphify/SKILL.md → .claude/skills/graphify/references/extraction-spec.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **graphify Auto-Rebuild Trigger Mechanisms** — claude_skills_graphify_references_update_update_flow, claude_skills_graphify_references_hooks_post_commit_hook, claude_skills_graphify_references_add_watch_watch_command [INFERRED 0.85]
- **Release Management Pipeline** — github_workflows_create_release_doc, github_workflows_release_sync_doc, github_workflows_release_tagging_doc [INFERRED 0.85]

## Communities (48 total, 19 thin omitted)

### Community 0 - "graphify SKILL.md"
Cohesion: 0.27
Nodes (10): transcribe.md Reference, Whisper Domain-Hint Prompt, File Detection (Step 2), graphify SKILL.md, Semantic Extraction Cache, Graph Health Check, GRAPH_REPORT.md Output, Honesty Rules (+2 more)

### Community 1 - "CLAUDE.md Project Instructions"
Cohesion: 0.15
Nodes (13): ADR-001-use-case-first.md, ADR-002-jpa-and-jdbc.md, ADR-003-capability-based-ports.md, ADR-004-reference-module-category.md, Coding Style Rules, DDD + Hexagonal Architecture Mandate, 11-Step Development Workflow Order, CLAUDE.md Project Instructions (+5 more)

### Community 2 - "utils.sh"
Cohesion: 0.12
Nodes (35): main(), create-release.sh script, main(), create-tag.sh script, main(), open_sync_pull_request(), merge-main.sh script, commit_pom_version_bump() (+27 more)

### Community 3 - "org.springframework.stereotype.Service"
Cohesion: 0.09
Nodes (18): org.springframework.stereotype.Service, org.springframework.transaction.annotation.Transactional, ChangeCategoryStatusCommand, DeleteCategoryCommand, UpdateCategoryCommand, ChangeCategoryStatusUseCase, DeleteCategoryUseCase, GetCategoryByIdUseCase (+10 more)

### Community 4 - "query.md Reference"
Cohesion: 0.33
Nodes (7): BFS/DFS Traversal Modes, query.md Reference, graphify explain Command, graphify path Command, graphify reflect / LESSONS.md, save-result Work Memory, Query Fast Path

### Community 5 - "mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 6 - "Release Tagging Workflow"
Cohesion: 0.33
Nodes (6): Create Release Workflow, scripts/create-release.sh, Release Sync Workflow, scripts/merge-main.sh, Release Tagging Workflow, scripts/create-tag.sh

### Community 7 - "AppApplicationTests.java"
Cohesion: 0.60
Nodes (3): org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, AppApplicationTests

### Community 11 - "ADR-004-reference-module-category.md"
Cohesion: 0.06
Nodes (35): 10. Persistence, 11. Testing, 1. REST API, 2. Requests, 3. Responses, 4. Commands, 5. Queries, 6. Use Cases (+27 more)

### Community 12 - "Category"
Cohesion: 0.11
Nodes (16): com.floristeriarosy.application.category.port.out.CategoryExistencePort, com.floristeriarosy.application.category.port.out.CategoryReadPort, com.floristeriarosy.application.category.port.out.CategoryWritePort, org.springframework.stereotype.Repository, CreateCategoryCommand, CreateCategoryUseCase, GetCategoriesUseCase, GetCategoriesQuery (+8 more)

### Community 13 - "ADR-002-jpa-and-jdbc.md"
Cohesion: 0.06
Nodes (31): Adapter, ADR-002: Combined Persistence Strategy (JPA + JDBC), Application Mapper, Architectural Principle, ArchUnit Enforcement, Benefits, Consequences, Context (+23 more)

### Community 14 - "ADR-003-capability-based-ports.md"
Cohesion: 0.06
Nodes (29): ADR-003: Capability-Based Output Ports, ArchUnit Enforcement, Benefits, Better Readability, Better Testability, Communication Flow, Consequences, Context (+21 more)

### Community 15 - "ADR-001-use-case-first.md"
Cohesion: 0.07
Nodes (27): ADR-001: Use Case First Architecture, Architectural Constraints, ArchUnit Enforcement, Benefits, Better Readability, Business Logic in Controllers, Communication Flow, Consequences (+19 more)

### Community 16 - "CategoryController.java"
Cohesion: 0.14
Nodes (14): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.DeleteMapping, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PatchMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.PutMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController (+6 more)

### Community 17 - "00-project-principles.md"
Cohesion: 0.10
Nodes (19): 10. Mappers, 11. Validation, 12. Transactions, 13. REST, 14. Domain Events, 15. ArchUnit, 16. Testing, 17. Reference Implementation (+11 more)

### Community 18 - "com.tngtech.archunit.junit.AnalyzeClasses"
Cohesion: 0.29
Nodes (9): com.tngtech.archunit.junit.AnalyzeClasses, com.tngtech.archunit.lang.ArchRule, ApplicationArchitectureTest, DependencyArchitectureTest, DomainArchitectureTest, HexagonalArchitectureTest, InfrastructureArchitectureTest, NamingConventionArchitectureTest (+1 more)

### Community 19 - "Gestión de versiones y releases"
Cohesion: 0.12
Nodes (15): Branch protection recomendada, Configuración necesaria en GitHub, Decisiones de diseño y alternativas consideradas, Gestión de versiones y releases, Instrucciones de instalación, Limitaciones conocidas, Modo dry-run, Permisos del `GITHUB_TOKEN` (+7 more)

### Community 21 - "extraction-spec.md Reference"
Cohesion: 0.22
Nodes (9): graphify Section in CLAUDE.md, Confidence Score Rubric, extraction-spec.md Reference, Hyperedge Extraction Rule, Node ID Format Rules, hooks.md Reference, Native CLAUDE.md Integration, Post-Commit Auto-Rebuild Hook (+1 more)

### Community 22 - "--watch Background Watcher"
Cohesion: 0.28
Nodes (9): graphify add Command, add-watch.md Reference, needs_update Flag, --watch Background Watcher, build_merge Replace-on-Re-extract, --cluster-only Flow, update.md Reference, --update Incremental Flow (+1 more)

### Community 23 - "Naming Conventions"
Cohesion: 0.22
Nodes (8): Commands, Naming Conventions, Ports, Queries, Requests, Responses, Services, Use Cases

### Community 24 - "exports.md Reference"
Cohesion: 0.40
Nodes (6): Token Reduction Benchmark, exports.md Reference, FalkorDB Export, Neo4j Export, Wiki Export (--wiki), Community Labeling

### Community 25 - "graph.json Output"
Cohesion: 0.33
Nodes (6): MCP stdio Server (--mcp), graphify clone Command, github-and-merge.md Reference, graphify merge-graphs Command, Constrained Query Expansion, graph.json Output

### Community 26 - "Architecture Overview"
Cohesion: 0.40
Nodes (4): Architecture, Architecture Overview, Main Layers, Main Principles

### Community 27 - "Package Conventions"
Cohesion: 0.40
Nodes (4): Application, Domain, Infrastructure, Package Conventions

### Community 28 - "REST Conventions"
Cohesion: 0.40
Nodes (4): API Versioning, HTTP Methods, Resource Naming, REST Conventions

### Community 29 - "Transaction Conventions"
Cohesion: 0.50
Nodes (3): Reading Use Cases, Transaction Conventions, Writing Use Cases

### Community 30 - "CategoryStatus"
Cohesion: 0.50
Nodes (3): CategoryStatus, ACTIVE, INACTIVE

## Knowledge Gaps
- **191 isolated node(s):** `com.floristeriarosy:rosy-floristas-back`, `test_helper.bash script`, `utils.sh script`, `SEMVER_REGEX`, `ACTIVE` (+186 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **19 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `graphify SKILL.md` connect `graphify SKILL.md` to `query.md Reference`, `extraction-spec.md Reference`, `--watch Background Watcher`, `exports.md Reference`, `graph.json Output`?**
  _High betweenness centrality (0.012) - this node is a cross-community bridge._
- **What connects `com.floristeriarosy:rosy-floristas-back`, `test_helper.bash script`, `utils.sh script` to the rest of the system?**
  _191 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `utils.sh` be split into smaller, more focused modules?**
  _Cohesion score 0.1173054587688734 - nodes in this community are weakly interconnected._
- **Should `org.springframework.stereotype.Service` be split into smaller, more focused modules?**
  _Cohesion score 0.08819345661450925 - nodes in this community are weakly interconnected._
- **Should `ADR-004-reference-module-category.md` be split into smaller, more focused modules?**
  _Cohesion score 0.05555555555555555 - nodes in this community are weakly interconnected._
- **Should `Category` be split into smaller, more focused modules?**
  _Cohesion score 0.10873440285204991 - nodes in this community are weakly interconnected._
- **Should `ADR-002-jpa-and-jdbc.md` be split into smaller, more focused modules?**
  _Cohesion score 0.0625 - nodes in this community are weakly interconnected._