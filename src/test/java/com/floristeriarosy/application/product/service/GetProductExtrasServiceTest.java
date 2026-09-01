package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductSuggestionPort;
import com.floristeriarosy.application.product.query.GetProductExtrasQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductExtrasServiceTest {

  @Mock private ProductExistencePort existencePort;
  @Mock private ProductSuggestionPort suggestionPort;

  private GetProductExtrasService service;

  @Test
  void listsTheVisibleSuggestedExtras() {
    service = new GetProductExtrasService(existencePort, suggestionPort);
    UUID id = UUID.randomUUID();
    ProductSummaryDto extra =
        new ProductSummaryDto(
            UUID.randomUUID(), "Bombones", "bombones", BigDecimal.TEN, BigDecimal.TEN, false, null);
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(suggestionPort.findVisibleSuggestions(ProductId.of(id))).thenReturn(List.of(extra));

    List<ProductSummaryDto> extras = service.execute(new GetProductExtrasQuery(id));

    assertThat(extras).containsExactly(extra);
  }

  @Test
  void rejectsListingExtrasOfAnUnknownProduct() {
    service = new GetProductExtrasService(existencePort, suggestionPort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetProductExtrasQuery(id)))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
