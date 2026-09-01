package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.UpdateProductCategoriesCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductWithoutCategoryException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
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
class UpdateProductCategoriesServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;

  private UpdateProductCategoriesService service;

  private Product product(UUID id) {
    return Product.reconstitute(
        ProductId.of(id),
        "Ramo",
        ProductSlug.generateFrom("Ramo"),
        null,
        BigDecimal.TEN,
        null,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void replacesTheFullCategorySet() {
    service = new UpdateProductCategoriesService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id)));
    when(categoryPort.findCategories(ProductId.of(id)))
        .thenReturn(List.of(new ProductCategoryRef(categoryId, "Ramos", "ramos")));

    ProductDto dto = service.execute(new UpdateProductCategoriesCommand(id, List.of(categoryId)));

    assertThat(dto.categories()).hasSize(1);
    verify(categoryPort).replaceCategories(ProductId.of(id), List.of(CategoryId.of(categoryId)));
  }

  @Test
  void rejectsEmptyingTheCategorySetThroughThisEndpoint() {
    service = new UpdateProductCategoriesService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.execute(new UpdateProductCategoriesCommand(id, List.of())))
        .isInstanceOf(ProductWithoutCategoryException.class);
    verifyNoInteractions(readPort);
  }

  @Test
  void rejectsUpdatingCategoriesOfAnUnknownProduct() {
    service = new UpdateProductCategoriesService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new UpdateProductCategoriesCommand(id, List.of(UUID.randomUUID()))))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
