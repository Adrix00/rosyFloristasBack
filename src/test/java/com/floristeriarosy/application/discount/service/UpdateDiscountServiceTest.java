package com.floristeriarosy.application.discount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.discount.command.UpdateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountNotEditableException;
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
class UpdateDiscountServiceTest {

  @Mock private DiscountReadPort readPort;
  @Mock private DiscountWritePort writePort;

  private UpdateDiscountService service;

  private Discount newDiscount(DiscountId id, Instant startsAt, Instant endsAt) {
    return Discount.create(
        id, ProductId.newId(), new BigDecimal("20.00"), new BigDecimal("15.00"), startsAt, endsAt, null);
  }

  @Test
  void updatesTheEditableFieldsOfAScheduledDiscount() {
    service = new UpdateDiscountService(readPort, writePort);
    Instant now = Instant.now();
    DiscountId id = DiscountId.newId();
    Discount discount = newDiscount(id, now.plusSeconds(3600), now.plusSeconds(7200));
    when(readPort.findById(id)).thenReturn(Optional.of(discount));
    when(writePort.save(discount)).thenReturn(discount);
    BigDecimal newSalePrice = new BigDecimal("12.00");

    DiscountDto dto =
        service.execute(new UpdateDiscountCommand(id.value(), null, null, null, newSalePrice));

    assertThat(dto.salePrice()).isEqualByComparingTo(newSalePrice);
  }

  @Test
  void throwsDiscountNotFoundWhenTheDiscountDoesNotExist() {
    service = new UpdateDiscountService(readPort, writePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(DiscountId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new UpdateDiscountCommand(id, null, null, null, null)))
        .isInstanceOf(DiscountNotFoundException.class);
    verify(writePort, never()).save(any());
  }

  @Test
  void propagatesDiscountNotEditableWithoutSavingWhenAFieldIsLocked() {
    service = new UpdateDiscountService(readPort, writePort);
    Instant now = Instant.now();
    DiscountId id = DiscountId.newId();
    Discount discount = newDiscount(id, now.minusSeconds(3600), now.plusSeconds(3600));
    when(readPort.findById(id)).thenReturn(Optional.of(discount));

    assertThatThrownBy(
            () -> service.execute(new UpdateDiscountCommand(id.value(), now.minusSeconds(1800), null, null, null)))
        .isInstanceOf(DiscountNotEditableException.class);
    verify(writePort, never()).save(any());
  }
}
