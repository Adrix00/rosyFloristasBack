# Graph Report - .  (2026-08-15)

## Corpus Check
- Corpus is ~15,427 words - fits in a single context window. You may not need a graph.

## Summary
- 140 nodes · 218 edges · 11 communities (8 shown, 3 thin omitted)
- Extraction: 88% EXTRACTED · 11% INFERRED · 1% AMBIGUOUS · INFERRED: 25 edges (avg confidence: 0.77)
- Token cost: 136,357 input · 0 output

## Community Hubs (Navigation)
- Graphify Skill Commands
- DDD/Hexagonal Architecture Rules
- Release & Merge Scripts
- Version Parsing Utilities
- Graphify Query & Export Tools
- Maven Wrapper Script
- GitHub Release Workflows
- Spring Boot Test Bootstrap
- Spring Boot App Entry Point
- Maven Parent POM Notes
- Maven Package Root

## God Nodes (most connected - your core abstractions)
1. `graphify SKILL.md` - 20 edges
2. `Architecture Handbook (docs/ARCHITECTURE.md)` - 14 edges
3. `CLAUDE.md Project Instructions` - 12 edges
4. `main()` - 10 edges
5. `main()` - 10 edges
6. `fail()` - 9 edges
7. `main()` - 8 edges
8. `version.sh script` - 8 edges
9. `query.md Reference` - 8 edges
10. `log_info()` - 6 edges

## Surprising Connections (you probably didn't know these)
- `11-Step Development Workflow Order` --semantically_similar_to--> `Use Case First Pattern`  [INFERRED] [semantically similar]
  CLAUDE.md → docs/ARCHITECTURE.md
- `Roadmap` --references--> `CI Workflow (ci.yml)`  [AMBIGUOUS]
  docs/ARCHITECTURE.md → .github/workflows/ci.yml
- `CLAUDE.md Project Instructions` --references--> `Architecture Handbook (docs/ARCHITECTURE.md)`  [AMBIGUOUS]
  CLAUDE.md → docs/ARCHITECTURE.md
- `DDD + Hexagonal Architecture Mandate` --conceptually_related_to--> `Architecture Handbook (docs/ARCHITECTURE.md)`  [INFERRED]
  CLAUDE.md → docs/ARCHITECTURE.md
- `ADR-001-use-case-first.md` --conceptually_related_to--> `Use Case First Pattern`  [INFERRED]
  CLAUDE.md → docs/ARCHITECTURE.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Release Management Pipeline** — github_workflows_create_release_doc, github_workflows_release_sync_doc, github_workflows_release_tagging_doc [INFERRED 0.85]
- **graphify Auto-Rebuild Trigger Mechanisms** — claude_skills_graphify_references_update_update_flow, claude_skills_graphify_references_hooks_post_commit_hook, claude_skills_graphify_references_add_watch_watch_command [INFERRED 0.85]
- **Use-Case-First + Capability-Ports Architecture Pattern** — claude_md_development_workflow_order, docs_architecture_use_case_first, docs_architecture_ports [INFERRED 0.75]

## Communities (11 total, 3 thin omitted)

### Community 0 - "Graphify Skill Commands"
Cohesion: 0.09
Nodes (33): graphify Section in CLAUDE.md, graphify add Command, add-watch.md Reference, needs_update Flag, --watch Background Watcher, Wiki Export (--wiki), Confidence Score Rubric, extraction-spec.md Reference (+25 more)

### Community 1 - "DDD/Hexagonal Architecture Rules"
Cohesion: 0.10
Nodes (27): ADR-001-use-case-first.md, ADR-002-jpa-and-jdbc.md, ADR-003-capability-based-ports.md, ADR-004-reference-module-category.md, Coding Style Rules, DDD + Hexagonal Architecture Mandate, 11-Step Development Workflow Order, CLAUDE.md Project Instructions (+19 more)

### Community 2 - "Release & Merge Scripts"
Cohesion: 0.19
Nodes (17): main(), create-release.sh script, main(), create-tag.sh script, main(), open_sync_pull_request(), merge-main.sh script, commit_pom_version_bump() (+9 more)

### Community 3 - "Version Parsing Utilities"
Cohesion: 0.25
Nodes (13): fail(), SEMVER_REGEX, version.sh script, version_last_tag_for_branch(), version_major(), version_minor(), version_next_patch(), version_next_release() (+5 more)

### Community 4 - "Graphify Query & Export Tools"
Cohesion: 0.16
Nodes (14): Token Reduction Benchmark, exports.md Reference, FalkorDB Export, MCP stdio Server (--mcp), Neo4j Export, BFS/DFS Traversal Modes, query.md Reference, graphify explain Command (+6 more)

### Community 5 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 6 - "GitHub Release Workflows"
Cohesion: 0.33
Nodes (6): Create Release Workflow, scripts/create-release.sh, Release Sync Workflow, scripts/merge-main.sh, Release Tagging Workflow, scripts/create-tag.sh

### Community 7 - "Spring Boot Test Bootstrap"
Cohesion: 0.60
Nodes (3): org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, AppApplicationTests

## Ambiguous Edges - Review These
- `CI Workflow (ci.yml)` → `Roadmap`  [AMBIGUOUS]
  docs/ARCHITECTURE.md · relation: references
- `CLAUDE.md Project Instructions` → `Architecture Handbook (docs/ARCHITECTURE.md)`  [AMBIGUOUS]
  CLAUDE.md · relation: references

## Knowledge Gaps
- **15 isolated node(s):** `com.floristeriarosy:rosy-floristas-back`, `utils.sh script`, `SEMVER_REGEX`, `GRAPH_REPORT.md Output`, `Token Reduction Benchmark` (+10 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `CI Workflow (ci.yml)` and `Roadmap`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `CLAUDE.md Project Instructions` and `Architecture Handbook (docs/ARCHITECTURE.md)`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `graphify SKILL.md` connect `Graphify Skill Commands` to `Graphify Query & Export Tools`?**
  _High betweenness centrality (0.207) - this node is a cross-community bridge._
- **Why does `CLAUDE.md Project Instructions` connect `DDD/Hexagonal Architecture Rules` to `Graphify Skill Commands`?**
  _High betweenness centrality (0.148) - this node is a cross-community bridge._
- **What connects `com.floristeriarosy:rosy-floristas-back`, `utils.sh script`, `SEMVER_REGEX` to the rest of the system?**
  _15 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Graphify Skill Commands` be split into smaller, more focused modules?**
  _Cohesion score 0.08901515151515152 - nodes in this community are weakly interconnected._
- **Should `DDD/Hexagonal Architecture Rules` be split into smaller, more focused modules?**
  _Cohesion score 0.10317460317460317 - nodes in this community are weakly interconnected._