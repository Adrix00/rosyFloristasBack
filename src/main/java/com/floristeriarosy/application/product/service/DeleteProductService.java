package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.DeleteProductCommand;
import com.floristeriarosy.application.product.port.in.DeleteProductUseCase;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link DeleteProductUseCase}: permanently removes a product with no commercial history. */
@Service
@Transactional
public class DeleteProductService implements DeleteProductUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteProductService.class);

  private final ProductExistencePort existencePort;
  private final ProductWritePort writePort;

  /**
   * @param existencePort checks the product exists before attempting the delete
   * @param writePort performs the delete
   */
  public DeleteProductService(ProductExistencePort existencePort, ProductWritePort writePort) {
    this.existencePort = existencePort;
    this.writePort = writePort;
  }

  /**
   * Deletes the product. {@code product_categories}, {@code product_images} and {@code
   * product_suggestions} rows cascade (product.md, section 3.10).
   *
   * @param command id of the product to delete
   * @throws ProductNotFoundException {@code command.id()} does not exist
   * @throws ProductHasHistoryException the product has orders, stock movements or purchases
   *     referencing it
   */
  @Override
  public void execute(DeleteProductCommand command) {
    LOGGER.debug("deleteProduct id={}", command.id());

    ProductId id = ProductId.of(command.id());
    if (!existencePort.existsById(id)) {
      throw new ProductNotFoundException("Product " + id + " not found");
    }
    writePort.delete(id);

    LOGGER.debug("deleteProduct -> id={} deleted", id);
  }
}
