package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;

/** Manages a product's category associations (ADR-003; product.md, section 8). */
public interface ProductCategoryPort {

  /**
   * Replaces every category association for {@code id} with {@code categoryIds}, sent complete —
   * a partial send would leave the previous set half-updated (product.md, section 4).
   *
   * @param id the product whose categories are being set
   * @param categoryIds every category id the product should belong to
   */
  void replaceCategories(ProductId id, List<CategoryId> categoryIds);

  /**
   * @param id the product to look up
   * @return the categories it belongs to
   */
  List<ProductCategoryRef> findCategories(ProductId id);
}
