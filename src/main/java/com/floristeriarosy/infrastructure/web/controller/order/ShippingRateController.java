package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.port.in.CreateShippingRateUseCase;
import com.floristeriarosy.application.order.port.in.GetShippingRatesUseCase;
import com.floristeriarosy.application.order.port.in.UpdateShippingRateUseCase;
import com.floristeriarosy.infrastructure.security.jwt.CurrentAdminResolver;
import com.floristeriarosy.infrastructure.web.mapper.order.ShippingRateWebMapper;
import com.floristeriarosy.infrastructure.web.request.order.CreateShippingRateRequest;
import com.floristeriarosy.infrastructure.web.request.order.UpdateShippingRateRequest;
import com.floristeriarosy.infrastructure.web.response.order.ShippingRateResponse;
import com.floristeriarosy.infrastructure.web.response.order.ShippingRatesResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code /api/v1/shipping-rates} (order.md, section 4). All three are {@code ADMIN}. */
@RestController
@RequestMapping("/api/v1/shipping-rates")
public class ShippingRateController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingRateController.class);

  private final GetShippingRatesUseCase getShippingRatesUseCase;
  private final CreateShippingRateUseCase createShippingRateUseCase;
  private final UpdateShippingRateUseCase updateShippingRateUseCase;
  private final ShippingRateWebMapper mapper;

  /**
   * @param getShippingRatesUseCase backs {@code GET /shipping-rates}
   * @param createShippingRateUseCase backs {@code POST /shipping-rates}
   * @param updateShippingRateUseCase backs {@code PUT /shipping-rates/{id}}
   * @param mapper translates Request/Response to/from Command/Dto
   */
  public ShippingRateController(
      GetShippingRatesUseCase getShippingRatesUseCase,
      CreateShippingRateUseCase createShippingRateUseCase,
      UpdateShippingRateUseCase updateShippingRateUseCase,
      ShippingRateWebMapper mapper) {
    this.getShippingRatesUseCase = getShippingRatesUseCase;
    this.createShippingRateUseCase = createShippingRateUseCase;
    this.updateShippingRateUseCase = updateShippingRateUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /shipping-rates} ({@code ADMIN}, {@code @PreAuthorize} on the service).
   *
   * @return 200 with every band, active or not, and the current delivery radius
   */
  @GetMapping
  public ResponseEntity<ShippingRatesResponse> getAll() {
    LOGGER.debug("GET /shipping-rates");
    ShippingRatesResponse response = mapper.toResponse(getShippingRatesUseCase.execute());
    LOGGER.debug("GET /shipping-rates -> 200 bands={}", response.rates().size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /shipping-rates} ({@code ADMIN}, {@code @PreAuthorize} on the service).
   *
   * @param request the band to create
   * @return 201 with the created band
   */
  @PostMapping
  public ResponseEntity<ShippingRateResponse> create(@Valid @RequestBody CreateShippingRateRequest request) {
    LOGGER.debug("POST /shipping-rates");
    ShippingRateResponse response =
        mapper.toResponse(
            createShippingRateUseCase.execute(mapper.toCommand(CurrentAdminResolver.resolve(), request)));
    LOGGER.debug("POST /shipping-rates -> 201 id={}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code PUT /shipping-rates/{id}} ({@code ADMIN}, {@code @PreAuthorize} on the service).
   *
   * @param id the band to change
   * @param request the new field values, replacing every editable field
   * @return 200 with the updated band
   */
  @PutMapping("/{id}")
  public ResponseEntity<ShippingRateResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateShippingRateRequest request) {
    LOGGER.debug("PUT /shipping-rates/{}", id);
    ShippingRateResponse response =
        mapper.toResponse(
            updateShippingRateUseCase.execute(
                mapper.toCommand(id, CurrentAdminResolver.resolve(), request)));
    LOGGER.debug("PUT /shipping-rates/{} -> 200", id);
    return ResponseEntity.ok(response);
  }
}
