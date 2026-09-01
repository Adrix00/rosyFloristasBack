package com.floristeriarosy.application.discount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.query.GetProductDiscountsQuery;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductDiscountsServiceTest {

  @Mock private ProductExistencePort productExistencePort;
  @Mock private DiscountReadPort readPort;

  private GetProductDiscountsService service;

  @Test
  void returnsTheCompleteDiscountHistoryOfAnExistingProduct() {
    service = new GetProductDiscountsService(productExistencePort, readPort);
    ProductId productId = ProductId.newId();
    Instant now = Instant.now();
    Discount discount =
        Discount.create(
            DiscountId.newId(),
            productId,
            new BigDecimal("20.00"),
            new BigDecimal("15.00"),
            now.minusSeconds(3600),
            now.plusSeconds(3600),
            null);
    when(productExistencePort.existsById(productId)).thenReturn(true);
    when(readPort.findByProduct(productId)).thenReturn(List.of(discount));

    List<DiscountDto> result = service.execute(new GetProductDiscountsQuery(productId.value()));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).salePrice()).isEqualByComparingTo("15.00");
  }

  @Test
  void throwsProductNotFoundWhenTheProductDoesNotExist() {
    service = new GetProductDiscountsService(productExistencePort, readPort);
    UUID productId = UUID.randomUUID();
    when(productExistencePort.existsById(ProductId.of(productId))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetProductDiscountsQuery(productId)))
        .isInstanceOf(ProductNotFoundException.class);
    verify(readPort, never()).findByProduct(any());
  }
}
