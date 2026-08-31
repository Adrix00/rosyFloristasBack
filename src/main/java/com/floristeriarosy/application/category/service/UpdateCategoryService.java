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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateCategoryService implements UpdateCategoryUseCase {

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  public UpdateCategoryService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  @Override
  public CategoryDto execute(UpdateCategoryCommand command) {
    CategoryId id = CategoryId.of(command.id());
    Category category =
        readPort
            .findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Category " + id + " not found"));

    CategorySlug slug = CategorySlug.generateFrom(command.name());
    if (!slug.equals(category.slug())) {
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
    return CategoryDtoMapper.toDto(writePort.save(category));
  }
}
