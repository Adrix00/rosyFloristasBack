package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.product.ProductEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.product.repository.ProductJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.product.ProductPersistenceMapper;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductSearchTextBuilder;
import java.math.BigDecimal;
import java.util.Optional;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ProductReadPort}, {@link ProductWritePort} and {@link ProductExistencePort}
 * (ADR-003): JPA for writes and simple lookups, JDBC for visibility, the active discount price and
 * the deletion-impact counts (ADR-002).
 */
@Repository
public class ProductPersistenceAdapter implements ProductReadPort, ProductWritePort, ProductExistencePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductPersistenceAdapter.class);

  private final ProductJpaRepository jpaRepository;
  private final ProductJdbcRepository jdbcRepository;
  private final ProductPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups by id/slug
   * @param jdbcRepository visibility, the active discount price and the deletion-impact counts
   * @param mapper converts between the domain {@link Product} and the JPA {@link ProductEntity}
   */
  public ProductPersistenceAdapter(
      ProductJpaRepository jpaRepository, ProductJdbcRepository jdbcRepository, ProductPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the product to load
   * @return the product, if it exists
   */
  @Override
  public Optional<Product> findById(ProductId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Product> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param slug the product to load
   * @return the product, if it exists
   */
  @Override
  public Optional<Product> findBySlug(String slug) {
    // CodeQL's log-injection sanitizer recognition doesn't trace through LogSanitizer as a
    // helper method call, only a literal encode call on the tainted expression at the log site.
    LOGGER.debug("findBySlug slug={}", Encode.forJava(slug));
    Optional<Product> result = jpaRepository.findBySlug(slug).map(mapper::toDomain);
    LOGGER.debug("findBySlug slug={} -> found={}", Encode.forJava(slug), result.isPresent());
    return result;
  }

  /**
   * @param id the product to check
   * @return {@code true} if {@code status = ACTIVE} and it has at least one {@code ACTIVE}
   *     category
   */
  @Override
  public boolean isVisible(ProductId id) {
    LOGGER.debug("isVisible id={}", id);
    boolean result = jdbcRepository.isVisible(id.value());
    LOGGER.debug("isVisible id={} -> {}", id, result);
    return result;
  }

  /**
   * @param id the product to price
   * @return the {@code sale_price} of its currently active discount, if any
   */
  @Override
  public Optional<BigDecimal> findActiveSalePrice(ProductId id) {
    LOGGER.debug("findActiveSalePrice id={}", id);
    Optional<BigDecimal> result = jdbcRepository.findActiveSalePrice(id.value());
    LOGGER.debug("findActiveSalePrice id={} -> present={}", id, result.isPresent());
    return result;
  }

  /**
   * Updates the managed entity in place when {@code product.id()} already exists, so the {@code
   * @Version} Hibernate loaded is what gets checked — a freshly built detached entity would always
   * carry {@code version = 0} and be mistaken for a new row (ADR-009). Builds a fresh entity only
   * for a genuinely new product.
   *
   * @param product the product to insert or update
   * @return the saved product, with timestamps populated by the database
   * @throws ProductAlreadyExistsException the slug unique constraint was violated
   * @throws ResourceModifiedException the product was changed concurrently
   */
  @Override
  public Product save(Product product) {
    LOGGER.debug("save id={} slug={}", product.id(), product.slug());
    String searchText = ProductSearchTextBuilder.build(product.name(), product.description(), product.attributes());
    ProductEntity entity = jpaRepository.findById(product.id().value()).orElse(null);
    if (entity != null) {
      entity.applyChanges(product, searchText);
    } else {
      entity = mapper.toEntity(product, searchText);
    }
    try {
      Product result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Product " + product.id() + " was modified concurrently");
    } catch (DataIntegrityViolationException violation) {
      throw translateSave(violation, product);
    }
  }

  /**
   * @param id the product to delete; {@code product_categories}, {@code product_images} and
   *     {@code product_suggestions} rows cascade
   * @throws ProductHasHistoryException the product has orders, stock movements or purchases
   *     referencing it
   */
  @Override
  public void delete(ProductId id) {
    LOGGER.debug("delete id={}", id);
    try {
      jpaRepository.deleteById(id.value());
    } catch (DataIntegrityViolationException violation) {
      throw new ProductHasHistoryException(
          "Product " + id + " has orders, stock movements or purchases referencing it");
    }
    LOGGER.debug("delete id={} -> deleted", id);
  }

  /**
   * @param id the product to change
   * @param status the new status
   * @return the updated product
   * @throws ResourceModifiedException the product was changed concurrently
   */
  @Override
  public Product updateStatus(ProductId id, ProductStatus status) {
    LOGGER.debug("updateStatus id={} status={}", id, status);
    ProductEntity entity =
        jpaRepository.findById(id.value()).orElseThrow(() -> new IllegalStateException("Product " + id + " not found"));
    entity.changeStatus(status);
    try {
      Product result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("updateStatus id={} -> {}", id, result.status());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Product " + id + " was modified concurrently");
    }
  }

  /**
   * @param id the product to check
   * @return whether it exists
   */
  @Override
  public boolean existsById(ProductId id) {
    LOGGER.debug("existsById id={}", id);
    boolean result = jpaRepository.existsById(id.value());
    LOGGER.debug("existsById id={} -> {}", id, result);
    return result;
  }

  /**
   * @param slug the slug to check
   * @return whether a product already uses it
   */
  @Override
  public boolean existsBySlug(String slug) {
    LOGGER.debug("existsBySlug slug={}", slug);
    boolean result = jpaRepository.existsBySlug(slug);
    LOGGER.debug("existsBySlug slug={} -> {}", slug, result);
    return result;
  }

  /**
   * @param id the product being previewed for deletion
   * @return the impact preview
   */
  @Override
  public ProductDeletionImpact deletionImpact(ProductId id) {
    LOGGER.debug("deletionImpact id={}", id);
    ProductDeletionImpact result = jdbcRepository.deletionImpact(id.value());
    LOGGER.debug("deletionImpact id={} -> deletable={}", id, result.deletable());
    return result;
  }

  /**
   * Translates a database constraint violation from {@link #save} into the business exception it
   * represents. The constraint's name never reaches the client (06-validation-conventions.md).
   *
   * @param violation the low-level constraint violation caught around the save
   * @param product the product that was being saved
   * @return the business exception to throw instead, or {@code violation} itself if the
   *     constraint is not one this module owns
   */
  private RuntimeException translateSave(DataIntegrityViolationException violation, Product product) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save id={} -> constraint violation: {}", product.id(), message);
    if (message.contains("uq_products_slug")) {
      return new ProductAlreadyExistsException("A product with slug '" + product.slug().value() + "' already exists");
    }
    return violation;
  }
}
