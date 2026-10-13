package com.sts.training.service;

import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrainingParticipantContentFactListenerTest {

  @Test
  @DisplayName("监听器将事件委托为单行投影刷新")
  void onFactChanged_delegatesToSynchronizeParticipantContent() {
    TrainingResultSyncService resultSyncService = mock(TrainingResultSyncService.class);
    TrainingParticipantContentFactListener listener =
      new TrainingParticipantContentFactListener(resultSyncService);
    TrainingParticipantContentFactChangedEvent event =
      new TrainingParticipantContentFactChangedEvent(this, 2L, 11L);

    listener.onFactChanged(event);

    verify(resultSyncService).synchronizeParticipantContent(2L, 11L);
  }
}
