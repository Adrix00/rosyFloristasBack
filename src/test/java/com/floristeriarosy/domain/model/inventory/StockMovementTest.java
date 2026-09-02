package com.floristeriarosy.domain.model.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Sign rules per {@code StockMovementType} and the {@code resultingStock >= 0} invariant
 * (inventory.md, section 1, section 2: {@code chk_stock_movements_*_sign}, {@code
 * chk_stock_movements_resulting_stock}).
 */
class StockMovementTest {

  private StockMovementId id() {
    return StockMovementId.newId();
  }

  private ProductId productId() {
    return ProductId.newId();
  }

  @Test
  void createAcceptsAnInitialMovementWithZeroStartingStock() {
    StockMovement movement = StockMovement.create(id(), productId(), StockMovementType.INITIAL, 0, 0, null, null);

    assertThat(movement.quantity()).isZero();
    assertThat(movement.resultingStock()).isZero();
    assertThat(movement.createdAt()).isNull();
  }

  @Test
  void createRejectsANegativeInitialQuantity() {
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.INITIAL, -1, 0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAPurchaseWithNonPositiveQuantity() {
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.PURCHASE, 0, 5, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.PURCHASE, -3, 5, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsASaleWithNonNegativeQuantity() {
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.SALE, 0, 5, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.SALE, 3, 5, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAWasteWithNonNegativeQuantity() {
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.WASTE, 0, 5, null, "roto"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.WASTE, 2, 5, null, "roto"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAnAdjustmentWithZeroQuantity() {
    assertThatThrownBy(
            () -> StockMovement.create(id(), productId(), StockMovementType.ADJUSTMENT, 0, 5, null, "recuento"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsAnAdjustmentWithEitherSign() {
    StockMovement positive =
        StockMovement.create(id(), productId(), StockMovementType.ADJUSTMENT, 3, 8, null, "recuento");
    StockMovement negative =
        StockMovement.create(id(), productId(), StockMovementType.ADJUSTMENT, -3, 2, null, "recuento");

    assertThat(positive.quantity()).isEqualTo(3);
    assertThat(negative.quantity()).isEqualTo(-3);
  }

  @Test
  void createRejectsANegativeResultingStock() {
    assertThatThrownBy(() -> StockMovement.create(id(), productId(), StockMovementType.SALE, -3, -1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstitutePreservesThePersistedCreatedAt() {
    Instant createdAt = Instant.now();

    StockMovement movement =
        StockMovement.reconstitute(
            id(), productId(), StockMovementType.SALE, -2, 8, null, null, createdAt);

    assertThat(movement.createdAt()).isEqualTo(createdAt);
  }
}
