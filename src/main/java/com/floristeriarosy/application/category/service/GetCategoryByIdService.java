package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.port.in.GetCategoryByIdUseCase;
import com.floristeriarosy.application.category.query.GetCategoryByIdQuery;
import com.floristeriarosy.domain.model.category.Category;
import org.springframework.stereotype.Service;

@Service
public class GetCategoryByIdService implements GetCategoryByIdUseCase {

    @Override
    public Category execute(GetCategoryByIdQuery query) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
