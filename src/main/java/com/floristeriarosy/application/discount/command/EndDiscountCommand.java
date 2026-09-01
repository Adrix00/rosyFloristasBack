package com.floristeriarosy.application.discount.command;

import java.util.UUID;

/**
 * @param id the discount to close now
 */
public record EndDiscountCommand(UUID id) {}
