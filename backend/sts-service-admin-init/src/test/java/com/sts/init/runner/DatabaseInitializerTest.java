package com.sts.init.runner;

import com.sts.init.config.InitProperties;
import com.sts.init.service.DatabaseSchemaInitializer;
import com.sts.init.service.FlywayBaselineInitializer;
import com.sts.init.service.SeedDataFillerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DatabaseInitializerTest {

  @Mock
  private DatabaseSchemaInitializer databaseSchemaInitializer;
  @Mock
  private FlywayBaselineInitializer flywayBaselineInitializer;
  @Mock
  private SeedDataFillerService seedDataFillerService;

  @Test
  void shouldBuildFullSchemaThenRegisterBaselineThenFillSeedData() {
    InitProperties properties = new InitProperties();
    properties.setEnabled(true);
    DatabaseInitializer initializer =
      new DatabaseInitializer(
        properties, databaseSchemaInitializer, flywayBaselineInitializer, seedDataFillerService);

    initializer.run();

    var ordered = inOrder(databaseSchemaInitializer, flywayBaselineInitializer, seedDataFillerService);
    ordered.verify(databaseSchemaInitializer).initializeEmptyDatabase();
    ordered.verify(flywayBaselineInitializer).baseline();
    ordered.verify(seedDataFillerService).fillSeedData();
  }

  @Test
  void shouldDoNothingWhenInitializationIsDisabled() {
    InitProperties properties = new InitProperties();
    properties.setEnabled(false);
    DatabaseInitializer initializer =
      new DatabaseInitializer(
        properties, databaseSchemaInitializer, flywayBaselineInitializer, seedDataFillerService);

    initializer.run();

    verifyNoInteractions(databaseSchemaInitializer, flywayBaselineInitializer, seedDataFillerService);
  }
}
