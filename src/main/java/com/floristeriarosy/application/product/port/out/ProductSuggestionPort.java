package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;

/** Manages a product's suggested extras (ADR-003; product.md, section 8, section 3.6). */
public interface ProductSuggestionPort {

  /**
   * Replaces every suggestion for {@code id} with {@code extraProductIds}, sent complete
   * (product.md, section 4).
   *
   * @param id the product the suggestions are attached to
   * @param extraProductIds every {@code is_extra = true} product to suggest, in display order
   */
  void replaceSuggestions(ProductId id, List<ProductId> extraProductIds);

  /**
   * @param id the product whose suggestions to list
   * @return the suggested extras, already filtered by visibility (product.md, section 3.6)
   */
  List<ProductSummaryDto> findVisibleSuggestions(ProductId id);
}
