package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryServiceTest {

  @Mock private CategoryExistencePort existencePort;
  @Mock private CategoryWritePort writePort;

  private DeleteCategoryService service;

  @Test
  void throwsNotFoundAndNeverDeletesWhenCategoryDoesNotExist() {
    service = new DeleteCategoryService(existencePort, writePort);
    CategoryId id = CategoryId.newId();
    when(existencePort.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new DeleteCategoryCommand(id.value())))
        .isInstanceOf(CategoryNotFoundException.class);
    verifyNoInteractions(writePort);
  }

  @Test
  void deletesWhenCategoryExists() {
    service = new DeleteCategoryService(existencePort, writePort);
    CategoryId id = CategoryId.newId();
    when(existencePort.existsById(id)).thenReturn(true);

    service.execute(new DeleteCategoryCommand(id.value()));

    verify(writePort).delete(id);
  }
}
