package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductAdminListingCriteria;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.query.GetProductsQuery;
import com.floristeriarosy.domain.model.product.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductsServiceTest {

  @Mock private ProductReadPort readPort;

  private GetProductsService service;

  @Test
  void listsEveryProductRegardlessOfVisibility() {
    service = new GetProductsService(readPort);
    PageResult<ProductSummaryDto> page = new PageResult<>(List.of(), 0, 0, 20);
    when(readPort.findAllForAdmin(any(ProductAdminListingCriteria.class))).thenReturn(page);

    PageResult<ProductSummaryDto> result =
        service.execute(new GetProductsQuery(ProductStatus.INACTIVE, false, null, 0, 20));

    assertThat(result).isSameAs(page);
  }
}
