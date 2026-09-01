package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.port.out.ProductSearchPort;
import com.floristeriarosy.application.product.query.AutocompleteProductsQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutocompleteProductsServiceTest {

  @Mock private ProductSearchPort searchPort;

  private AutocompleteProductsService service;

  @Test
  void delegatesToTheTrigramAutocompleteWithACappedLimit() {
    service = new AutocompleteProductsService(searchPort);
    ProductSuggestionDto suggestion = new ProductSuggestionDto("Rosas rojas", "rosas-rojas");
    when(searchPort.autocomplete("ros", 10)).thenReturn(List.of(suggestion));

    List<ProductSuggestionDto> result = service.execute(new AutocompleteProductsQuery("ros"));

    assertThat(result).containsExactly(suggestion);
  }
}
