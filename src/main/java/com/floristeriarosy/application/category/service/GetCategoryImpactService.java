package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.port.in.GetCategoryImpactUseCase;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryProductsPort;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetCategoryImpactUseCase}: preview before deactivating or deleting. */
@Service
public class GetCategoryImpactService implements GetCategoryImpactUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCategoryImpactService.class);

  private final CategoryExistencePort existencePort;
  private final CategoryProductsPort productsPort;

  /**
   * @param existencePort checks the category exists before computing its impact
   * @param productsPort counts and lists the affected products
   */
  public GetCategoryImpactService(
      CategoryExistencePort existencePort, CategoryProductsPort productsPort) {
    this.existencePort = existencePort;
    this.productsPort = productsPort;
  }

  /**
   * Computes what would change if this category were deactivated or deleted, without applying
   * anything (category.md, section 3.4).
   *
   * @param query id of the category to preview
   * @return total associated products, and the two impact lists
   * @throws CategoryNotFoundException {@code query.id()} does not exist
   */
  @Override
  public CategoryImpact execute(GetCategoryImpactQuery query) {
    LOGGER.debug("getCategoryImpact id={}", query.id());

    CategoryId id = CategoryId.of(query.id());
    if (!existencePort.existsById(id)) {
      throw new CategoryNotFoundException("Category " + id + " not found");
    }
    CategoryImpact result =
        new CategoryImpact(
            productsPort.countByCategory(id),
            productsPort.findLosingVisibility(id),
            productsPort.findLeftWithoutCategory(id));

    LOGGER.debug(
        "getCategoryImpact -> totalProducts={} losingVisibility={} leftWithoutCategory={}",
        result.totalProducts(),
        result.productsLosingVisibility().size(),
        result.productsLeftWithoutCategory().size());
    return result;
  }
}
