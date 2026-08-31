package com.floristeriarosy.infrastructure.web.controller.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.port.in.ChangeCategoryStatusUseCase;
import com.floristeriarosy.application.category.port.in.CreateCategoryUseCase;
import com.floristeriarosy.application.category.port.in.DeleteCategoryUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoryImpactUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoryUseCase;
import com.floristeriarosy.application.category.port.in.ReorderCategoriesUseCase;
import com.floristeriarosy.application.category.port.in.UpdateCategoryUseCase;
import com.floristeriarosy.application.category.query.GetCategoryQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.CategoryStatus;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.category.CategoryWebMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({CategoryWebMapper.class, SecurityConfig.class})
class CategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateCategoryUseCase createCategoryUseCase;
  @MockitoBean private UpdateCategoryUseCase updateCategoryUseCase;
  @MockitoBean private ChangeCategoryStatusUseCase changeCategoryStatusUseCase;
  @MockitoBean private ReorderCategoriesUseCase reorderCategoriesUseCase;
  @MockitoBean private DeleteCategoryUseCase deleteCategoryUseCase;
  @MockitoBean private GetCategoryUseCase getCategoryUseCase;
  @MockitoBean private GetCategoriesUseCase getCategoriesUseCase;
  @MockitoBean private GetCategoryImpactUseCase getCategoryImpactUseCase;

  @Test
  void createReturns201() throws Exception {
    CategoryDto dto =
        new CategoryDto(
            UUID.randomUUID(),
            "Ramos",
            "ramos",
            null,
            CategoryStatus.ACTIVE,
            null,
            0,
            Instant.now(),
            Instant.now());
    when(createCategoryUseCase.execute(any(CreateCategoryCommand.class))).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ramos\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("ramos"));
  }

  @Test
  void createWithBlankNameReturns422WithCategoryValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().is(422))
        .andExpect(
            content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.code").value("CATEGORY_VALIDATION_FAILED"));
  }

  @Test
  void getUnknownCategoryReturns404WithCategoryNotFoundCode() throws Exception {
    when(getCategoryUseCase.execute(any(GetCategoryQuery.class)))
        .thenThrow(new CategoryNotFoundException("Category not found"));

    mockMvc
        .perform(get("/api/v1/categories/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }
}
