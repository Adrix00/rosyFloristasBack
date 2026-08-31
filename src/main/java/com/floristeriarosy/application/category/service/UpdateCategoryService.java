package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.UpdateCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.shared.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateCategoryUseCase}: full replace ({@code PUT}) of an existing category. */
@Service
@Transactional
public class UpdateCategoryService implements UpdateCategoryUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCategoryService.class);

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  /**
   * @param readPort loads the category being updated, and checks the new slug for conflicts
   * @param writePort persists the updated category
   */
  public UpdateCategoryService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * Replaces name, description, image and position of an existing category. An absent optional
   * field clears its previous value (category.md, section 5).
   *
   * @param command id of the category to update, plus its new field values
   * @return the updated category
   * @throws CategoryNotFoundException {@code command.id()} does not exist
   * @throws CategoryAlreadyExistsException the slug generated from the new name is already used by
   *     another category
   */
  @Override
  public CategoryDto execute(UpdateCategoryCommand command) {
    LOGGER.debug(
        "updateCategory id={} name={} description={} imageId={} position={}",
        command.id(),
        LogSanitizer.sanitize(command.name()),
        LogSanitizer.sanitize(command.description()),
        command.imageId(),
        command.position());

    CategoryId id = CategoryId.of(command.id());
    Category category =
        readPort
            .findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Category " + id + " not found"));

    CategorySlug slug = CategorySlug.generateFrom(command.name());
    if (!slug.equals(category.slug())) {
      // Renombrar regenera el slug: solo choca si OTRA categoria ya lo usa (category.md, 3.1).
      readPort
          .findBySlug(slug.value())
          .filter(other -> !other.id().equals(id))
          .ifPresent(
              other -> {
                throw new CategoryAlreadyExistsException(
                    "A category with slug '" + slug.value() + "' already exists");
              });
    }

    category.replace(
        command.name(), slug, command.description(), command.imageId(), command.position());
    CategoryDto result = CategoryDtoMapper.toDto(writePort.save(category));

    LOGGER.debug("updateCategory -> id={} slug={}", result.id(), result.slug());
    return result;
  }
}
