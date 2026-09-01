package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.port.in.AutocompleteProductsUseCase;
import com.floristeriarosy.application.product.port.out.ProductSearchPort;
import com.floristeriarosy.application.product.query.AutocompleteProductsQuery;
import java.util.List;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link AutocompleteProductsUseCase}: trigram autocomplete for the search bar's
 * dropdown (ADR-006). Text normalization (diacritics, case) happens in the persistence adapter,
 * the same place it happens when {@code search_text} is written — application never depends on
 * that infrastructure utility.
 */
@Service
public class AutocompleteProductsService implements AutocompleteProductsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteProductsService.class);

  /** Suggestions are for a dropdown, not a full listing — this is not a request parameter. */
  private static final int SUGGESTION_LIMIT = 10;

  private final ProductSearchPort searchPort;

  /**
   * @param searchPort runs the trigram autocomplete query
   */
  public AutocompleteProductsService(ProductSearchPort searchPort) {
    this.searchPort = searchPort;
  }

  /**
   * @param query the text typed so far
   * @return the matching visible product names and slugs, most similar first, capped at {@link
   *     #SUGGESTION_LIMIT}
   */
  @Override
  public List<ProductSuggestionDto> execute(AutocompleteProductsQuery query) {
    LOGGER.debug("autocompleteProducts q={}", Encode.forJava(query.q()));

    List<ProductSuggestionDto> result = searchPort.autocomplete(query.q(), SUGGESTION_LIMIT);

    LOGGER.debug("autocompleteProducts -> count={}", result.size());
    return result;
  }
}
