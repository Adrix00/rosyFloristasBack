package com.floristeriarosy.infrastructure.web.controller.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.port.in.AddCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.in.RemoveCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.UpdateCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.application.cart.query.GetCartQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.exception.cart.CartLineLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartProductNotFoundException;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.cart.CartWebMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@link CartController}: cart.md §4 (endpoints) and §9 (error contract). */
@WebMvcTest(CartController.class)
@Import({CartWebMapper.class, SecurityConfig.class})
class CartControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private GetCartUseCase getCartUseCase;
  @MockitoBean private AddCartItemUseCase addCartItemUseCase;
  @MockitoBean private UpdateCartItemUseCase updateCartItemUseCase;
  @MockitoBean private RemoveCartItemUseCase removeCartItemUseCase;
  @MockitoBean private ClearCartUseCase clearCartUseCase;
  @MockitoBean private ValidateCartUseCase validateCartUseCase;

  private CartDto cartWithOneItem(UUID cartId, UUID customerId, UUID productId) {
    CartItemDto item =
        new CartItemDto(
            productId,
            "Ramo",
            "ramo",
            null,
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            false,
            2,
            new BigDecimal("20.00"),
            5);
    return new CartDto(cartId, customerId, "session-token", List.of(item), new BigDecimal("20.00"), 2);
  }

  @Test
  void getCartOnAFirstVisitWithNoCookieReturnsAnEmptyCartWithoutCreatingOne() throws Exception {
    when(getCartUseCase.execute(any(GetCartQuery.class))).thenReturn(CartDto.empty());

    mockMvc
        .perform(get("/api/v1/cart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.itemCount").value(0))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void getCartWithAValidCustomerJwtIdentifiesByCustomerIdInsteadOfTheCookie() throws Exception {
    UUID customerId = UUID.randomUUID();
    ArgumentCaptor<GetCartQuery> captor = ArgumentCaptor.forClass(GetCartQuery.class);
    when(getCartUseCase.execute(captor.capture())).thenReturn(CartDto.empty());

    mockMvc
        .perform(
            get("/api/v1/cart")
                .with(jwt().jwt(jwt -> jwt.subject(customerId.toString()).claim("subject_type", "CUSTOMER")))
                .cookie(new Cookie("cart_session", "some-guest-token")))
        .andExpect(status().isOk());

    assertThat(captor.getValue().customerId()).isEqualTo(customerId);
  }

  @Test
  void getCartWithAnAdminBearerTokenIsTreatedAsAnonymousNotAsACustomerCart() throws Exception {
    ArgumentCaptor<GetCartQuery> captor = ArgumentCaptor.forClass(GetCartQuery.class);
    when(getCartUseCase.execute(captor.capture())).thenReturn(CartDto.empty());

    mockMvc
        .perform(
            get("/api/v1/cart")
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN"))))
        .andExpect(status().isOk());

    assertThat(captor.getValue().customerId()).isNull();
  }

  @Test
  void addItemAsAGuestSetsTheCartSessionCookieOnce200() throws Exception {
    UUID productId = UUID.randomUUID();
    UUID cartId = UUID.randomUUID();
    when(addCartItemUseCase.execute(any())).thenReturn(cartWithOneItem(cartId, null, productId));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itemCount").value(2))
        .andExpect(header().string("Set-Cookie", containsString("cart_session=session-token")));
  }

  @Test
  void addItemAsAnAuthenticatedCustomerNeverSetsTheGuestCookie() throws Exception {
    UUID customerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID cartId = UUID.randomUUID();
    when(addCartItemUseCase.execute(any())).thenReturn(cartWithOneItem(cartId, customerId, productId));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject(customerId.toString()).claim("subject_type", "CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void addItemWithoutCsrfTokenIsRejectedWithForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":2}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void addItemWithAQuantityAboveNinetyNineFailsBeanValidationWithTheCartValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":100}"))
        .andExpect(status().is(422))
        .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.code").value("CART_VALIDATION_FAILED"));
  }

  @Test
  void addItemForAProductThatIsNotVisibleReturns404WithCartProductNotFoundCode() throws Exception {
    when(addCartItemUseCase.execute(any())).thenThrow(new CartProductNotFoundException("not visible"));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_FOUND"));
  }

  @Test
  void addItemBeyondRealStockReturns422WithTheRealAvailableQuantity() throws Exception {
    when(addCartItemUseCase.execute(any()))
        .thenThrow(new CartInsufficientStockException("only 3 left", 3));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":5}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("CART_INSUFFICIENT_STOCK"))
        .andExpect(jsonPath("$.availableQuantity").value(3));
  }

  @Test
  void addItemPastTheLineLimitReturns422WithCartLineLimitExceededCode() throws Exception {
    when(addCartItemUseCase.execute(any())).thenThrow(new CartLineLimitExceededException("50 lines already"));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("CART_LINE_LIMIT_EXCEEDED"));
  }

  @Test
  void addItemPastNinetyNineOnAnExistingLineReturns422WithCartItemLimitExceededCode() throws Exception {
    when(addCartItemUseCase.execute(any())).thenThrow(new CartItemLimitExceededException("99 already"));

    mockMvc
        .perform(
            post("/api/v1/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("CART_ITEM_LIMIT_EXCEEDED"));
  }

  @Test
  void updateItemFixesTheQuantityAndReturns200() throws Exception {
    UUID productId = UUID.randomUUID();
    when(updateCartItemUseCase.execute(any())).thenReturn(cartWithOneItem(UUID.randomUUID(), null, productId));

    mockMvc
        .perform(
            patch("/api/v1/cart/items/" + productId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
        .andExpect(status().isOk());
  }

  @Test
  void updateItemWithoutCsrfTokenIsRejectedWithForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/cart/items/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateItemNotInTheCartReturns404WithCartItemNotFoundCode() throws Exception {
    when(updateCartItemUseCase.execute(any())).thenThrow(new CartItemNotFoundException("no such line"));

    mockMvc
        .perform(
            patch("/api/v1/cart/items/" + UUID.randomUUID())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
  }

  @Test
  void removeItemReturns200WithTheResultingCartNotA204() throws Exception {
    UUID productId = UUID.randomUUID();
    when(removeCartItemUseCase.execute(any())).thenReturn(CartDto.empty());

    mockMvc
        .perform(delete("/api/v1/cart/items/" + productId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void removeItemWithoutCsrfTokenIsRejectedWithForbidden() throws Exception {
    mockMvc.perform(delete("/api/v1/cart/items/" + UUID.randomUUID())).andExpect(status().isForbidden());
  }

  @Test
  void removeItemNotInTheCartReturns404WithCartItemNotFoundCode() throws Exception {
    when(removeCartItemUseCase.execute(any())).thenThrow(new CartItemNotFoundException("no such line"));

    mockMvc
        .perform(delete("/api/v1/cart/items/" + UUID.randomUUID()).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
  }

  @Test
  void clearCartReturns200WithTheNowEmptyCartNotA204() throws Exception {
    when(clearCartUseCase.execute(any())).thenReturn(CartDto.empty());

    mockMvc
        .perform(delete("/api/v1/cart").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void clearCartWithoutCsrfTokenIsRejectedWithForbidden() throws Exception {
    mockMvc.perform(delete("/api/v1/cart")).andExpect(status().isForbidden());
  }

  @Test
  void getValidationReturnsTheValidationBodyShape() throws Exception {
    when(validateCartUseCase.execute(any())).thenReturn(new CartValidationDto(true, List.of(), List.of()));

    mockMvc
        .perform(get("/api/v1/cart/validation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.removedItems").isEmpty())
        .andExpect(jsonPath("$.insufficientStockItems").isEmpty());
  }
}
