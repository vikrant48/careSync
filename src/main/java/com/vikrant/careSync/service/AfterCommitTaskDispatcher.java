package com.vikrant.careSync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class AfterCommitTaskDispatcher {

    private final TaskExecutor taskExecutor;

    public AfterCommitTaskDispatcher(@Qualifier("appointmentNotificationExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void submitAfterCommit(String taskName, Runnable task) {
        Runnable protectedTask = () -> {
            try {
                task.run();
            } catch (Exception exception) {
                log.error("After-commit task failed: {}", taskName, exception);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(protectedTask);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(protectedTask);
            }
        });
    }
}
