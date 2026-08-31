package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.model.category.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateCategoryServiceTest {

  @Mock private CategoryWritePort writePort;
  @Mock private CategoryExistencePort existencePort;

  private CreateCategoryService service;

  @Test
  void createsCategoryWithGeneratedSlug() {
    service = new CreateCategoryService(writePort, existencePort);
    when(existencePort.existsBySlug("ramos")).thenReturn(false);
    when(writePort.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CategoryDto dto = service.execute(new CreateCategoryCommand("Ramos", "descripcion", null, 0));

    assertThat(dto.name()).isEqualTo("Ramos");
    assertThat(dto.slug()).isEqualTo("ramos");
  }

  @Test
  void rejectsWhenSlugAlreadyExists() {
    service = new CreateCategoryService(writePort, existencePort);
    when(existencePort.existsBySlug("ramos")).thenReturn(true);

    assertThatThrownBy(() -> service.execute(new CreateCategoryCommand("Ramos", null, null, 0)))
        .isInstanceOf(CategoryAlreadyExistsException.class);
  }
}
