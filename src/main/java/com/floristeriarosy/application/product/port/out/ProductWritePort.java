package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;

/** Persists changes to a product (ADR-003; product.md, section 8). */
public interface ProductWritePort {

  /**
   * @param product the product to insert or update
   * @return the saved product, with timestamps populated by the database
   * @throws ProductAlreadyExistsException the slug unique constraint was violated
   * @throws ResourceModifiedException the product was changed concurrently (ADR-009)
   */
  Product save(Product product);

  /**
   * @param id the product to delete; {@code product_categories}, {@code product_images} and
   *     {@code product_suggestions} rows cascade
   * @throws ProductHasHistoryException the product has orders, stock movements or purchases
   *     referencing it (product.md, section 3.10)
   */
  void delete(ProductId id);

  /**
   * A narrow write of just the status column, still guarded by the row's {@code version}
   * (product.md, section 8).
   *
   * @param id the product to change
   * @param status the new status
   * @return the updated product
   * @throws ResourceModifiedException the product was changed concurrently (ADR-009)
   */
  Product updateStatus(ProductId id, ProductStatus status);
}
