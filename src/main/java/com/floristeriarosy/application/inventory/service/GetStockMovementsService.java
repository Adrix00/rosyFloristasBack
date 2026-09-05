package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.GetStockMovementsUseCase;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.application.inventory.query.GetStockMovementsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.application.shared.support.AdminDisplayNameResolver;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Implements {@link GetStockMovementsUseCase}: a product's complete stock movement history. */
@Service
public class GetStockMovementsService implements GetStockMovementsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetStockMovementsService.class);

  private final ProductExistencePort productExistencePort;
  private final StockMovementReadPort readPort;
  private final AdminReadPort adminReadPort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param productExistencePort checks the source product exists
   * @param readPort lists the product's movement history
   * @param adminReadPort resolves a triggering admin's encrypted email, for {@code
   *     adminUserName} (inventory.md, section 6)
   * @param piiCryptoPort decrypts it (ADR-005)
   */
  public GetStockMovementsService(
      ProductExistencePort productExistencePort,
      StockMovementReadPort readPort,
      AdminReadPort adminReadPort,
      PiiCryptoPort piiCryptoPort) {
    this.productExistencePort = productExistencePort;
    this.readPort = readPort;
    this.adminReadPort = adminReadPort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param query the product whose history to list, plus the requested page
   * @return the matching movements, paginated, most recent first
   * @throws ProductNotFoundException {@code query.productId()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public PageResult<StockMovementDto> execute(GetStockMovementsQuery query) {
    LOGGER.debug(
        "getStockMovements productId={} page={} size={}",
        query.productId(),
        query.page(),
        query.size());

    ProductId productId = ProductId.of(query.productId());
    if (!productExistencePort.existsById(productId)) {
      throw new ProductNotFoundException("Product " + productId + " not found");
    }
    PageResult<StockMovementDto> result =
        readPort.findByProduct(productId, query.page(), query.size());
    PageResult<StockMovementDto> withNames =
        new PageResult<>(
            result.items().stream().map(this::withAdminUserName).toList(),
            result.totalElements(),
            result.page(),
            result.size());

    LOGGER.debug(
        "getStockMovements productId={} -> totalElements={}", productId, withNames.totalElements());
    return withNames;
  }

  /**
   * @param dto a movement fetched from persistence, with {@code adminUserName} always {@code
   *     null} (the row mapper cannot decrypt PII)
   * @return the same movement with {@code adminUserName} resolved, if it has one
   */
  private StockMovementDto withAdminUserName(StockMovementDto dto) {
    if (dto.adminUserId() == null) {
      return dto;
    }
    String adminUserName = AdminDisplayNameResolver.resolve(adminReadPort, piiCryptoPort, dto.adminUserId());
    return new StockMovementDto(
        dto.id(),
        dto.productId(),
        dto.type(),
        dto.quantity(),
        dto.resultingStock(),
        dto.adminUserId(),
        adminUserName,
        dto.note(),
        dto.createdAt());
  }
}
