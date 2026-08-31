package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.domain.model.category.Category;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetCategoriesUseCase}: the public and the admin category listings. */
@Service
public class GetCategoriesService implements GetCategoriesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCategoriesService.class);

  private final CategoryReadPort readPort;

  /**
   * @param readPort lists categories, active-only or all statuses
   */
  public GetCategoriesService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  /**
   * Lists categories ordered by position then name.
   *
   * @param query {@code includeInactive=false} for {@code GET /categories} (public), {@code true}
   *     for {@code GET /categories/all} (ADMIN)
   * @return the matching categories
   */
  @Override
  public List<CategoryDto> execute(GetCategoriesQuery query) {
    LOGGER.debug("getCategories includeInactive={}", query.includeInactive());

    List<Category> categories =
        query.includeInactive() ? readPort.findAll() : readPort.findAllActive();
    List<CategoryDto> result = categories.stream().map(CategoryDtoMapper::toDto).toList();

    LOGGER.debug("getCategories -> {} categories", result.size());
    return result;
  }
}
