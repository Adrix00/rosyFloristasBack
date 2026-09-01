package com.floristeriarosy.infrastructure.persistence.jpa.discount.repository;

import com.floristeriarosy.infrastructure.persistence.entity.discount.DiscountEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link DiscountEntity}: writes and simple lookups (ADR-002). */
public interface DiscountJpaRepository extends JpaRepository<DiscountEntity, UUID> {}
