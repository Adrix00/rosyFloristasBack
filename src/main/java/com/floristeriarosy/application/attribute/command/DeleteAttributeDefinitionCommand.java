package com.floristeriarosy.application.attribute.command;

import java.util.UUID;

/**
 * @param id the attribute definition to delete
 */
public record DeleteAttributeDefinitionCommand(UUID id) {}
