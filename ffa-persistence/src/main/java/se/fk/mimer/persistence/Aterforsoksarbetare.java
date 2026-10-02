package se.fk.mimer.persistence;

import java.time.Duration;
import java.util.concurrent.*;

/** Periodiska återförsök så länge värdprocessen lever. Stäng före Kafka-publiceraren. */
public final class Aterforsoksarbetare implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(Aterforsoksarbetare.class.getName());
    private final ScheduledExecutorService executor;

    public Aterforsoksarbetare(PostgresKafkaLager lager, Duration intervall, int batchstorlek) {
        if (intervall == null || intervall.toMillis() <= 0 || batchstorlek <= 0)
            throw new IllegalArgumentException("Intervall och batchstorlek måste vara positiva");
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "ffa-aterforsok");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { lager.skickaVantande(batchstorlek); }
            catch (RuntimeException e) { LOG.log(System.Logger.Level.WARNING, "Återförsök kvarstår", e); }
        }, intervall.toMillis(), intervall.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                    throw new IllegalStateException("Återförsöksarbetaren kunde inte stoppas");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stopp av återförsök avbröts", e);
        }
    }
}
