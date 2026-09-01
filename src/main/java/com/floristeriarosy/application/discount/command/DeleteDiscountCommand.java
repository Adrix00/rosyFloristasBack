package com.floristeriarosy.application.discount.command;

import java.util.UUID;

/**
 * @param id the discount to delete
 */
public record DeleteDiscountCommand(UUID id) {}
