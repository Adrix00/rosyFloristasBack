package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.UpdateProductExtrasCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductSuggestionPort;
import com.floristeriarosy.domain.exception.product.ProductNotAnExtraException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductSuggestsItselfException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateProductExtrasServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;
  @Mock private ProductSuggestionPort suggestionPort;

  private UpdateProductExtrasService service;

  private Product product(UUID id, boolean isExtra) {
    return Product.reconstitute(
        ProductId.of(id),
        "Ramo",
        ProductSlug.generateFrom("Ramo " + id),
        null,
        BigDecimal.TEN,
        null,
        null,
        ProductStatus.ACTIVE,
        isExtra,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void replacesTheSuggestionsWithValidExtras() {
    service = new UpdateProductExtrasService(readPort, categoryPort, imagePort, suggestionPort);
    UUID id = UUID.randomUUID();
    UUID extraId = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, false)));
    when(readPort.findById(ProductId.of(extraId))).thenReturn(Optional.of(product(extraId, true)));

    ProductDto dto = service.execute(new UpdateProductExtrasCommand(id, List.of(extraId)));

    assertThat(dto.id()).isEqualTo(id);
    verify(suggestionPort).replaceSuggestions(ProductId.of(id), List.of(ProductId.of(extraId)));
  }

  @Test
  void rejectsSuggestingAProductToItself() {
    service = new UpdateProductExtrasService(readPort, categoryPort, imagePort, suggestionPort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, true)));

    assertThatThrownBy(() -> service.execute(new UpdateProductExtrasCommand(id, List.of(id))))
        .isInstanceOf(ProductSuggestsItselfException.class);
  }

  @Test
  void rejectsAnUnknownCandidateExtra() {
    service = new UpdateProductExtrasService(readPort, categoryPort, imagePort, suggestionPort);
    UUID id = UUID.randomUUID();
    UUID extraId = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, false)));
    when(readPort.findById(ProductId.of(extraId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new UpdateProductExtrasCommand(id, List.of(extraId))))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void rejectsACandidateThatIsNotAnExtra() {
    service = new UpdateProductExtrasService(readPort, categoryPort, imagePort, suggestionPort);
    UUID id = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, false)));
    when(readPort.findById(ProductId.of(candidateId))).thenReturn(Optional.of(product(candidateId, false)));

    assertThatThrownBy(() -> service.execute(new UpdateProductExtrasCommand(id, List.of(candidateId))))
        .isInstanceOf(ProductNotAnExtraException.class);
  }

  @Test
  void rejectsUpdatingExtrasOfAnUnknownProduct() {
    service = new UpdateProductExtrasService(readPort, categoryPort, imagePort, suggestionPort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new UpdateProductExtrasCommand(id, List.of())))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
