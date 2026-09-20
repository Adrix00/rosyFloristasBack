package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.UpdateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.mapper.ShippingBandDtoMapper;
import com.floristeriarosy.application.order.port.in.UpdateShippingRateUseCase;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.order.ShippingRateNotFoundException;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.ShippingBands;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link UpdateShippingRateUseCase}: locks every band, applies the change to the
 * in-memory set, checks the active bands are still one coherent tariff (order.md §3.7), then saves
 * and audits (ADR-010) — all in one transaction, so a rejected change leaves nothing behind. Existing
 * orders are untouched: each stores its own {@code shipping_cost}.
 */
@Service
@Transactional
public class UpdateShippingRateService implements UpdateShippingRateUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateShippingRateService.class);
  private static final String ENTITY_TYPE = "shipping_rate";

  private final ShippingRatePort shippingRatePort;
  private final AuditLogPort auditLogPort;

  /**
   * @param shippingRatePort locks and saves the bands
   * @param auditLogPort records the change (ADR-010)
   */
  public UpdateShippingRateService(ShippingRatePort shippingRatePort, AuditLogPort auditLogPort) {
    this.shippingRatePort = shippingRatePort;
    this.auditLogPort = auditLogPort;
  }

  /**
   * @param command the band to change, its new fields and who is changing it
   * @return the updated band
   * @throws ShippingRateNotFoundException the band does not exist
   * @throws ShippingRatesInvalidException the band, or the resulting set, breaks a rule
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ShippingBandDto execute(UpdateShippingRateCommand command) {
    LOGGER.debug(
        "updateShippingRate id={} adminUserId={} minKm={} maxKm={} price={} freeFromAmount={} active={}",
        command.id(),
        command.adminUserId(),
        command.minKm(),
        command.maxKm(),
        command.price(),
        command.freeFromAmount(),
        command.active());

    List<ShippingBand> bands = shippingRatePort.findAllUnderTariffLock();
    ShippingBand band =
        bands.stream()
            .filter(candidate -> candidate.id().value().equals(command.id()))
            .findFirst()
            .orElseThrow(
                () -> new ShippingRateNotFoundException("Shipping band " + command.id() + " not found"));

    List<String> changedFields =
        band.replaceWith(
            command.minKm(), command.maxKm(), command.price(), command.freeFromAmount(), command.active());

    ShippingBand result = band;
    if (!changedFields.isEmpty()) {
      new ShippingBands(bands).requireCoherent();
      result = shippingRatePort.save(band);
      auditLogPort.record(
          command.adminUserId(), AuditAction.UPDATE, ENTITY_TYPE, result.id().value(), changedFields);
    }

    LOGGER.debug("updateShippingRate -> id={} changed={}", result.id(), changedFields);
    return ShippingBandDtoMapper.toDto(result);
  }
}
