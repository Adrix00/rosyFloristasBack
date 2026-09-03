package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.port.in.DeleteCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link DeleteCategoryUseCase}: permanently removes a category. */
@Service
@Transactional
public class DeleteCategoryService implements DeleteCategoryUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteCategoryService.class);

  private final CategoryExistencePort existencePort;
  private final CategoryWritePort writePort;

  /**
   * @param existencePort checks the category exists before attempting the delete
   * @param writePort performs the delete
   */
  public DeleteCategoryService(CategoryExistencePort existencePort, CategoryWritePort writePort) {
    this.existencePort = existencePort;
    this.writePort = writePort;
  }

  /**
   * Deletes the category. {@code product_categories} rows cascade; the products themselves survive
   * (category.md, section 3.3).
   *
   * @param command id of the category to delete
   * @throws CategoryNotFoundException {@code command.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public void execute(DeleteCategoryCommand command) {
    LOGGER.debug("deleteCategory id={}", command.id());

    CategoryId id = CategoryId.of(command.id());
    if (!existencePort.existsById(id)) {
      throw new CategoryNotFoundException("Category " + id + " not found");
    }
    writePort.delete(id);

    LOGGER.debug("deleteCategory -> id={} deleted", id);
  }
}
