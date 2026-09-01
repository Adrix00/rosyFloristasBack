package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductImageJdbcRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ProductImagePort} (ADR-003) over JDBC (ADR-002): a pure join-table
 * association, no aggregate to load.
 */
@Repository
public class ProductImagePersistenceAdapter implements ProductImagePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductImagePersistenceAdapter.class);

  private final ProductImageJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository reads and writes {@code product_images}
   */
  public ProductImagePersistenceAdapter(ProductImageJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param id the product whose gallery is being set
   * @param images every image the gallery should contain, in display order
   */
  @Override
  public void replaceImages(ProductId id, List<ProductImageAssignment> images) {
    LOGGER.debug("replaceImages id={} count={}", id, images.size());
    jdbcRepository.replaceImages(id.value(), images);
    LOGGER.debug("replaceImages id={} -> replaced", id);
  }

  /**
   * @param id the product to look up
   * @return its gallery, ordered by position
   */
  @Override
  public List<ProductImageRef> findImages(ProductId id) {
    LOGGER.debug("findImages id={}", id);
    List<ProductImageRef> result = jdbcRepository.findImages(id.value());
    LOGGER.debug("findImages id={} -> count={}", id, result.size());
    return result;
  }
}
