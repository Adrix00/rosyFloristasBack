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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link ReorderCategoriesUseCase}: full-catalog reorder in one transaction. */
@Service
@Transactional
public class ReorderCategoriesService implements ReorderCategoriesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReorderCategoriesService.class);

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  /**
   * @param readPort loads every existing category, to validate the submitted list is complete
   * @param writePort applies the new positions
   */
  public ReorderCategoriesService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * Sets each category's position to its index in {@code command.categoryIds()}. The list must name
   * every existing category, no more and no less (category.md, section 3.5).
   *
   * @param command the categories in their new order
   * @throws CategoryNotFoundException an id in the list does not exist
   * @throws CategoryPositionsIncompleteException the list omits an existing category
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public void execute(ReorderCategoriesCommand command) {
    LOGGER.debug("reorderCategories categoryIds={}", command.categoryIds());

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
    LOGGER.debug("reorderCategories -> {} categories repositioned", submittedIds.size());
  }
}
