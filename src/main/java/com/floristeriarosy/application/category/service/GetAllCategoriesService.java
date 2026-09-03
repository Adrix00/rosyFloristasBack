package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetAllCategoriesUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Implements {@link GetAllCategoriesUseCase}: the admin category listing, every status. */
@Service
public class GetAllCategoriesService implements GetAllCategoriesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAllCategoriesService.class);

  private final CategoryReadPort readPort;

  /**
   * @param readPort lists every category regardless of status
   */
  public GetAllCategoriesService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  /**
   * @return every category regardless of status, ordered by position then name
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public List<CategoryDto> execute() {
    LOGGER.debug("getAllCategories");

    List<CategoryDto> result = readPort.findAll().stream().map(CategoryDtoMapper::toDto).toList();

    LOGGER.debug("getAllCategories -> {} categories", result.size());
    return result;
  }
}
