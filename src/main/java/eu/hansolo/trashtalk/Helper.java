package eu.hansolo.trashtalk;

import eu.hansolo.toolbox.properties.ObjectProperty;
import eu.hansolo.toolbox.statemachine.State;
import eu.hansolo.toolbox.statemachine.StateChangeException;
import eu.hansolo.toolbox.statemachine.StateMachine;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;


public class Helper {
    public static final PauseTransition pause(final long ms) {
        PauseTransition pause = new PauseTransition(Duration.millis(ms));
        return pause;
    }

    public static void runAndWait(final Runnable action) {
        if (action == null) { throw new NullPointerException("action"); }

        // Run synchronously on JavaFX thread
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        // Queue on JavaFX thread and wait for completion
        final CountDownLatch doneLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                doneLatch.countDown();
            }
        });

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            // ignore exception
        }
    }

    public static boolean runAndWait(final Callable<Boolean> action) {
        try {
            if (Platform.isFxApplicationThread()) {
                return action.call();
            } else {
                final FutureTask<Boolean> futureTask = new FutureTask<>(action);
                Platform.runLater(futureTask);
                return futureTask.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
