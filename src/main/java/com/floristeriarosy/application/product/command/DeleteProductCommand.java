package com.floristeriarosy.application.product.command;

import java.util.UUID;

/**
 * @param id the product to delete
 */
public record DeleteProductCommand(UUID id) {}
