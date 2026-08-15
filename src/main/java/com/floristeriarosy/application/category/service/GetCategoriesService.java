package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.domain.model.category.Category;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetCategoriesService implements GetCategoriesUseCase {

    @Override
    public List<Category> execute(GetCategoriesQuery query) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
