package com.floristeriarosy.infrastructure.persistence.jdbc.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads {@code order_number_seq} directly (ADR-002: a sequence is not a JPA concern). */
@Repository
public class OrderNumberJdbcRepository {

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the sequence read
   */
  public OrderNumberJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return the next value of {@code order_number_seq}, atomic under concurrency
   */
  public long nextSequenceValue() {
    return jdbcTemplate.queryForObject("SELECT nextval('order_number_seq')", Long.class);
  }
}
