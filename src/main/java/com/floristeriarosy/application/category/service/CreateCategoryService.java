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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateCategoryService implements CreateCategoryUseCase {

  private final CategoryWritePort writePort;
  private final CategoryExistencePort existencePort;

  public CreateCategoryService(CategoryWritePort writePort, CategoryExistencePort existencePort) {
    this.writePort = writePort;
    this.existencePort = existencePort;
  }

  @Override
  public CategoryDto execute(CreateCategoryCommand command) {
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
    return CategoryDtoMapper.toDto(writePort.save(category));
  }
}
