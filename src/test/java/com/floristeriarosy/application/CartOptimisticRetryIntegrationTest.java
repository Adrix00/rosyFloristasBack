package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.AddCartItemUseCase;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The cart's one-off retry after losing the {@code @Version} race (ADR-009 amendment) against a real
 * PostgreSQL. A retry inside the caller's own transaction cannot work: the failed attempt leaves its
 * stale, dirty entity in the Hibernate session and the retry's first query flushes it and fails again.
 * The conflict is provoked for real: another connection bumps the cart's version between the
 * service's read and its write.
 */
@Testcontainers
@SpringBootTest
class CartOptimisticRetryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AddCartItemUseCase addCartItemUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoSpyBean private CartProductAvailabilityPort availabilityPort;

  @Test
  void addingAnItemSucceedsAfterLosingTheVersionRaceOnce() {
    UUID cartId = UUID.randomUUID();
    UUID productId = seedProduct();
    String sessionToken = seedCart(cartId);
    AtomicBoolean raced = new AtomicBoolean();
    doReturn(true).when(availabilityPort).isVisible(any());
    doAnswer(
            call -> {
              if (raced.compareAndSet(false, true)) {
                bumpVersion(cartId);
              }
              return call.callRealMethod();
            })
        .when(availabilityPort)
        .availableStock(any());

    CartDto result =
        addCartItemUseCase.execute(new AddCartItemCommand(null, sessionToken, productId, 2));

    assertThat(raced).isTrue();
    assertThat(result.itemCount()).isEqualTo(2);
    assertThat(quantityOf(cartId)).isEqualTo(2);
  }

  @Test
  void theCallerStillGetsResourceModifiedNotTheRawLockingExceptionWhenTheRetryLosesToo() {
    UUID cartId = UUID.randomUUID();
    UUID productId = seedProduct();
    String sessionToken = seedCart(cartId);
    doReturn(true).when(availabilityPort).isVisible(any());
    doAnswer(
            call -> {
              bumpVersion(cartId);
              return call.callRealMethod();
            })
        .when(availabilityPort)
        .availableStock(any());

    assertThatThrownBy(
            () -> addCartItemUseCase.execute(new AddCartItemCommand(null, sessionToken, productId, 2)))
        .isInstanceOf(ResourceModifiedException.class);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", Integer.class, cartId))
        .isZero();
  }

  private UUID seedProduct() {
    UUID productId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, status) VALUES (?, ?, ?, 10.00, 'ACTIVE')",
        productId,
        "Cart retry " + productId,
        "cart-retry-" + productId);
    return productId;
  }

  private String seedCart(UUID cartId) {
    String sessionToken = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO carts (id, session_token, expires_at) VALUES (?, ?, now() + interval '1 day')",
        cartId,
        sessionToken);
    return sessionToken;
  }

  private void bumpVersion(UUID cartId) {
    jdbcTemplate.update("UPDATE carts SET version = version + 1 WHERE id = ?", cartId);
  }

  private Integer quantityOf(UUID cartId) {
    return jdbcTemplate.queryForObject(
        "SELECT quantity FROM cart_items WHERE cart_id = ?", Integer.class, cartId);
  }
}
