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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryPersistenceAdapter
    implements CategoryReadPort, CategoryWritePort, CategoryExistencePort, CategoryProductsPort {

  private final CategoryJpaRepository jpaRepository;
  private final CategoryJdbcRepository jdbcRepository;
  private final CategoryPersistenceMapper mapper;

  public CategoryPersistenceAdapter(
      CategoryJpaRepository jpaRepository,
      CategoryJdbcRepository jdbcRepository,
      CategoryPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  @Override
  public Optional<Category> findById(CategoryId id) {
    return jpaRepository.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public Optional<Category> findBySlug(String slug) {
    return jpaRepository.findBySlug(slug).map(mapper::toDomain);
  }

  @Override
  public List<Category> findAllActive() {
    return jdbcRepository.findAllActive();
  }

  @Override
  public List<Category> findAll() {
    return jdbcRepository.findAll();
  }

  @Override
  public Category save(Category category) {
    CategoryEntity entity = mapper.toEntity(category);
    try {
      return mapper.toDomain(jpaRepository.save(entity));
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation, category);
    }
  }

  @Override
  public void delete(CategoryId id) {
    jpaRepository.deleteById(id.value());
  }

  @Override
  public void updatePositions(List<CategoryId> orderedIds) {
    jdbcRepository.updatePositions(orderedIds.stream().map(CategoryId::value).toList());
  }

  @Override
  public boolean existsById(CategoryId id) {
    return jpaRepository.existsById(id.value());
  }

  @Override
  public boolean existsBySlug(String slug) {
    return jpaRepository.existsBySlug(slug);
  }

  @Override
  public long countByCategory(CategoryId id) {
    return jdbcRepository.countByCategory(id.value());
  }

  @Override
  public List<CategoryProductRef> findLosingVisibility(CategoryId id) {
    return jdbcRepository.findLosingVisibility(id.value());
  }

  @Override
  public List<CategoryProductRef> findLeftWithoutCategory(CategoryId id) {
    return jdbcRepository.findLeftWithoutCategory(id.value());
  }

  // El nombre de la constraint nunca llega al cliente (06-validation-conventions.md): se
  // inspecciona aquí, server-side, y se traduce a una excepción de negocio.
  private RuntimeException translate(DataIntegrityViolationException violation, Category category) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
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
