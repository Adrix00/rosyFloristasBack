-- V17__cart_optimistic_locking.sql
-- ADR-009 amendment (2026-09-06): carts was left off the original @Version list by omission, not
-- by a documented exclusion. A customer's own cart is reachable from two devices at once; without
-- version, one device's line additions can be silently discarded by the other's last write.
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
