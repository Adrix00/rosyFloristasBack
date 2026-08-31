package com.floristeriarosy.application.attribute.command;

import com.floristeriarosy.domain.model.attribute.AttributeDataType;

/**
 * @param attributeKey the key to declare; immutable once created
 * @param label the visible label
 * @param dataType the value type every product must respect for this key
 * @param filterable whether {@code GET /products} may filter by this key
 * @param position position in the admin's attribute list
 */
public record CreateAttributeDefinitionCommand(
    String attributeKey, String label, AttributeDataType dataType, boolean filterable, int position) {}
