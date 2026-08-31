package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import java.util.List;

public interface GetCategoriesUseCase {

  List<CategoryDto> execute(GetCategoriesQuery query);
}
