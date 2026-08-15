package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.query.GetCategoryByIdQuery;
import com.floristeriarosy.domain.model.category.Category;

public interface GetCategoryByIdUseCase {

    Category execute(GetCategoryByIdQuery query);

}
