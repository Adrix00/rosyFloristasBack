package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;

/**
 * Manages a product's image gallery (ADR-003; product.md, section 8). Associates rows of {@code
 * images} — never uploads or stores binaries; that is image.md.
 */
public interface ProductImagePort {

  /**
   * Replaces the whole gallery with {@code images}, sent complete and in order — the list index
   * becomes {@code position} (product.md, section 4).
   *
   * @param id the product whose gallery is being set
   * @param images every image the gallery should contain, in display order
   */
  void replaceImages(ProductId id, List<ProductImageAssignment> images);

  /**
   * @param id the product to look up
   * @return its gallery, ordered by position
   */
  List<ProductImageRef> findImages(ProductId id);
}
