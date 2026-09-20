package com.floristeriarosy.infrastructure.web.mapper.order;

import com.floristeriarosy.application.order.command.CreateShippingRateCommand;
import com.floristeriarosy.application.order.command.UpdateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.dto.ShippingBandsDto;
import com.floristeriarosy.infrastructure.web.request.order.CreateShippingRateRequest;
import com.floristeriarosy.infrastructure.web.request.order.UpdateShippingRateRequest;
import com.floristeriarosy.infrastructure.web.response.order.ShippingRateResponse;
import com.floristeriarosy.infrastructure.web.response.order.ShippingRatesResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates the shipping-rate Requests and Responses to and from Commands and DTOs. Pure 1:1 field
 * mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the Controller's
 * own entry/exit log.
 */
@Component
public class ShippingRateWebMapper {

  /**
   * @param adminUserId the authenticated administrator, for the audit log
   * @param request the create request
   * @return the command for {@code CreateShippingRateUseCase}
   */
  public CreateShippingRateCommand toCommand(UUID adminUserId, CreateShippingRateRequest request) {
    return new CreateShippingRateCommand(
        adminUserId,
        request.minKm(),
        request.maxKm(),
        request.price(),
        request.freeFromAmount(),
        request.active());
  }

  /**
   * @param id the band to change, from the path
   * @param adminUserId the authenticated administrator, for the audit log
   * @param request the new field values
   * @return the command for {@code UpdateShippingRateUseCase}
   */
  public UpdateShippingRateCommand toCommand(
      UUID id, UUID adminUserId, UpdateShippingRateRequest request) {
    return new UpdateShippingRateCommand(
        id,
        adminUserId,
        request.minKm(),
        request.maxKm(),
        request.price(),
        request.freeFromAmount(),
        request.active());
  }

  /**
   * @param dto one band
   * @return its API representation
   */
  public ShippingRateResponse toResponse(ShippingBandDto dto) {
    return new ShippingRateResponse(
        dto.id(), dto.minKm(), dto.maxKm(), dto.price(), dto.freeFromAmount(), dto.active());
  }

  /**
   * @param dto the whole tariff
   * @return its API representation
   */
  public ShippingRatesResponse toResponse(ShippingBandsDto dto) {
    return new ShippingRatesResponse(
        dto.bands().stream().map(this::toResponse).toList(), dto.deliveryRadiusKm());
  }
}
