package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetCategoriesUseCase}: the public category listing. */
@Service
public class GetCategoriesService implements GetCategoriesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCategoriesService.class);

  private final CategoryReadPort readPort;

  /**
   * @param readPort lists the {@code ACTIVE} categories
   */
  public GetCategoriesService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  /**
   * @return the {@code ACTIVE} categories, ordered by position then name
   */
  @Override
  public List<CategoryDto> execute() {
    LOGGER.debug("getCategories");

    List<CategoryDto> result =
        readPort.findAllActive().stream().map(CategoryDtoMapper::toDto).toList();

    LOGGER.debug("getCategories -> {} categories", result.size());
    return result;
  }
}
