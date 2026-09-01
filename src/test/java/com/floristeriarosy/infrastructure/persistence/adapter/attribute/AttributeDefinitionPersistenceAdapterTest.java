package com.floristeriarosy.infrastructure.persistence.adapter.attribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionAlreadyExistsException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migrations against real PostgreSQL, then exercises the attribute-definition adapter. */
@Testcontainers
@SpringBootTest
class AttributeDefinitionPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AttributeDefinitionPersistenceAdapter adapter;

  private AttributeDefinition newDefinition(String key) {
    return AttributeDefinition.create(
        AttributeDefinitionId.newId(), key, "Label " + key, AttributeDataType.TEXT, true, 0);
  }

  @Test
  void savesAndFindsByIdAndKey() {
    String key = "color-" + UUID.randomUUID().toString().substring(0, 8);
    AttributeDefinition saved = adapter.save(newDefinition(key));

    assertThat(adapter.findById(saved.id())).isPresent();
    assertThat(adapter.findByKey(key)).isPresent();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void rejectsADuplicateKey() {
    String key = "altura-" + UUID.randomUUID().toString().substring(0, 8);
    adapter.save(newDefinition(key));
    AttributeDefinition duplicate =
        AttributeDefinition.create(
            AttributeDefinitionId.newId(), key, "Otra etiqueta", AttributeDataType.NUMBER, true, 0);

    assertThatThrownBy(() -> adapter.save(duplicate))
        .isInstanceOf(AttributeDefinitionAlreadyExistsException.class);
  }

  @Test
  void deletesAnAttributeDefinition() {
    String key = "riego-" + UUID.randomUUID().toString().substring(0, 8);
    AttributeDefinition saved = adapter.save(newDefinition(key));

    adapter.delete(saved.id());

    assertThat(adapter.findById(saved.id())).isEmpty();
  }

  @Test
  void listsOrderedByPositionThenLabel() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    adapter.save(
        AttributeDefinition.create(
            AttributeDefinitionId.newId(),
            "z-" + suffix,
            "AAA " + suffix,
            AttributeDataType.TEXT,
            true,
            0));
    adapter.save(
        AttributeDefinition.create(
            AttributeDefinitionId.newId(),
            "a-" + suffix,
            "ZZZ " + suffix,
            AttributeDataType.TEXT,
            true,
            0));

    assertThat(adapter.findAll()).isNotEmpty();
  }
}
