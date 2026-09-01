package com.floristeriarosy.domain.model.product;

/** Sale state of a product (product.md, section 3.2). */
public enum ProductStatus {
  /** For sale. */
  ACTIVE,
  /** Withdrawn temporarily: reversible back to {@code ACTIVE}. */
  INACTIVE,
  /** Permanent withdrawal: terminal, never reversible. */
  DISCONTINUED
}
