package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
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
class ChangeCategoryStatusServiceTest {

  @Mock private CategoryReadPort readPort;
  @Mock private CategoryWritePort writePort;

  private ChangeCategoryStatusService service;

  @Test
  void deactivatingAnAlreadyInactiveCategoryIsANoOp() {
    service = new ChangeCategoryStatusService(readPort, writePort);
    CategoryId id = CategoryId.newId();
    Category category =
        Category.create(id, "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);
    category.changeStatus(CategoryStatus.INACTIVE);
    when(readPort.findById(id)).thenReturn(Optional.of(category));
    when(writePort.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CategoryDto dto =
        service.execute(new ChangeCategoryStatusCommand(id.value(), CategoryStatus.INACTIVE));

    assertThat(dto.status()).isEqualTo(CategoryStatus.INACTIVE);
  }

  @Test
  void throwsNotFoundWhenCategoryDoesNotExist() {
    service = new ChangeCategoryStatusService(readPort, writePort);
    CategoryId id = CategoryId.newId();
    when(readPort.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeCategoryStatusCommand(id.value(), CategoryStatus.INACTIVE)))
        .isInstanceOf(CategoryNotFoundException.class);
  }
}
