package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param recipientName who receives the delivery
 * @param recipientPhone the recipient's phone
 * @param street the street
 * @param number the street number
 * @param detail the address detail, optional
 * @param postalCode the postal code
 * @param deliveryDate the requested date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 * @param cardMessage the card message, optional
 */
public record DeliveryRequest(
    @NotBlank String recipientName,
    @NotBlank String recipientPhone,
    @NotBlank String street,
    @NotBlank String number,
    String detail,
    @NotBlank String postalCode,
    @NotNull @Future LocalDate deliveryDate,
    @NotNull LocalTime slotFrom,
    @NotNull LocalTime slotTo,
    @Size(max = 500) String cardMessage) {}
