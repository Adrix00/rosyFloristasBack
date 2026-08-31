package com.floristeriarosy.application.attribute.command;

import java.util.UUID;

/**
 * @param id the attribute definition to update
 * @param label the new label; {@code attributeKey} and {@code dataType} cannot change
 *     (product.md, section 3.5)
 * @param filterable the new filterable flag
 * @param position the new position
 */
public record UpdateAttributeDefinitionCommand(UUID id, String label, boolean filterable, int position) {}
