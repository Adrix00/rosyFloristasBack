package com.floristeriarosy.application.discount.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.discount.command.DeleteDiscountCommand;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountAlreadyStartedException;
import com.floristeriarosy.domain.exception.discount.DiscountNotFoundException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteDiscountServiceTest {

  @Mock private DiscountReadPort readPort;
  @Mock private DiscountWritePort writePort;

  private DeleteDiscountService service;

  private Discount newDiscount(DiscountId id, Instant startsAt, Instant endsAt) {
    return Discount.create(
        id, ProductId.newId(), new BigDecimal("20.00"), new BigDecimal("15.00"), startsAt, endsAt, null);
  }

  @Test
  void deletesAScheduledDiscount() {
    service = new DeleteDiscountService(readPort, writePort);
    Instant now = Instant.now();
    DiscountId id = DiscountId.newId();
    Discount discount = newDiscount(id, now.plusSeconds(3600), now.plusSeconds(7200));
    when(readPort.findById(id)).thenReturn(Optional.of(discount));

    service.execute(new DeleteDiscountCommand(id.value()));

    verify(writePort).delete(id);
  }

  @Test
  void throwsDiscountNotFoundWhenTheDiscountDoesNotExist() {
    service = new DeleteDiscountService(readPort, writePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(DiscountId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new DeleteDiscountCommand(id)))
        .isInstanceOf(DiscountNotFoundException.class);
    verify(writePort, never()).delete(any());
  }

  @Test
  void throwsDiscountAlreadyStartedWithoutDeletingWhenTheDiscountHasAlreadyStarted() {
    service = new DeleteDiscountService(readPort, writePort);
    Instant now = Instant.now();
    DiscountId id = DiscountId.newId();
    Discount discount = newDiscount(id, now.minusSeconds(3600), now.plusSeconds(3600));
    when(readPort.findById(id)).thenReturn(Optional.of(discount));

    assertThatThrownBy(() -> service.execute(new DeleteDiscountCommand(id.value())))
        .isInstanceOf(DiscountAlreadyStartedException.class);
    verify(writePort, never()).delete(any());
  }
}
