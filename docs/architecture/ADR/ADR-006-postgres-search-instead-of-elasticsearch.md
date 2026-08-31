# ADR-006: PostgreSQL Search Instead of Elasticsearch

- **Status:** Accepted
- **Date:** 2026-08-16
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

The catalog needs a reactive search bar: suggestions appear as the user types, tolerant of missing
accents and small typos, over a Spanish-language catalog of a few thousand products at most.

Elasticsearch/OpenSearch is the default reach for this kind of feature, but it is a second stateful
service to deploy, operate and keep in sync with the catalog — a new failure mode (index drifts from
the source of truth) for a scale where it buys nothing PostgreSQL cannot already do in milliseconds.

---

# Decision

Search stays inside PostgreSQL, behind a `ProductSearchPort` (capability-based output port, per
ADR-003), so that replacing the adapter later — if the catalog ever grows enough to justify it — does
not touch application or domain code.

## Two distinct mechanisms, not one

**Full-text** — `products.search_vector`, a `tsvector` generated column:

```sql
search_vector tsvector GENERATED ALWAYS AS (to_tsvector('spanish', coalesce(search_text, ''))) STORED
```

Queried with `plainto_tsquery('spanish', ...)` or `to_tsquery('spanish', ...)`. This does **not**
match prefixes: `plainto_tsquery('spanish', 'ros')` will not find "rosas". It is the right tool for
"search across the whole description", not for "autocomplete as you type".

**Autocomplete, prefixes and typos** — `products.search_text`, a plain `TEXT` column indexed with
`pg_trgm`:

```sql
CREATE INDEX ix_products_search_text_trgm ON products USING gin (search_text gin_trgm_ops);
```

Queried with `search_text LIKE 'ros%'` for prefixes, or `similarity(search_text, ?)` for
typo-tolerance. This is the mechanism behind the reactive search bar's suggestions.

Both are populated by the persistence adapter, not computed by triggers: `search_text` is nome +
description + the text values of `attributes` (JSONB), normalized in Java
(`java.text.Normalizer`, NFD form, diacritics stripped, lower-cased) before being written. The
incoming query string is normalized with the same routine before it reaches SQL, so an accent-free
query matches an accented product name.

## Why not `unaccent()` in the generated column

The obvious-looking alternative —

```sql
search_vector tsvector GENERATED ALWAYS AS (to_tsvector('spanish', unaccent(coalesce(search_text, '')))) STORED
```

— does not compile on PostgreSQL 16. `unaccent()` is declared `STABLE`, not `IMMUTABLE`, and a
generated column's expression is required to be immutable. The usual workaround — a custom text
search configuration (e.g. `es_unaccent`) built on `unaccent`, then calling
`to_tsvector('es_unaccent', ...)` — does work, because the two-argument form of `to_tsvector` is
immutable regardless of which configuration it names. It was rejected anyway for this phase: it
requires `CREATE EXTENSION unaccent`, which in turn requires a privileged role, and it complicates
`pg_dump`/`restore` ordering (the configuration object must exist before the table that depends on
it). Normalizing in Java sidesteps both problems and needs only `pg_trgm`.

`to_tsvector('spanish', text)` — the two-argument form actually used — is immutable: the
single-argument form (`to_tsvector(text)`) is the one that is stable, because it silently reads
`default_text_search_config`.

---

# Consequences

- Zero new services to deploy, monitor or keep in sync. Search data lives in the same transaction as
  the product it describes — it cannot drift.
- Diacritics are handled in Java, not in PostgreSQL: the normalization routine is a small piece of
  application code that must be kept consistent between indexing and querying.
- `pg_trgm` must exist before `V1__initial_schema.sql` runs, or the user running the migration must
  have privilege to create it (see the extension permissions note in
  `docs/database/README.md`).
- If the catalog grows to a scale where relevance tuning, synonyms or multi-language ranking become
  necessary — a threshold in the tens of thousands of products, not the low thousands expected at
  launch — `ProductSearchPort` is the seam to swap in Elasticsearch/OpenSearch without touching
  callers.

---

# Alternatives considered

**Elasticsearch/OpenSearch from the start.** Rejected for this phase: introduces a second stateful
service, an indexing pipeline that must stay synchronized with the catalog, and a new class of
failure (index out of date) — none of which this catalog's size requires yet.

**`unaccent` + custom search configuration.** Technically valid (see above) but deferred: it works,
but costs elevated privileges and dump/restore complexity that normalizing in Java avoids entirely
for the same practical result.

**Only `pg_trgm`, no `tsvector`.** Rejected: trigram similarity alone is good at prefixes and typos
but weak at full-text relevance across a longer description. Keeping both, each queried for what it
is good at, is a few lines of SQL, not an architectural cost.
