package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.CreateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.mapper.ShippingBandDtoMapper;
import com.floristeriarosy.application.order.port.in.CreateShippingRateUseCase;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.ShippingBands;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CreateShippingRateUseCase}: locks every band (so two administrators editing at
 * once are serialized), adds the new one to the in-memory set, checks the active bands are still one
 * coherent tariff (order.md §3.7), then saves and audits (ADR-010) — all in one transaction.
 */
@Service
@Transactional
public class CreateShippingRateService implements CreateShippingRateUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateShippingRateService.class);
  private static final String ENTITY_TYPE = "shipping_rate";
  private static final List<String> ALL_FIELDS =
      List.of("min_km", "max_km", "price", "free_from_amount", "active");

  private final ShippingRatePort shippingRatePort;
  private final AuditLogPort auditLogPort;

  /**
   * @param shippingRatePort locks and saves the bands
   * @param auditLogPort records the creation (ADR-010)
   */
  public CreateShippingRateService(ShippingRatePort shippingRatePort, AuditLogPort auditLogPort) {
    this.shippingRatePort = shippingRatePort;
    this.auditLogPort = auditLogPort;
  }

  /**
   * @param command the band to create and who is creating it
   * @return the created band
   * @throws ShippingRatesInvalidException the band, or the resulting set, breaks a rule
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ShippingBandDto execute(CreateShippingRateCommand command) {
    LOGGER.debug(
        "createShippingRate adminUserId={} minKm={} maxKm={} price={} freeFromAmount={} active={}",
        command.adminUserId(),
        command.minKm(),
        command.maxKm(),
        command.price(),
        command.freeFromAmount(),
        command.active());

    List<ShippingBand> resulting = new ArrayList<>(shippingRatePort.findAllForUpdate());
    ShippingBand created =
        ShippingBand.create(
            ShippingBandId.newId(),
            command.minKm(),
            command.maxKm(),
            command.price(),
            command.freeFromAmount(),
            command.active());
    resulting.add(created);
    new ShippingBands(resulting).requireCoherent();

    ShippingBand saved = shippingRatePort.save(created);
    auditLogPort.record(
        command.adminUserId(), AuditAction.CREATE, ENTITY_TYPE, saved.id().value(), ALL_FIELDS);

    LOGGER.debug("createShippingRate -> id={}", saved.id());
    return ShippingBandDtoMapper.toDto(saved);
  }
}
