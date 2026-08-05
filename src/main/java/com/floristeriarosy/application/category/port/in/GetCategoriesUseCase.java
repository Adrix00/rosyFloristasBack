package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.domain.model.category.Category;
import java.util.List;

public interface GetCategoriesUseCase {

    List<Category> execute(GetCategoriesQuery query);

}
