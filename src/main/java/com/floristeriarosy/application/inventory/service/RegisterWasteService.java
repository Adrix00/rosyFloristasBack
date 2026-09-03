package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.command.RegisterWasteCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterWasteUseCase;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link RegisterWasteUseCase}: orchestrates over {@link RegisterStockMovementUseCase},
 * applying the negative sign {@code WASTE} always carries (inventory.md, section 3.5).
 *
 * <p>{@code adminUserId} is always {@code null}: no {@code auth}/{@code admin} module exists yet to
 * resolve a principal from (known gap, dev-plan.md, pending {@code feature/auth}).
 */
@Service
public class RegisterWasteService implements RegisterWasteUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterWasteService.class);

  private final RegisterStockMovementUseCase registerStockMovementUseCase;

  /**
   * @param registerStockMovementUseCase the single write path this use case delegates to
   */
  public RegisterWasteService(RegisterStockMovementUseCase registerStockMovementUseCase) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
  }

  /**
   * @param command the product, wasted quantity (positive) and required note
   * @return the recorded {@code WASTE} movement
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public StockMovementDto execute(RegisterWasteCommand command) {
    LOGGER.debug("registerWaste productId={} quantity={}", command.productId(), command.quantity());

    RegisterStockMovementCommand delegateCommand =
        new RegisterStockMovementCommand(
            command.productId(),
            StockMovementType.WASTE,
            -Math.abs(command.quantity()),
            null,
            command.note());
    StockMovementDto result = registerStockMovementUseCase.execute(delegateCommand);

    LOGGER.debug("registerWaste -> id={} resultingStock={}", result.id(), result.resultingStock());
    return result;
  }
}
