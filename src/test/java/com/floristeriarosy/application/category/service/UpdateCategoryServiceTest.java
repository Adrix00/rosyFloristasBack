package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCategoryServiceTest {

  @Mock private CategoryReadPort readPort;
  @Mock private CategoryWritePort writePort;

  private UpdateCategoryService service;

  private Category existing(CategoryId id, String name) {
    return Category.create(id, name, CategorySlug.generateFrom(name), null, null, 0);
  }

  @Test
  void throwsNotFoundWhenCategoryDoesNotExist() {
    service = new UpdateCategoryService(readPort, writePort);
    CategoryId id = CategoryId.newId();
    when(readPort.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new UpdateCategoryCommand(id.value(), "Ramos", null, null, 0)))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void rejectsRenameToASlugUsedByAnotherCategory() {
    service = new UpdateCategoryService(readPort, writePort);
    CategoryId id = CategoryId.newId();
    Category category = existing(id, "Ramos");
    Category other = existing(CategoryId.newId(), "Plantas");
    when(readPort.findById(id)).thenReturn(Optional.of(category));
    when(readPort.findBySlug("plantas")).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () -> service.execute(new UpdateCategoryCommand(id.value(), "Plantas", null, null, 0)))
        .isInstanceOf(CategoryAlreadyExistsException.class);
  }

  @Test
  void allowsKeepingTheSameSlugOnItsOwnCategory() {
    service = new UpdateCategoryService(readPort, writePort);
    CategoryId id = CategoryId.newId();
    Category category = existing(id, "Ramos");
    when(readPort.findById(id)).thenReturn(Optional.of(category));
    when(writePort.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CategoryDto dto =
        service.execute(new UpdateCategoryCommand(id.value(), "Ramos", "nueva desc", null, 2));

    assertThat(dto.description()).isEqualTo("nueva desc");
    assertThat(dto.position()).isEqualTo(2);
  }
}
