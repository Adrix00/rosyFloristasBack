package com.floristeriarosy.application.customer.command;

/**
 * @param email the buyer's email from the checkout (customer.md, rule 3.2)
 * @param firstName the buyer's first name
 * @param lastName the buyer's last name
 * @param phone the buyer's phone
 */
public record FindOrCreateGuestCustomerCommand(String email, String firstName, String lastName, String phone) {}
