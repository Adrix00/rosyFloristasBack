package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.CreateCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CreateCategoryUseCase}: creates a category with a slug generated from its name.
 */
@Service
@Transactional
public class CreateCategoryService implements CreateCategoryUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateCategoryService.class);

  private final CategoryWritePort writePort;
  private final CategoryExistencePort existencePort;

  /**
   * @param writePort persists the new category
   * @param existencePort checks the generated slug is not already taken
   */
  public CreateCategoryService(CategoryWritePort writePort, CategoryExistencePort existencePort) {
    this.writePort = writePort;
    this.existencePort = existencePort;
  }

  /**
   * Creates a new, {@code ACTIVE} category.
   *
   * @param command name, description, imageId and position of the category to create
   * @return the created category
   * @throws CategoryAlreadyExistsException the slug generated from {@code command.name()} is
   *     already in use
   */
  @Override
  public CategoryDto execute(CreateCategoryCommand command) {
    LOGGER.debug(
        "createCategory name={} description={} imageId={} position={}",
        command.name(),
        command.description(),
        command.imageId(),
        command.position());

    CategorySlug slug = CategorySlug.generateFrom(command.name());
    if (existencePort.existsBySlug(slug.value())) {
      throw new CategoryAlreadyExistsException(
          "A category with slug '" + slug.value() + "' already exists");
    }
    Category category =
        Category.create(
            CategoryId.newId(),
            command.name(),
            slug,
            command.description(),
            command.imageId(),
            command.position());
    CategoryDto result = CategoryDtoMapper.toDto(writePort.save(category));

    LOGGER.debug("createCategory -> id={} slug={}", result.id(), result.slug());
    return result;
  }
}
