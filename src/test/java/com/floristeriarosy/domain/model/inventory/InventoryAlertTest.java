package com.floristeriarosy.domain.model.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle rules of an alert (inventory.md, section 3.8; ADR-013): born {@code OPEN}, {@code
 * resolve}/{@code dismiss} are both terminal.
 */
class InventoryAlertTest {

  private InventoryAlert openAlert() {
    return InventoryAlert.open(InventoryAlertId.newId(), InventoryAlertType.LOW_STOCK, ProductId.newId(), 2, 5);
  }

  @Test
  void openCreatesAnAlertWithOpenStatusAndNoResolution() {
    InventoryAlert alert = openAlert();

    assertThat(alert.status()).isEqualTo(InventoryAlertStatus.OPEN);
    assertThat(alert.resolvedAt()).isNull();
    assertThat(alert.resolvedByAdminId()).isNull();
  }

  @Test
  void resolveClosesAnOpenAlertAsFixed() {
    InventoryAlert alert = openAlert();
    Instant now = Instant.now();
    UUID adminId = UUID.randomUUID();

    alert.resolve(adminId, "repuesto", now);

    assertThat(alert.status()).isEqualTo(InventoryAlertStatus.RESOLVED);
    assertThat(alert.resolvedAt()).isEqualTo(now);
    assertThat(alert.resolvedByAdminId()).isEqualTo(adminId);
    assertThat(alert.note()).isEqualTo("repuesto");
  }

  @Test
  void dismissClosesAnOpenAlertAsAcknowledged() {
    InventoryAlert alert = openAlert();
    Instant now = Instant.now();

    alert.dismiss(null, "umbral demasiado conservador", now);

    assertThat(alert.status()).isEqualTo(InventoryAlertStatus.DISMISSED);
    assertThat(alert.resolvedAt()).isEqualTo(now);
  }

  @Test
  void resolvingAnAlreadyResolvedAlertThrows() {
    InventoryAlert alert = openAlert();
    alert.resolve(null, null, Instant.now());

    assertThatThrownBy(() -> alert.resolve(null, null, Instant.now()))
        .isInstanceOf(InventoryAlertNotOpenException.class);
  }

  @Test
  void dismissingAnAlreadyDismissedAlertThrows() {
    InventoryAlert alert = openAlert();
    alert.dismiss(null, null, Instant.now());

    assertThatThrownBy(() -> alert.dismiss(null, null, Instant.now()))
        .isInstanceOf(InventoryAlertNotOpenException.class);
  }

  @Test
  void resolvingAnAlreadyDismissedAlertThrows() {
    InventoryAlert alert = openAlert();
    alert.dismiss(null, null, Instant.now());

    assertThatThrownBy(() -> alert.resolve(null, null, Instant.now()))
        .isInstanceOf(InventoryAlertNotOpenException.class);
  }
}
