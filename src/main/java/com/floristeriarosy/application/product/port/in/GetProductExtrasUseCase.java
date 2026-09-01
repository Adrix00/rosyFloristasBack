package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.query.GetProductExtrasQuery;
import java.util.List;

/** Lists a product's suggested extras, already filtered by visibility (product.md, section 7, section 3.6). */
public interface GetProductExtrasUseCase {

  /**
   * @param query the product whose suggested extras to list
   * @return the visible suggested extras
   */
  List<ProductSummaryDto> execute(GetProductExtrasQuery query);
}
