package com.floristeriarosy.infrastructure.web.request.inventory;

import com.floristeriarosy.shared.validation.NonZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code quantity = 0} is rejected here by {@link NonZero} so the client gets a clean 422 instead
 * of the domain's {@code StockMovement} constructor throwing before ever reaching {@code
 * chk_stock_movements_quantity_nonzero}, the DB constraint that otherwise never gets a chance to
 * fire (inventory.md, section 5).
 *
 * @param quantity required, non-zero; positive or negative, whichever the correction needs
 * @param note required explanation
 */
public record RegisterAdjustmentRequest(
    @NotNull @NonZero Integer quantity, @NotBlank @Size(max = 500) String note) {}
