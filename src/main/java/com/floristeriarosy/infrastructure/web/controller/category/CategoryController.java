package com.floristeriarosy.infrastructure.web.controller.category;

import com.floristeriarosy.infrastructure.web.request.category.ChangeCategoryStatusRequest;
import com.floristeriarosy.infrastructure.web.request.category.CreateCategoryRequest;
import com.floristeriarosy.infrastructure.web.request.category.UpdateCategoryRequest;
import com.floristeriarosy.infrastructure.web.response.category.CategoryResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategorySummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  @PostMapping
  public ResponseEntity<CategoryResponse> create(@RequestBody CreateCategoryRequest request) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping
  public ResponseEntity<List<CategorySummaryResponse>> getAll() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping("/{id}")
  public ResponseEntity<CategoryResponse> getById(@PathVariable UUID id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PutMapping("/{id}")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id, @RequestBody UpdateCategoryRequest request) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<CategoryResponse> changeStatus(
      @PathVariable UUID id, @RequestBody ChangeCategoryStatusRequest request) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
