package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.query.GetCategoryQuery;

public interface GetCategoryUseCase {

  CategoryDto execute(GetCategoryQuery query);
}
