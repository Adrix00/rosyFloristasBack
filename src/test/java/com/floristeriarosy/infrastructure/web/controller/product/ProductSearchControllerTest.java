package com.floristeriarosy.infrastructure.web.controller.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.in.AutocompleteProductsUseCase;
import com.floristeriarosy.application.product.port.in.GetProductsUseCase;
import com.floristeriarosy.application.product.port.in.SearchProductsUseCase;
import com.floristeriarosy.application.product.query.GetProductsQuery;
import com.floristeriarosy.application.product.query.SearchProductsQuery;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.product.ProductWebMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSearchController.class)
@Import({ProductWebMapper.class, SecurityConfig.class})
class ProductSearchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchProductsUseCase searchProductsUseCase;
  @MockitoBean private AutocompleteProductsUseCase autocompleteProductsUseCase;
  @MockitoBean private GetProductsUseCase getProductsUseCase;

  private ProductSummaryDto summary() {
    return new ProductSummaryDto(
        java.util.UUID.randomUUID(), "Ramo de rosas", "ramo-de-rosas", BigDecimal.TEN, BigDecimal.TEN, false, null);
  }

  @Test
  void searchReturns200() throws Exception {
    when(searchProductsUseCase.execute(any(SearchProductsQuery.class)))
        .thenReturn(new PageResult<>(List.of(summary()), 1, 0, 20));

    mockMvc
        .perform(get("/api/v1/products").param("q", "rosas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].slug").value("ramo-de-rosas"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void searchWithAnUndeclaredAttributeFilterReturns422WithAttributeUndeclaredCode() throws Exception {
    when(searchProductsUseCase.execute(any(SearchProductsQuery.class)))
        .thenThrow(new ProductAttributeUndeclaredException("not declared"));

    mockMvc
        .perform(get("/api/v1/products").param("attr.color", "rojo"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("PRODUCT_ATTRIBUTE_UNDECLARED"));
  }

  @Test
  void suggestionsReturns200() throws Exception {
    when(autocompleteProductsUseCase.execute(any())).thenReturn(List.of(new ProductSuggestionDto("Rosas", "rosas")));

    mockMvc
        .perform(get("/api/v1/products/suggestions").param("q", "ros"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Rosas"));
  }

  @Test
  void getAllReturns200() throws Exception {
    when(getProductsUseCase.execute(any(GetProductsQuery.class)))
        .thenReturn(new PageResult<>(List.of(summary()), 1, 0, 20));

    mockMvc
        .perform(get("/api/v1/products/all").param("status", "INACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }
}
