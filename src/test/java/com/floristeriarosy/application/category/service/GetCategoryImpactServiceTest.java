package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryProductsPort;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCategoryImpactServiceTest {

  @Mock private CategoryExistencePort existencePort;
  @Mock private CategoryProductsPort productsPort;

  private GetCategoryImpactService service;

  @Test
  void throwsNotFoundWhenCategoryDoesNotExist() {
    service = new GetCategoryImpactService(existencePort, productsPort);
    CategoryId id = CategoryId.newId();
    when(existencePort.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetCategoryImpactQuery(id.value())))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void aggregatesTotalsAndImpactLists() {
    service = new GetCategoryImpactService(existencePort, productsPort);
    CategoryId id = CategoryId.newId();
    when(existencePort.existsById(id)).thenReturn(true);
    when(productsPort.countByCategory(id)).thenReturn(5L);
    when(productsPort.findLosingVisibility(id)).thenReturn(List.of());
    when(productsPort.findLeftWithoutCategory(id)).thenReturn(List.of());

    CategoryImpact impact = service.execute(new GetCategoryImpactQuery(id.value()));

    assertThat(impact.totalProducts()).isEqualTo(5L);
  }
}
