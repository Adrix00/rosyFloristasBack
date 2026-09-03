package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.ChangeCategoryStatusUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link ChangeCategoryStatusUseCase}: {@code ACTIVE}/{@code INACTIVE} transition. */
@Service
@Transactional
public class ChangeCategoryStatusService implements ChangeCategoryStatusUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangeCategoryStatusService.class);

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  /**
   * @param readPort loads the category whose status is changing
   * @param writePort persists the new status
   */
  public ChangeCategoryStatusService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * Sets the category's status. Idempotent: setting the same status twice is a no-op, not an error
   * (category.md, section 10).
   *
   * @param command id of the category and the status to set
   * @return the category with its (possibly unchanged) status
   * @throws CategoryNotFoundException {@code command.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public CategoryDto execute(ChangeCategoryStatusCommand command) {
    LOGGER.debug("changeCategoryStatus id={} status={}", command.id(), command.status());

    CategoryId id = CategoryId.of(command.id());
    Category category =
        readPort
            .findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Category " + id + " not found"));

    category.changeStatus(command.status());
    CategoryDto result = CategoryDtoMapper.toDto(writePort.save(category));

    LOGGER.debug("changeCategoryStatus -> id={} status={}", result.id(), result.status());
    return result;
  }
}
