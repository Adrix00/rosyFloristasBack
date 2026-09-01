package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.in.SearchProductsUseCase;
import com.floristeriarosy.application.product.port.out.ProductSearchPort;
import com.floristeriarosy.application.product.query.SearchProductsQuery;
import com.floristeriarosy.domain.exception.product.ProductAttributeTypeMismatchException;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link SearchProductsUseCase}: validates {@code attr.{key}} filters against the
 * declared attribute definitions, type-coerces their values, then delegates the actual query to
 * {@link ProductSearchPort} (ADR-006).
 */
@Service
public class SearchProductsService implements SearchProductsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchProductsService.class);

  private final ProductSearchPort searchPort;
  private final AttributeDefinitionPort attributeDefinitionPort;

  /**
   * @param searchPort runs the full-text search
   * @param attributeDefinitionPort resolves each {@code attr.{key}} filter's declared type and
   *     filterable flag
   */
  public SearchProductsService(
      ProductSearchPort searchPort, AttributeDefinitionPort attributeDefinitionPort) {
    this.searchPort = searchPort;
    this.attributeDefinitionPort = attributeDefinitionPort;
  }

  /**
   * @param query the combinable filters and the requested page
   * @return the matching visible products, paginated
   * @throws ProductAttributeUndeclaredException an {@code attr.{key}} filter is not declared, or
   *     not marked {@code filterable} (product.md, section 4)
   * @throws ProductAttributeTypeMismatchException an {@code attr.{key}} filter's value does not
   *     respect the key's declared {@code data_type}
   */
  @Override
  public PageResult<ProductSummaryDto> execute(SearchProductsQuery query) {
    LOGGER.debug(
        "searchProducts q={} category={} minPrice={} maxPrice={} onSale={} attributeKeys={}"
            + " page={} size={}",
        query.q() == null ? null : Encode.forJava(query.q()),
        query.category() == null ? null : Encode.forJava(query.category()),
        query.minPrice(),
        query.maxPrice(),
        query.onSale(),
        query.attributeFilters().keySet(),
        query.page(),
        query.size());

    ProductSearchCriteria criteria =
        new ProductSearchCriteria(
            query.q(),
            query.category(),
            query.minPrice(),
            query.maxPrice(),
            query.onSale(),
            typedAttributeFilters(query.attributeFilters()),
            query.page(),
            query.size());
    PageResult<ProductSummaryDto> result = searchPort.search(criteria);

    LOGGER.debug("searchProducts -> totalElements={}", result.totalElements());
    return result;
  }

  /**
   * @param rawFilters the raw {@code attr.{key}=value} query parameters
   * @return {@code rawFilters}, keyed the same, with every value coerced to the type its
   *     declared {@code data_type} requires
   * @throws ProductAttributeUndeclaredException a key is not declared, or not filterable
   * @throws ProductAttributeTypeMismatchException a value does not parse as its declared type
   */
  private Map<String, Object> typedAttributeFilters(Map<String, String> rawFilters) {
    Map<String, Object> typed = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : rawFilters.entrySet()) {
      AttributeDefinition definition =
          attributeDefinitionPort
              .findByKey(entry.getKey())
              .filter(AttributeDefinition::filterable)
              .orElseThrow(
                  () ->
                      new ProductAttributeUndeclaredException(
                          "Attribute key '" + entry.getKey() + "' is not declared or not filterable"));
      typed.put(entry.getKey(), coerce(definition, entry.getValue()));
    }
    return typed;
  }

  /**
   * @param definition the declared definition for the filtered key
   * @param rawValue the raw query-parameter value
   * @return {@code rawValue} coerced to the type {@code definition.dataType()} requires
   * @throws ProductAttributeTypeMismatchException {@code rawValue} does not parse as that type
   */
  private Object coerce(AttributeDefinition definition, String rawValue) {
    // Deliberately an if/else chain, not a switch: javac emits a synthetic $SwitchMap class for
    // any switch on an enum, and this codebase's ArchUnit naming/package rules scan every class
    // file in `application..service`, including synthetic ones.
    if (definition.dataType() == AttributeDataType.NUMBER) {
      return parseNumber(definition, rawValue);
    }
    if (definition.dataType() == AttributeDataType.BOOLEAN) {
      return parseBoolean(definition, rawValue);
    }
    return rawValue;
  }

  /**
   * @param definition the declared definition for the filtered key
   * @param rawValue the raw query-parameter value
   * @return {@code rawValue} parsed as a decimal
   * @throws ProductAttributeTypeMismatchException {@code rawValue} is not a valid number
   */
  private BigDecimal parseNumber(AttributeDefinition definition, String rawValue) {
    try {
      return new BigDecimal(rawValue);
    } catch (NumberFormatException notANumber) {
      throw new ProductAttributeTypeMismatchException(
          "Attribute '" + definition.attributeKey() + "' expects NUMBER but got a non-numeric value");
    }
  }

  /**
   * @param definition the declared definition for the filtered key
   * @param rawValue the raw query-parameter value
   * @return {@code rawValue} parsed as a boolean
   * @throws ProductAttributeTypeMismatchException {@code rawValue} is neither {@code "true"} nor
   *     {@code "false"}
   */
  private Boolean parseBoolean(AttributeDefinition definition, String rawValue) {
    if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
      return Boolean.parseBoolean(rawValue);
    }
    throw new ProductAttributeTypeMismatchException(
        "Attribute '" + definition.attributeKey() + "' expects BOOLEAN but got a non-boolean value");
  }
}
