package com.sts.init.runner;

import com.sts.init.config.InitProperties;
import com.sts.init.service.DatabaseSchemaInitializer;
import com.sts.init.service.FlywayBaselineInitializer;
import com.sts.init.service.SeedDataFillerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

  private final InitProperties initProperties;
  private final DatabaseSchemaInitializer databaseSchemaInitializer;
  private final FlywayBaselineInitializer flywayBaselineInitializer;
  private final SeedDataFillerService seedDataFillerService;

  @Override
  public void run(String... args) {
    if (!initProperties.isEnabled()) {
      log.info("System initial seed filler is disabled, exiting.");
      return;
    }

    try {
      log.info("Initializing database from modular full-schema SQL...");
      databaseSchemaInitializer.initializeEmptyDatabase();
      log.info("Registering Flyway baseline for the initialized schema...");
      flywayBaselineInitializer.baseline();
      log.info("Filling platform seed data...");
      seedDataFillerService.fillSeedData();
      log.info("Database initialization executed successfully.");
    } catch (Exception e) {
      log.error("Failed to execute database initialization: {}", e.getMessage(), e);
      throw e;
    }
  }
}
