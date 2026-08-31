package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;
import com.floristeriarosy.application.category.port.in.ReorderCategoriesUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.exception.category.CategoryPositionsIncompleteException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReorderCategoriesService implements ReorderCategoriesUseCase {

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  public ReorderCategoriesService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  @Override
  public void execute(ReorderCategoriesCommand command) {
    Set<CategoryId> existingIds =
        readPort.findAll().stream().map(Category::id).collect(Collectors.toSet());
    List<CategoryId> submittedIds = command.categoryIds().stream().map(CategoryId::of).toList();

    for (CategoryId id : submittedIds) {
      if (!existingIds.contains(id)) {
        throw new CategoryNotFoundException("Category " + id + " not found");
      }
    }
    if (submittedIds.size() != existingIds.size()) {
      throw new CategoryPositionsIncompleteException(
          "The reorder request must include every existing category");
    }

    writePort.updatePositions(submittedIds);
  }
}
