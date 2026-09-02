-- V16__inventory_alerts_note.sql
-- inventory.md, section 5: ResolveAlertRequest/DismissAlertRequest carry an optional note, closing
-- context for whoever reviews the alert history later. V7 did not add a column for it — same style
-- as stock_movements.note (nullable, no business meaning encoded, never PII: ADR-005 does not apply).
ALTER TABLE inventory_alerts ADD COLUMN note VARCHAR(500);
