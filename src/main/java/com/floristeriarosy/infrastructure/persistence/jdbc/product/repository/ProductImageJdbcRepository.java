package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper.ProductImageRefRowMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads and writes for a product's image gallery (ADR-002). */
@Repository
public class ProductImageJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageJdbcRepository.class);

  private static final String FIND_SQL =
      "SELECT id, image_id, alt_text, position FROM product_images WHERE product_id = ? ORDER BY position";

  private final JdbcTemplate jdbcTemplate;
  private final ProductImageRefRowMapper rowMapper = new ProductImageRefRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductImageJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Deletes every existing gallery row for {@code productId} and inserts {@code images}; the list
   * index becomes {@code position}.
   *
   * @param productId the product whose gallery is being set
   * @param images every image the gallery should contain, in display order
   */
  public void replaceImages(UUID productId, List<ProductImageAssignment> images) {
    LOGGER.debug("replaceImages productId={} count={}", productId, images.size());
    jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", productId);
    Timestamp now = Timestamp.from(Instant.now());
    List<Object[]> params =
        java.util.stream.IntStream.range(0, images.size())
            .mapToObj(
                index -> {
                  ProductImageAssignment image = images.get(index);
                  return new Object[] {
                    UUID.randomUUID(), productId, image.imageId(), image.altText(), index, now, now
                  };
                })
            .toList();
    jdbcTemplate.batchUpdate(
        "INSERT INTO product_images (id, product_id, image_id, alt_text, position, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        params);
    LOGGER.debug("replaceImages productId={} -> {} rows inserted", productId, images.size());
  }

  /**
   * @param productId the product to look up
   * @return its gallery, ordered by position
   */
  public List<ProductImageRef> findImages(UUID productId) {
    LOGGER.debug("findImages productId={}", productId);
    List<ProductImageRef> result = jdbcTemplate.query(FIND_SQL, rowMapper, productId);
    LOGGER.debug("findImages productId={} -> count={}", productId, result.size());
    return result;
  }
}
