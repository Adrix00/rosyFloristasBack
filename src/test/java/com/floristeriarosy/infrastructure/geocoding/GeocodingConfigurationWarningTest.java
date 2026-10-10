package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** {@link GeocodingConfigurationWarning} — the startup signal that delivery would be quietly wrong. */
class GeocodingConfigurationWarningTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(GeocodingConfigurationWarning.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void aBlankApiKeyWarns() {
    new GeocodingConfigurationWarning(properties("", "40.393944", "-3.6980998"));

    assertThat(warnings()).singleElement().satisfies(m -> assertThat(m).contains("GOOGLE_MAPS_API_KEY"));
  }

  @Test
  void aConfiguredKeyStaysQuietAndNeverLogsTheKey() {
    new GeocodingConfigurationWarning(properties("secret-key-value", "40.393944", "-3.6980998"));

    assertThat(appender.list).isEmpty();
  }

  private List<String> warnings() {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private GeocodingProperties properties(String apiKey, String latitude, String longitude) {
    return new GeocodingProperties(
        new GeocodingProperties.GoogleMaps(apiKey, "https://example.invalid/geocode"),
        new GeocodingProperties.Store(new BigDecimal(latitude), new BigDecimal(longitude)));
  }
}
