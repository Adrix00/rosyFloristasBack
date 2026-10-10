package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.ShippingRateEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository for {@code shipping_rates} (order.md §3.7). */
public interface ShippingRateJpaRepository extends JpaRepository<ShippingRateEntity, UUID> {

  /**
   * @return every band, ordered by where it starts
   */
  @Query("select r from ShippingRateEntity r order by r.minKm, r.maxKm")
  List<ShippingRateEntity> findAllOrdered();
}
