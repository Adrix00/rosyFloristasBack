package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.OrderNumberJdbcRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link OrderNumberPort} (order.md, rule 3.1). */
@Repository
public class OrderNumberPersistenceAdapter implements OrderNumberPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderNumberPersistenceAdapter.class);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final OrderNumberJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository reads the sequence
   */
  public OrderNumberPersistenceAdapter(OrderNumberJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param channel the sales channel
   * @return the next order number, e.g. {@code WEB-20260913-000001}
   */
  @Override
  public OrderNumber next(OrderChannel channel) {
    LOGGER.debug("next channel={}", channel);
    long sequence = jdbcRepository.nextSequenceValue();
    OrderNumber result =
        OrderNumber.of(channel + "-" + LocalDate.now().format(DATE_FORMAT) + "-" + String.format("%06d", sequence));
    LOGGER.debug("next channel={} -> {}", channel, result);
    return result;
  }
}
