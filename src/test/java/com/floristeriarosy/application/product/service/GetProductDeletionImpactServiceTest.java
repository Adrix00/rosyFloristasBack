package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.query.GetProductDeletionImpactQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductDeletionImpactServiceTest {

  @Mock private ProductExistencePort existencePort;

  private GetProductDeletionImpactService service;

  @Test
  void returnsTheDeletableImpactForAFreshProduct() {
    service = new GetProductDeletionImpactService(existencePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(existencePort.deletionImpact(ProductId.of(id)))
        .thenReturn(new ProductDeletionImpact(true, List.of(), 0, 0, 0));

    ProductDeletionImpact impact = service.execute(new GetProductDeletionImpactQuery(id));

    assertThat(impact.deletable()).isTrue();
    assertThat(impact.blockedBy()).isEmpty();
  }

  @Test
  void returnsTheBlockedReasonsForAProductWithHistory() {
    service = new GetProductDeletionImpactService(existencePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(existencePort.deletionImpact(ProductId.of(id)))
        .thenReturn(new ProductDeletionImpact(false, List.of("ORDERS"), 2, 0, 0));

    ProductDeletionImpact impact = service.execute(new GetProductDeletionImpactQuery(id));

    assertThat(impact.deletable()).isFalse();
    assertThat(impact.blockedBy()).containsExactly("ORDERS");
    assertThat(impact.orderCount()).isEqualTo(2);
  }

  @Test
  void rejectsPreviewingAnUnknownProduct() {
    service = new GetProductDeletionImpactService(existencePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetProductDeletionImpactQuery(id)))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
