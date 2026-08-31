package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoryQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.CategoryStatus;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCategoryServiceTest {

  @Mock private CategoryReadPort readPort;

  private GetCategoryService service;

  @Test
  void findsByUuid() {
    service = new GetCategoryService(readPort);
    CategoryId id = CategoryId.newId();
    Category category =
        Category.create(id, "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    when(readPort.findById(id)).thenReturn(Optional.of(category));

    CategoryDto dto = service.execute(new GetCategoryQuery(id.value().toString()));

    assertThat(dto.id()).isEqualTo(id.value());
  }

  @Test
  void findsBySlugWhenNotAUuid() {
    service = new GetCategoryService(readPort);
    CategoryId id = CategoryId.newId();
    Category category =
        Category.create(id, "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    when(readPort.findBySlug("ramos")).thenReturn(Optional.of(category));

    CategoryDto dto = service.execute(new GetCategoryQuery("ramos"));

    assertThat(dto.slug()).isEqualTo("ramos");
  }

  @Test
  void inactiveCategoryIsNotFoundOnPublicAccess() {
    service = new GetCategoryService(readPort);
    CategoryId id = CategoryId.newId();
    Category category =
        Category.create(id, "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    category.changeStatus(CategoryStatus.INACTIVE);
    when(readPort.findBySlug("ramos")).thenReturn(Optional.of(category));

    assertThatThrownBy(() -> service.execute(new GetCategoryQuery("ramos")))
        .isInstanceOf(CategoryNotFoundException.class);
  }
}
