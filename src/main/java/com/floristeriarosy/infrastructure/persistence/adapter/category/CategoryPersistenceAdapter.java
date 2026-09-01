package com.floristeriarosy.infrastructure.persistence.adapter.category;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryProductsPort;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.exception.category.CategoryImageNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.infrastructure.persistence.entity.category.CategoryEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.category.repository.CategoryJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.category.repository.CategoryJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.category.CategoryPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements the category output ports (ADR-003): JPA for writes and simple lookups, JDBC for
 * ordered listings and the impact-preview joins (ADR-002).
 */
@Repository
public class CategoryPersistenceAdapter
    implements CategoryReadPort, CategoryWritePort, CategoryExistencePort, CategoryProductsPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(CategoryPersistenceAdapter.class);

  private final CategoryJpaRepository jpaRepository;
  private final CategoryJdbcRepository jdbcRepository;
  private final CategoryPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups by id/slug
   * @param jdbcRepository ordered listings and the impact-preview joins
   * @param mapper converts between the domain {@link Category} and the JPA {@link CategoryEntity}
   */
  public CategoryPersistenceAdapter(
      CategoryJpaRepository jpaRepository,
      CategoryJdbcRepository jdbcRepository,
      CategoryPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the category to load
   * @return the category, if it exists
   */
  @Override
  public Optional<Category> findById(CategoryId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Category> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param slug the category to load
   * @return the category, if it exists
   */
  @Override
  public Optional<Category> findBySlug(String slug) {
    // CodeQL's log-injection sanitizer recognition doesn't trace through LogSanitizer as a
    // helper method call, only a literal encode call on the tainted expression at the log site.
    LOGGER.debug("findBySlug slug={}", Encode.forJava(slug));
    Optional<Category> result = jpaRepository.findBySlug(slug).map(mapper::toDomain);
    LOGGER.debug("findBySlug slug={} -> found={}", Encode.forJava(slug), result.isPresent());
    return result;
  }

  /**
   * @return {@code ACTIVE} categories, ordered by position then name
   */
  @Override
  public List<Category> findAllActive() {
    LOGGER.debug("findAllActive");
    List<Category> result = jdbcRepository.findAllActive();
    LOGGER.debug("findAllActive -> count={}", result.size());
    return result;
  }

  /**
   * @return every category regardless of status, same order as {@link #findAllActive()}
   */
  @Override
  public List<Category> findAll() {
    LOGGER.debug("findAll");
    List<Category> result = jdbcRepository.findAll();
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }

  /**
   * @param category the category to insert or update
   * @return the saved category, with timestamps populated by the database
   * @throws CategoryAlreadyExistsException the slug unique constraint was violated
   * @throws CategoryImageNotFoundException the {@code image_id} foreign key was violated
   */
  @Override
  public Category save(Category category) {
    LOGGER.debug("save id={} slug={}", category.id(), category.slug());
    CategoryEntity entity = mapper.toEntity(category);
    try {
      Category result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation, category);
    }
  }

  /**
   * @param id the category to delete; {@code product_categories} rows cascade
   */
  @Override
  public void delete(CategoryId id) {
    LOGGER.debug("delete id={}", id);
    jpaRepository.deleteById(id.value());
    LOGGER.debug("delete id={} -> deleted", id);
  }

  /**
   * @param orderedIds every category id, in its new order
   */
  @Override
  public void updatePositions(List<CategoryId> orderedIds) {
    LOGGER.debug("updatePositions count={}", orderedIds.size());
    jdbcRepository.updatePositions(orderedIds.stream().map(CategoryId::value).toList());
    LOGGER.debug("updatePositions -> {} rows updated", orderedIds.size());
  }

  /**
   * @param id the category to check
   * @return whether it exists
   */
  @Override
  public boolean existsById(CategoryId id) {
    LOGGER.debug("existsById id={}", id);
    boolean result = jpaRepository.existsById(id.value());
    LOGGER.debug("existsById id={} -> {}", id, result);
    return result;
  }

  /**
   * @param slug the slug to check
   * @return whether a category already uses it
   */
  @Override
  public boolean existsBySlug(String slug) {
    LOGGER.debug("existsBySlug slug={}", slug);
    boolean result = jpaRepository.existsBySlug(slug);
    LOGGER.debug("existsBySlug slug={} -> {}", slug, result);
    return result;
  }

  /**
   * @param id the category to count products for
   * @return number of products associated with it, regardless of status
   */
  @Override
  public long countByCategory(CategoryId id) {
    LOGGER.debug("countByCategory id={}", id);
    long result = jdbcRepository.countByCategory(id.value());
    LOGGER.debug("countByCategory id={} -> {}", id, result);
    return result;
  }

  /**
   * @param id the category being previewed for deactivation
   * @return {@code ACTIVE} products for which {@code id} is their only {@code ACTIVE} category
   */
  @Override
  public List<CategoryProductRef> findLosingVisibility(CategoryId id) {
    LOGGER.debug("findLosingVisibility id={}", id);
    List<CategoryProductRef> result = jdbcRepository.findLosingVisibility(id.value());
    LOGGER.debug("findLosingVisibility id={} -> count={}", id, result.size());
    return result;
  }

  /**
   * @param id the category being previewed for deletion
   * @return products that would be left with zero categories if {@code id} is deleted
   */
  @Override
  public List<CategoryProductRef> findLeftWithoutCategory(CategoryId id) {
    LOGGER.debug("findLeftWithoutCategory id={}", id);
    List<CategoryProductRef> result = jdbcRepository.findLeftWithoutCategory(id.value());
    LOGGER.debug("findLeftWithoutCategory id={} -> count={}", id, result.size());
    return result;
  }

  /**
   * Translates a database constraint violation into the business exception it represents. The
   * constraint's name never reaches the client (06-validation-conventions.md).
   *
   * @param violation the low-level constraint violation caught around the save
   * @param category the category that was being saved
   * @return the business exception to throw instead, or {@code violation} itself if the constraint
   *     is not one of the two this module owns
   */
  private RuntimeException translate(DataIntegrityViolationException violation, Category category) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save id={} -> constraint violation: {}", category.id(), message);
    if (message.contains("uq_categories_slug")) {
      return new CategoryAlreadyExistsException(
          "A category with slug '" + category.slug().value() + "' already exists");
    }
    if (message.contains("image_id")) {
      return new CategoryImageNotFoundException(
          "imageId " + category.imageId() + " does not reference an existing image");
    }
    return violation;
  }
}
