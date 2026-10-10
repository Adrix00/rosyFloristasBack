package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param deliveryDate the requested pickup date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 */
public record PickupRequest(
    @NotNull @Future LocalDate deliveryDate, @NotNull LocalTime slotFrom, @NotNull LocalTime slotTo) {}
