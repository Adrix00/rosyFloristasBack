package com.floristeriarosy.infrastructure.web.controller.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.product.command.ChangeProductStatusCommand;
import com.floristeriarosy.application.product.command.CreateProductCommand;
import com.floristeriarosy.application.product.command.UpdateProductCategoriesCommand;
import com.floristeriarosy.application.product.command.UpdateProductCommand;
import com.floristeriarosy.application.product.command.UpdateProductExtrasCommand;
import com.floristeriarosy.application.product.command.UpdateProductImagesCommand;
import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.in.ChangeInventoryModeUseCase;
import com.floristeriarosy.application.product.port.in.ChangeProductStatusUseCase;
import com.floristeriarosy.application.product.port.in.CreateProductUseCase;
import com.floristeriarosy.application.product.port.in.DeleteProductUseCase;
import com.floristeriarosy.application.product.port.in.GetProductDeletionImpactUseCase;
import com.floristeriarosy.application.product.port.in.GetProductExtrasUseCase;
import com.floristeriarosy.application.product.port.in.GetProductUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductCategoriesUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductExtrasUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductImagesUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductUseCase;
import com.floristeriarosy.application.product.query.GetProductQuery;
import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.exception.product.ProductNotAnExtraException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductStockRequiredException;
import com.floristeriarosy.domain.exception.product.ProductWithoutCategoryException;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.product.ProductWebMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({ProductWebMapper.class, SecurityConfig.class})
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private CreateProductUseCase createProductUseCase;
  @MockitoBean private UpdateProductUseCase updateProductUseCase;
  @MockitoBean private ChangeProductStatusUseCase changeProductStatusUseCase;
  @MockitoBean private UpdateProductCategoriesUseCase updateProductCategoriesUseCase;
  @MockitoBean private UpdateProductImagesUseCase updateProductImagesUseCase;
  @MockitoBean private UpdateProductExtrasUseCase updateProductExtrasUseCase;
  @MockitoBean private ChangeInventoryModeUseCase changeInventoryModeUseCase;
  @MockitoBean private DeleteProductUseCase deleteProductUseCase;
  @MockitoBean private GetProductUseCase getProductUseCase;
  @MockitoBean private GetProductExtrasUseCase getProductExtrasUseCase;
  @MockitoBean private GetProductDeletionImpactUseCase getProductDeletionImpactUseCase;

  private ProductDto dto(UUID id, String name, ProductStatus status) {
    return new ProductDto(
        id,
        name,
        "ramo-de-rosas",
        "descripcion",
        new BigDecimal("19.99"),
        new BigDecimal("19.99"),
        false,
        status,
        false,
        Map.of(),
        List.of(),
        List.of(),
        null,
        false,
        Instant.now(),
        Instant.now());
  }

  @Test
  void createReturns201() throws Exception {
    UUID id = UUID.randomUUID();
    when(createProductUseCase.execute(any(CreateProductCommand.class))).thenReturn(dto(id, "Ramo de rosas", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            post("/api/v1/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Ramo de rosas\",\"price\":19.99,\"categoryIds\":[\""
                        + UUID.randomUUID()
                        + "\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("ramo-de-rosas"));
  }

  @Test
  void createWithoutAnyCategoryReturns422WithWithoutCategoryCode() throws Exception {
    when(createProductUseCase.execute(any(CreateProductCommand.class)))
        .thenThrow(new ProductWithoutCategoryException("no category"));

    mockMvc
        .perform(
            post("/api/v1/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ramo\",\"price\":10.00,\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("PRODUCT_WITHOUT_CATEGORY"));
  }

  @Test
  void createWithBlankNameReturns422WithProductValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":10.00,\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().is(422))
        .andExpect(
            content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.code").value("PRODUCT_VALIDATION_FAILED"));
  }

  @Test
  void getOneReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(getProductUseCase.execute(any(GetProductQuery.class))).thenReturn(dto(id, "Ramo de rosas", ProductStatus.ACTIVE));

    mockMvc.perform(get("/api/v1/products/ramo-de-rosas")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void getOneOfANonVisibleProductReturns404WithProductNotFoundCode() throws Exception {
    when(getProductUseCase.execute(any(GetProductQuery.class))).thenThrow(new ProductNotFoundException("not found"));

    mockMvc
        .perform(get("/api/v1/products/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void getExtrasReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    ProductSummaryDto extra =
        new ProductSummaryDto(UUID.randomUUID(), "Bombones", "bombones", BigDecimal.TEN, BigDecimal.TEN, false, null);
    when(getProductExtrasUseCase.execute(any())).thenReturn(List.of(extra));

    mockMvc
        .perform(get("/api/v1/products/" + id + "/extras"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug").value("bombones"));
  }

  @Test
  void getDeletionImpactReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(getProductDeletionImpactUseCase.execute(any()))
        .thenReturn(new ProductDeletionImpact(true, List.of(), 0, 0, 0));

    mockMvc
        .perform(get("/api/v1/products/" + id + "/deletion-impact"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletable").value(true));
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductUseCase.execute(any(UpdateProductCommand.class)))
        .thenReturn(dto(id, "Ramo actualizado", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            put("/api/v1/products/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ramo actualizado\",\"price\":19.99}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ramo actualizado"));
  }

  @Test
  void updateADiscontinuedProductReturns409WithProductDiscontinuedCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductUseCase.execute(any(UpdateProductCommand.class)))
        .thenThrow(new ProductDiscontinuedException("discontinued"));

    mockMvc
        .perform(
            put("/api/v1/products/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ramo\",\"price\":19.99}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));
  }

  @Test
  void changeStatusReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(changeProductStatusUseCase.execute(any(ChangeProductStatusCommand.class)))
        .thenReturn(dto(id, "Ramo", ProductStatus.INACTIVE));

    mockMvc
        .perform(
            patch("/api/v1/products/" + id + "/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  @Test
  void reactivatingADiscontinuedProductReturns409WithProductDiscontinuedCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(changeProductStatusUseCase.execute(any(ChangeProductStatusCommand.class)))
        .thenThrow(new ProductDiscontinuedException("discontinued is terminal"));

    mockMvc
        .perform(
            patch("/api/v1/products/" + id + "/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));
  }

  @Test
  void updateCategoriesReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductCategoriesUseCase.execute(any(UpdateProductCategoriesCommand.class)))
        .thenReturn(dto(id, "Ramo", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            put("/api/v1/products/" + id + "/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  void emptyingCategoriesReturns422WithValidationCode() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/v1/products/" + id + "/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[]}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("PRODUCT_VALIDATION_FAILED"));
  }

  @Test
  void updateImagesReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductImagesUseCase.execute(any(UpdateProductImagesCommand.class)))
        .thenReturn(dto(id, "Ramo", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            put("/api/v1/products/" + id + "/images")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[]}"))
        .andExpect(status().isOk());
  }

  @Test
  void updateExtrasReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductExtrasUseCase.execute(any(UpdateProductExtrasCommand.class)))
        .thenReturn(dto(id, "Ramo", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            put("/api/v1/products/" + id + "/extras")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extraProductIds\":[]}"))
        .andExpect(status().isOk());
  }

  @Test
  void suggestingANonExtraProductReturns422WithNotAnExtraCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateProductExtrasUseCase.execute(any(UpdateProductExtrasCommand.class)))
        .thenThrow(new ProductNotAnExtraException("not an extra"));

    mockMvc
        .perform(
            put("/api/v1/products/" + id + "/extras")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extraProductIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AN_EXTRA"));
  }

  @Test
  void changeInventoryModeReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(changeInventoryModeUseCase.execute(any(ChangeInventoryModeCommand.class)))
        .thenReturn(dto(id, "Ramo", ProductStatus.ACTIVE));

    mockMvc
        .perform(
            patch("/api/v1/products/" + id + "/inventory")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managed\":true,\"stock\":10}"))
        .andExpect(status().isOk());
  }

  @Test
  void activatingInventoryWithoutStockReturns422WithStockRequiredCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(changeInventoryModeUseCase.execute(any(ChangeInventoryModeCommand.class)))
        .thenThrow(new ProductStockRequiredException("stock required"));

    mockMvc
        .perform(
            patch("/api/v1/products/" + id + "/inventory")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managed\":true,\"stock\":5}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("PRODUCT_STOCK_REQUIRED"));
  }

  @Test
  void deleteReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(delete("/api/v1/products/" + id).with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void deleteAProductWithHistoryReturns409WithHasHistoryCode() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new ProductHasHistoryException("has history")).when(deleteProductUseCase).execute(any());

    mockMvc
        .perform(delete("/api/v1/products/" + id).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_HAS_HISTORY"));
  }
}
