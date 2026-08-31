package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCategoriesServiceTest {

  @Mock private CategoryReadPort readPort;

  private GetCategoriesService service;

  @Test
  void publicListingUsesOnlyActiveCategories() {
    service = new GetCategoriesService(readPort);
    Category category =
        Category.create(
            CategoryId.newId(), "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    when(readPort.findAllActive()).thenReturn(List.of(category));

    List<CategoryDto> result = service.execute(new GetCategoriesQuery(false));

    assertThat(result).hasSize(1);
  }

  @Test
  void adminListingIncludesEveryStatus() {
    service = new GetCategoriesService(readPort);
    Category category =
        Category.create(
            CategoryId.newId(), "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    when(readPort.findAll()).thenReturn(List.of(category));

    List<CategoryDto> result = service.execute(new GetCategoriesQuery(true));

    assertThat(result).hasSize(1);
  }
}
