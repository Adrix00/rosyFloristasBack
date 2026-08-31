package com.floristeriarosy.domain.model.attribute;

/** Declared value type of a product attribute (product.md, section 3.5). */
public enum AttributeDataType {
  /** A free-text value, e.g. a color name. */
  TEXT,
  /** A numeric value, e.g. a height in centimeters. */
  NUMBER,
  /** A true/false value, e.g. whether a plant needs frequent watering. */
  BOOLEAN
}
