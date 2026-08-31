package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoryQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.CategoryStatus;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetCategoryUseCase}: public lookup by UUID or slug. */
@Service
public class GetCategoryService implements GetCategoryUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCategoryService.class);

  private final CategoryReadPort readPort;

  /**
   * @param readPort resolves the category by id or by slug
   */
  public GetCategoryService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  /**
   * Looks up one category. Public endpoint: an {@code INACTIVE} category is reported as not found,
   * never as forbidden (category.md, section 4).
   *
   * @param query a UUID or a slug
   * @return the matching, {@code ACTIVE} category
   * @throws CategoryNotFoundException no {@code ACTIVE} category matches {@code query.idOrSlug()}
   */
  @Override
  public CategoryDto execute(GetCategoryQuery query) {
    LOGGER.debug("getCategory idOrSlug={}", LogSanitizer.sanitize(query.idOrSlug()));

    Category category =
        lookUp(query.idOrSlug())
            .filter(found -> found.status() == CategoryStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new CategoryNotFoundException(
                        "Category " + LogSanitizer.sanitize(query.idOrSlug()) + " not found"));
    CategoryDto result = CategoryDtoMapper.toDto(category);

    LOGGER.debug("getCategory -> id={} slug={}", result.id(), result.slug());
    return result;
  }

  /**
   * Resolves the identifier: a valid UUID looks up by id, anything else looks up by slug.
   *
   * @param idOrSlug the raw path segment from the request
   * @return the matching category, of any status, if one exists
   */
  private Optional<Category> lookUp(String idOrSlug) {
    try {
      return readPort.findById(CategoryId.of(UUID.fromString(idOrSlug)));
    } catch (IllegalArgumentException notAUuid) {
      return readPort.findBySlug(idOrSlug);
    }
  }
}
