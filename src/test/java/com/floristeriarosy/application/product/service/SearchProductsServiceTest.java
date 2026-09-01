package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.out.ProductSearchPort;
import com.floristeriarosy.application.product.query.SearchProductsQuery;
import com.floristeriarosy.domain.exception.product.ProductAttributeTypeMismatchException;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchProductsServiceTest {

  @Mock private ProductSearchPort searchPort;
  @Mock private AttributeDefinitionPort attributeDefinitionPort;

  private SearchProductsService service;

  private AttributeDefinition definition(String key, AttributeDataType dataType, boolean filterable) {
    return AttributeDefinition.create(AttributeDefinitionId.newId(), key, "Label", dataType, filterable, 0);
  }

  private SearchProductsQuery query(Map<String, String> attributeFilters) {
    return new SearchProductsQuery(null, null, null, null, false, attributeFilters, 0, 20);
  }

  @Test
  void delegatesToSearchPortWhenNoAttributeFiltersArePresent() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    PageResult<ProductSummaryDto> page = new PageResult<>(List.of(), 0, 0, 20);
    when(searchPort.search(any(ProductSearchCriteria.class))).thenReturn(page);

    PageResult<ProductSummaryDto> result = service.execute(query(Map.of()));

    assertThat(result).isSameAs(page);
  }

  @Test
  void coercesADeclaredNumberFilterToBigDecimal() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("altura"))
        .thenReturn(Optional.of(definition("altura", AttributeDataType.NUMBER, true)));
    when(searchPort.search(any(ProductSearchCriteria.class))).thenReturn(new PageResult<>(List.of(), 0, 0, 20));

    service.execute(query(Map.of("altura", "30")));

    ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
    verify(searchPort).search(captor.capture());
    assertThat(captor.getValue().attributeFilters()).containsEntry("altura", new BigDecimal("30"));
  }

  @Test
  void coercesADeclaredBooleanFilterToBoolean() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("riego"))
        .thenReturn(Optional.of(definition("riego", AttributeDataType.BOOLEAN, true)));
    when(searchPort.search(any(ProductSearchCriteria.class))).thenReturn(new PageResult<>(List.of(), 0, 0, 20));

    service.execute(query(Map.of("riego", "true")));

    ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
    verify(searchPort).search(captor.capture());
    assertThat(captor.getValue().attributeFilters()).containsEntry("riego", Boolean.TRUE);
  }

  @Test
  void leavesADeclaredTextFilterAsAString() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color"))
        .thenReturn(Optional.of(definition("color", AttributeDataType.TEXT, true)));
    when(searchPort.search(any(ProductSearchCriteria.class))).thenReturn(new PageResult<>(List.of(), 0, 0, 20));

    service.execute(query(Map.of("color", "rojo")));

    ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
    verify(searchPort).search(captor.capture());
    assertThat(captor.getValue().attributeFilters()).containsEntry("color", "rojo");
  }

  @Test
  void rejectsAnUndeclaredAttributeKey() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(query(Map.of("color", "rojo"))))
        .isInstanceOf(ProductAttributeUndeclaredException.class);
  }

  @Test
  void rejectsAnAttributeKeyThatIsNotFilterable() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color"))
        .thenReturn(Optional.of(definition("color", AttributeDataType.TEXT, false)));

    assertThatThrownBy(() -> service.execute(query(Map.of("color", "rojo"))))
        .isInstanceOf(ProductAttributeUndeclaredException.class);
  }

  @Test
  void rejectsANonNumericValueForANumberAttribute() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("altura"))
        .thenReturn(Optional.of(definition("altura", AttributeDataType.NUMBER, true)));

    assertThatThrownBy(() -> service.execute(query(Map.of("altura", "alto"))))
        .isInstanceOf(ProductAttributeTypeMismatchException.class);
  }

  @Test
  void rejectsANonBooleanValueForABooleanAttribute() {
    service = new SearchProductsService(searchPort, attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("riego"))
        .thenReturn(Optional.of(definition("riego", AttributeDataType.BOOLEAN, true)));

    assertThatThrownBy(() -> service.execute(query(Map.of("riego", "quizas"))))
        .isInstanceOf(ProductAttributeTypeMismatchException.class);
  }
}
