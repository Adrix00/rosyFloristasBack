package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.domain.model.category.Category;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetCategoriesService implements GetCategoriesUseCase {

  private final CategoryReadPort readPort;

  public GetCategoriesService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  @Override
  public List<CategoryDto> execute(GetCategoriesQuery query) {
    List<Category> categories =
        query.includeInactive() ? readPort.findAll() : readPort.findAllActive();
    return categories.stream().map(CategoryDtoMapper::toDto).toList();
  }
}
