package com.floristeriarosy.infrastructure.persistence.adapter.attribute;

import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionAlreadyExistsException;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import com.floristeriarosy.infrastructure.persistence.entity.attribute.AttributeDefinitionEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.attribute.repository.AttributeDefinitionJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.attribute.repository.AttributeDefinitionJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.attribute.AttributeDefinitionPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link AttributeDefinitionPort} (ADR-003): JPA for writes and simple lookups, JDBC
 * for the ordered listing (ADR-002).
 */
@Repository
public class AttributeDefinitionPersistenceAdapter implements AttributeDefinitionPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDefinitionPersistenceAdapter.class);

  private final AttributeDefinitionJpaRepository jpaRepository;
  private final AttributeDefinitionJdbcRepository jdbcRepository;
  private final AttributeDefinitionPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups by id/key
   * @param jdbcRepository the ordered listing
   * @param mapper converts between the domain {@link AttributeDefinition} and the JPA
   *     {@link AttributeDefinitionEntity}
   */
  public AttributeDefinitionPersistenceAdapter(
      AttributeDefinitionJpaRepository jpaRepository,
      AttributeDefinitionJdbcRepository jdbcRepository,
      AttributeDefinitionPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * @return every attribute definition, ordered by position then label
   */
  @Override
  public List<AttributeDefinition> findAll() {
    LOGGER.debug("findAll");
    List<AttributeDefinition> result = jdbcRepository.findAll();
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }

  /**
   * @param id the attribute definition to load
   * @return the attribute definition, if it exists
   */
  @Override
  public Optional<AttributeDefinition> findById(AttributeDefinitionId id) {
    LOGGER.debug("findById id={}", id);
    Optional<AttributeDefinition> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param attributeKey the declared key to look up
   * @return the attribute definition, if it exists
   */
  @Override
  public Optional<AttributeDefinition> findByKey(String attributeKey) {
    LOGGER.debug("findByKey attributeKey={}", attributeKey);
    Optional<AttributeDefinition> result =
        jpaRepository.findByAttributeKey(attributeKey).map(mapper::toDomain);
    LOGGER.debug("findByKey attributeKey={} -> found={}", attributeKey, result.isPresent());
    return result;
  }

  /**
   * @param definition the attribute definition to insert or update
   * @return the saved attribute definition, with timestamps populated by the database
   * @throws AttributeDefinitionAlreadyExistsException the key unique constraint was violated
   */
  @Override
  public AttributeDefinition save(AttributeDefinition definition) {
    LOGGER.debug("save id={} attributeKey={}", definition.id(), definition.attributeKey());
    AttributeDefinitionEntity entity = mapper.toEntity(definition);
    try {
      AttributeDefinition result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation, definition);
    }
  }

  /**
   * @param id the attribute definition to delete
   */
  @Override
  public void delete(AttributeDefinitionId id) {
    LOGGER.debug("delete id={}", id);
    jpaRepository.deleteById(id.value());
    LOGGER.debug("delete id={} -> deleted", id);
  }

  /**
   * Translates a database constraint violation into the business exception it represents. The
   * constraint's name never reaches the client (06-validation-conventions.md).
   *
   * @param violation the low-level constraint violation caught around the save
   * @param definition the attribute definition that was being saved
   * @return the business exception to throw instead, or {@code violation} itself if the
   *     constraint is not one this module owns
   */
  private RuntimeException translate(
      DataIntegrityViolationException violation, AttributeDefinition definition) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save id={} -> constraint violation: {}", definition.id(), message);
    if (message.contains("uq_product_attribute_definitions_key")) {
      return new AttributeDefinitionAlreadyExistsException(
          "An attribute definition with key '" + definition.attributeKey() + "' already exists");
    }
    return violation;
  }
}
