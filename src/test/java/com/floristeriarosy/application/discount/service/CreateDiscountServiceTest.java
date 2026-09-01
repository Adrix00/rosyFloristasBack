package com.floristeriarosy.application.discount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateDiscountServiceTest {

  @Mock private ProductReadPort productReadPort;
  @Mock private DiscountWritePort writePort;

  private CreateDiscountService service;

  private Product newProduct(BigDecimal price) {
    return Product.create(
        ProductId.newId(), "Ramo", ProductSlug.generateFrom("Ramo"), null, price, false, Map.of());
  }

  @Test
  void createsDiscountFreezingTheProductsCurrentPrice() {
    service = new CreateDiscountService(productReadPort, writePort);
    Product product = newProduct(new BigDecimal("20.00"));
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(product));
    when(writePort.save(any(Discount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    Instant now = Instant.now();

    DiscountDto dto =
        service.execute(
            new CreateDiscountCommand(
                product.id().value(), new BigDecimal("15.00"), now.plusSeconds(3600), now.plusSeconds(7200), null));

    assertThat(dto.originalPrice()).isEqualByComparingTo("20.00");
    assertThat(dto.salePrice()).isEqualByComparingTo("15.00");
  }

  @Test
  void throwsProductNotFoundWhenTheProductDoesNotExist() {
    service = new CreateDiscountService(productReadPort, writePort);
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.empty());
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateDiscountCommand(
                        UUID.randomUUID(), new BigDecimal("15.00"), now.plusSeconds(3600), now.plusSeconds(7200), null)))
        .isInstanceOf(ProductNotFoundException.class);
    verify(writePort, never()).save(any());
  }

  @Test
  void throwsPeriodInvalidWhenEndsAtIsNotAfterStartsAt() {
    service = new CreateDiscountService(productReadPort, writePort);
    Product product = newProduct(new BigDecimal("20.00"));
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(product));
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateDiscountCommand(
                        product.id().value(), new BigDecimal("15.00"), now.plusSeconds(7200), now.plusSeconds(3600), null)))
        .isInstanceOf(DiscountPeriodInvalidException.class);
    verify(writePort, never()).save(any());
  }

  @Test
  void throwsPeriodInvalidWhenEndsAtIsInThePast() {
    service = new CreateDiscountService(productReadPort, writePort);
    Product product = newProduct(new BigDecimal("20.00"));
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(product));
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateDiscountCommand(
                        product.id().value(), new BigDecimal("15.00"), now.minusSeconds(7200), now.minusSeconds(3600), null)))
        .isInstanceOf(DiscountPeriodInvalidException.class);
    verify(writePort, never()).save(any());
  }

  @Test
  void throwsPriceNotLowerWhenSalePriceIsNotLowerThanTheProductsCurrentPrice() {
    service = new CreateDiscountService(productReadPort, writePort);
    Product product = newProduct(new BigDecimal("20.00"));
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(product));
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateDiscountCommand(
                        product.id().value(), new BigDecimal("20.00"), now.plusSeconds(3600), now.plusSeconds(7200), null)))
        .isInstanceOf(DiscountPriceNotLowerException.class);
    verify(writePort, never()).save(any());
  }
}
