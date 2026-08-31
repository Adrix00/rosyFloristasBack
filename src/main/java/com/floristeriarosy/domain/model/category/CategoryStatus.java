package com.floristeriarosy.domain.model.category;

/** Reversible in both directions; deactivating a category never touches its products (§3.2). */
public enum CategoryStatus {
  /** Navigable: lists, is searchable, opens by URL, may be suggested. */
  ACTIVE,
  /** Hidden from the storefront; a carted product already added still checks out. */
  INACTIVE
}
