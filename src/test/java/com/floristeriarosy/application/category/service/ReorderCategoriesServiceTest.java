package com.floristeriarosy.application.category.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.exception.category.CategoryPositionsIncompleteException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReorderCategoriesServiceTest {

  @Mock private CategoryReadPort readPort;
  @Mock private CategoryWritePort writePort;

  private ReorderCategoriesService service;

  private Category category(CategoryId id, String name) {
    return Category.create(id, name, CategorySlug.generateFrom(name), null, null, 0);
  }

  @Test
  void rejectsAnUnknownCategoryId() {
    service = new ReorderCategoriesService(readPort, writePort);
    CategoryId known = CategoryId.newId();
    when(readPort.findAll()).thenReturn(List.of(category(known, "Ramos")));
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> service.execute(new ReorderCategoriesCommand(List.of(unknown))))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void rejectsAPartialReorder() {
    service = new ReorderCategoriesService(readPort, writePort);
    CategoryId a = CategoryId.newId();
    CategoryId b = CategoryId.newId();
    when(readPort.findAll()).thenReturn(List.of(category(a, "Ramos"), category(b, "Plantas")));

    assertThatThrownBy(() -> service.execute(new ReorderCategoriesCommand(List.of(a.value()))))
        .isInstanceOf(CategoryPositionsIncompleteException.class);
  }

  @Test
  void reordersWhenTheListIsComplete() {
    service = new ReorderCategoriesService(readPort, writePort);
    CategoryId a = CategoryId.newId();
    CategoryId b = CategoryId.newId();
    when(readPort.findAll()).thenReturn(List.of(category(a, "Ramos"), category(b, "Plantas")));

    service.execute(new ReorderCategoriesCommand(List.of(b.value(), a.value())));

    verify(writePort).updatePositions(List.of(b, a));
  }
}
