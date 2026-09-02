package com.floristeriarosy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} activates {@code infrastructure.scheduler.InventoryAlertScheduler} (ADR-013). */
@SpringBootApplication
@EnableScheduling
public class AppApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppApplication.class, args);
  }
}
