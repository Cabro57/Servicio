package tr.cabro.servicio.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Servis katmanının veritabanı işlerini çalıştırdığı havuz.
 * <p>
 * Eskiden işler {@code ForkJoinPool.commonPool()}'da çalışıyordu: tek SQLite bağlantısını bekleyen
 * bloklayan JDBC çağrıları ortak havuzun tüm thread'lerini tutup aynı havuzu kullanan diğer işleri
 * aç bırakıyordu. Bağlantı zaten tek olduğu için birkaç thread yeterli.
 * <p>
 * Kural: bu havuzda çalışan kod başka bir DB future'ını {@code join()} ile beklememeli — zincirlemeli
 * ({@code thenCompose}) ya da repository'yi doğrudan çağırmalı. Aksi halde havuz dolduğunda kilitlenir.
 */
public final class DbExecutor {

    private static final int THREADS = 4;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final ExecutorService POOL;

    static {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "servicio-db-" + COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        // Boşta kalan thread'ler bir dakika sonra kapanır; uygulama açık dururken kaynak tutmaz.
        pool.allowCoreThreadTimeOut(true);
        POOL = pool;
    }

    private DbExecutor() {
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, POOL);
    }

    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, POOL);
    }
}
