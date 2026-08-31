package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

/** {@code ACTIVE}/{@code INACTIVE} transition (category.md, section 7). */
public interface ChangeCategoryStatusUseCase {

  /**
   * @param command id of the category and the status to set
   * @return the category with its (possibly unchanged) status
   */
  CategoryDto execute(ChangeCategoryStatusCommand command);
}
