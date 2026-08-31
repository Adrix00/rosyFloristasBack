package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;

public interface GetCategoryImpactUseCase {

  CategoryImpact execute(GetCategoryImpactQuery query);
}
