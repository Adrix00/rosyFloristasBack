package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.command.RegisterAdjustmentCommand;
import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.RegisterAdjustmentUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link RegisterAdjustmentUseCase}: orchestrates over {@link
 * RegisterStockMovementUseCase} for a manual correction (inventory.md, section 3.6).
 *
 * <p>{@code adminUserId} is always {@code null}: no {@code auth}/{@code admin} module exists yet to
 * resolve a principal from (known gap, dev-plan.md, pending {@code feature/auth}).
 */
@Service
public class RegisterAdjustmentService implements RegisterAdjustmentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterAdjustmentService.class);

  private final RegisterStockMovementUseCase registerStockMovementUseCase;

  /**
   * @param registerStockMovementUseCase the single write path this use case delegates to
   */
  public RegisterAdjustmentService(RegisterStockMovementUseCase registerStockMovementUseCase) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
  }

  /**
   * @param command the product, signed delta and required note
   * @return the recorded {@code ADJUSTMENT} movement
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public StockMovementDto execute(RegisterAdjustmentCommand command) {
    LOGGER.debug(
        "registerAdjustment productId={} quantity={}", command.productId(), command.quantity());

    RegisterStockMovementCommand delegateCommand =
        new RegisterStockMovementCommand(
            command.productId(),
            StockMovementType.ADJUSTMENT,
            command.quantity(),
            null,
            command.note());
    StockMovementDto result = registerStockMovementUseCase.execute(delegateCommand);

    LOGGER.debug(
        "registerAdjustment -> id={} resultingStock={}", result.id(), result.resultingStock());
    return result;
  }
}
