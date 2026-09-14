package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param firstName the buyer's first name
 * @param lastName the buyer's last name
 * @param email the buyer's email
 * @param phone the buyer's phone
 */
public record BuyerRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank @Email String email,
    @NotBlank String phone) {}
