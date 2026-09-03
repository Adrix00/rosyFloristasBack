package com.floristeriarosy;

import com.floristeriarosy.infrastructure.security.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} activates {@code infrastructure.scheduler.InventoryAlertScheduler}
 * (ADR-013). {@code @EnableConfigurationProperties} binds {@code app.rate-limit.*} (ADR-016).
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class AppApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppApplication.class, args);
  }
}
