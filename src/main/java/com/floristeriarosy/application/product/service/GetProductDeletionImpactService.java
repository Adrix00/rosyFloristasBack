package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.port.in.GetProductDeletionImpactUseCase;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.query.GetProductDeletionImpactQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetProductDeletionImpactUseCase}: previews whether a product can be physically
 * deleted.
 */
@Service
public class GetProductDeletionImpactService implements GetProductDeletionImpactUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GetProductDeletionImpactService.class);

  private final ProductExistencePort existencePort;

  /**
   * @param existencePort checks the product exists and counts its commercial history
   */
  public GetProductDeletionImpactService(ProductExistencePort existencePort) {
    this.existencePort = existencePort;
  }

  /**
   * @param query the product being previewed for deletion
   * @return the impact preview
   * @throws ProductNotFoundException {@code query.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ProductDeletionImpact execute(GetProductDeletionImpactQuery query) {
    LOGGER.debug("getProductDeletionImpact id={}", query.id());

    ProductId id = ProductId.of(query.id());
    if (!existencePort.existsById(id)) {
      throw new ProductNotFoundException("Product " + id + " not found");
    }
    ProductDeletionImpact result = existencePort.deletionImpact(id);

    LOGGER.debug("getProductDeletionImpact id={} -> deletable={}", id, result.deletable());
    return result;
  }
}
