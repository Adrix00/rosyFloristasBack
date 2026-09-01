package com.floristeriarosy.infrastructure.persistence.jpa.attribute.repository;

import com.floristeriarosy.infrastructure.persistence.entity.attribute.AttributeDefinitionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link AttributeDefinitionEntity}: writes and simple lookups (ADR-002). */
public interface AttributeDefinitionJpaRepository extends JpaRepository<AttributeDefinitionEntity, UUID> {

  /**
   * @param attributeKey the declared key to look up
   * @return the entity, if it exists
   */
  Optional<AttributeDefinitionEntity> findByAttributeKey(String attributeKey);
}
