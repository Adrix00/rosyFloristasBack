package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetOrdersQuery;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GetOrdersService} — authenticated customer's order list (order.md §4, §6). */
@ExtendWith(MockitoExtension.class)
class GetOrdersServiceTest {

  @Mock private OrderReadPort orderReadPort;

  private GetOrdersService service;

  @BeforeEach
  void setUp() {
    service = new GetOrdersService(orderReadPort);
  }

  @Test
  void returnsOrdersForCustomerNewestFirst() {
    UUID customerId = UUID.randomUUID();
    Order first =
        Order.reconstitute(
            OrderId.of(UUID.randomUUID()),
            OrderNumber.of("WEB-20260914-000001"),
            OrderChannel.WEB,
            OrderFulfillment.PICKUP,
            OrderStatus.PENDING,
            customerId,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            new BigDecimal("25.00"),
            BigDecimal.ZERO,
            Instant.now(),
            null,
            null,
            0L,
            Instant.now(),
            Instant.now());
    when(orderReadPort.findByCustomerId(customerId)).thenReturn(List.of(first));

    var result = service.execute(new GetOrdersQuery(customerId));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).orderNumber()).isEqualTo("WEB-20260914-000001");
  }

  @Test
  void emptyListWhenCustomerHasNoOrders() {
    UUID customerId = UUID.randomUUID();
    when(orderReadPort.findByCustomerId(customerId)).thenReturn(List.of());

    var result = service.execute(new GetOrdersQuery(customerId));

    assertThat(result).isEmpty();
  }
}
