package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.port.in.GetCategoryImpactUseCase;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryProductsPort;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.springframework.stereotype.Service;

@Service
public class GetCategoryImpactService implements GetCategoryImpactUseCase {

  private final CategoryExistencePort existencePort;
  private final CategoryProductsPort productsPort;

  public GetCategoryImpactService(
      CategoryExistencePort existencePort, CategoryProductsPort productsPort) {
    this.existencePort = existencePort;
    this.productsPort = productsPort;
  }

  @Override
  public CategoryImpact execute(GetCategoryImpactQuery query) {
    CategoryId id = CategoryId.of(query.id());
    if (!existencePort.existsById(id)) {
      throw new CategoryNotFoundException("Category " + id + " not found");
    }
    return new CategoryImpact(
        productsPort.countByCategory(id),
        productsPort.findLosingVisibility(id),
        productsPort.findLeftWithoutCategory(id));
  }
}
