package tr.cabro.servicio.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.InvocationEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EDT'yi uzun süre meşgul eden olayları yakalar ve hangi kodun beklettiğini loga yazar.
 * <p>
 * Kendi {@link EventQueue}'sunu sisteme takar; her olayın başlangıç zamanını tutar. Arka plandaki
 * bekçi iş parçacığı, süren olay {@link #SAMPLE_AFTER_MS} değerini aşınca EDT'nin stack trace'ini
 * örnekler (olay bitene kadar {@link #SAMPLE_INTERVAL_MS} aralıkla, en çok {@link #MAX_SAMPLES} kez).
 * Olay {@link #LOG_THRESHOLD_MS} değerinden uzun sürdüyse süresi ve örnekleri tek bir WARN satırı
 * olarak yazılır. Ardışık aynı örnekler birleştirilir; böylece hangi metodun zamanı yediği okunur.
 * <p>
 * Maliyeti olay başına iki {@code System.nanoTime()} çağrısı; bekçi yalnızca takılma varken örnek alır.
 */
public final class EdtStallMonitor {

    private static final Logger log = LoggerFactory.getLogger(EdtStallMonitor.class);

    /** Bu süreyi aşan olaylar loga yazılır (insan gözünün takılma olarak fark ettiği eşik). */
    private static final long LOG_THRESHOLD_MS = 80;
    /** Süren olay bu kadar uzadığında ilk stack örneği alınır. */
    private static final long SAMPLE_AFTER_MS = 40;
    private static final long SAMPLE_INTERVAL_MS = 20;
    private static final int MAX_SAMPLES = 25;
    /** Her örnekten yazılacak en fazla çerçeve sayısı (üstten). */
    private static final int MAX_FRAMES = 45;

    private static volatile boolean installed;
    private static volatile MonitoredQueue queue;

    private EdtStallMonitor() {}

    public static boolean isInstalled() {
        return installed;
    }

    /**
     * EDT'de o an işlenen olaya ek bilgi iliştirir; olay takılma sayılırsa rapora eklenir. Metin yalnızca
     * rapor yazılırken üretilir, takılma yoksa maliyeti bir alan atamasıdır. EDT dışından çağrılırsa yok sayılır.
     */
    public static void annotate(java.util.function.Supplier<String> note) {
        MonitoredQueue q = queue;
        if (q == null || !EventQueue.isDispatchThread()) return;
        Dispatch d = q.current;
        if (d != null) d.note = note;
    }

    /** Bir kez çağrılır; ikinci çağrı etkisizdir. Herhangi bir iş parçacığından çağrılabilir. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        MonitoredQueue q = new MonitoredQueue();
        queue = q;
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(q);
        Thread watchdog = new Thread(q::watch, "servicio-edt-watchdog");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.start();
        log.info("EDT takılma izleyicisi başlatıldı (eşik {} ms)", LOG_THRESHOLD_MS);
    }

    /** O an EDT'de işlenen olayın durumu; iç içe olay döngülerinde bir öncekine geri dönülür. */
    private static final class Dispatch {
        final long startNanos;
        final Thread thread;
        final List<StackTraceElement[]> samples = new ArrayList<>();
        long nextSampleNanos;
        volatile java.util.function.Supplier<String> note;

        Dispatch(long startNanos, Thread thread) {
            this.startNanos = startNanos;
            this.thread = thread;
            this.nextSampleNanos = startNanos + SAMPLE_AFTER_MS * 1_000_000L;
        }
    }

    private static final class MonitoredQueue extends EventQueue {

        private volatile Dispatch current;

        @Override
        protected void dispatchEvent(AWTEvent event) {
            Dispatch previous = current;
            Dispatch dispatch = new Dispatch(System.nanoTime(), Thread.currentThread());
            current = dispatch;
            try {
                super.dispatchEvent(event);
            } finally {
                current = previous;
                long elapsedMs = (System.nanoTime() - dispatch.startNanos) / 1_000_000L;
                if (elapsedMs >= LOG_THRESHOLD_MS) {
                    report(event, elapsedMs, dispatch);
                }
            }
        }

        void watch() {
            while (true) {
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                Dispatch dispatch = current;
                if (dispatch == null) continue;
                long now = System.nanoTime();
                synchronized (dispatch.samples) {
                    if (now < dispatch.nextSampleNanos || dispatch.samples.size() >= MAX_SAMPLES) continue;
                    dispatch.samples.add(dispatch.thread.getStackTrace());
                    dispatch.nextSampleNanos = now + SAMPLE_INTERVAL_MS * 1_000_000L;
                }
            }
        }

        private static void report(AWTEvent event, long elapsedMs, Dispatch dispatch) {
            StringBuilder sb = new StringBuilder();
            sb.append("EDT ").append(elapsedMs).append(" ms takıldı: ").append(describe(event));
            java.util.function.Supplier<String> note = dispatch.note;
            if (note != null) {
                try {
                    sb.append("\n  ").append(note.get());
                } catch (RuntimeException e) {
                    sb.append("\n  (ek bilgi üretilemedi: ").append(e).append(')');
                }
            }
            synchronized (dispatch.samples) {
                if (dispatch.samples.isEmpty()) {
                    sb.append("\n  (örnek alınamadı)");
                }
                StackTraceElement[] last = null;
                int repeat = 0;
                for (StackTraceElement[] sample : dispatch.samples) {
                    if (last != null && Arrays.equals(last, sample)) {
                        repeat++;
                        continue;
                    }
                    appendSample(sb, last, repeat);
                    last = sample;
                    repeat = 1;
                }
                appendSample(sb, last, repeat);
            }
            log.warn(sb.toString());
        }

        private static void appendSample(StringBuilder sb, StackTraceElement[] sample, int repeat) {
            if (sample == null) return;
            sb.append("\n  --- örnek (x").append(repeat).append(", ~")
                    .append(repeat * SAMPLE_INTERVAL_MS).append(" ms) ---");
            int count = Math.min(sample.length, MAX_FRAMES);
            for (int i = 0; i < count; i++) {
                sb.append("\n    at ").append(sample[i]);
            }
            if (sample.length > count) {
                sb.append("\n    ... ").append(sample.length - count).append(" çerçeve daha");
            }
        }

        /** Olayın kısa tanımı; bileşenin toString'i çok uzun olduğu için yalnızca sınıf adları yazılır. */
        private static String describe(AWTEvent event) {
            String source = event.getSource() == null ? "-" : event.getSource().getClass().getName();
            if (event instanceof InvocationEvent) {
                return "InvocationEvent " + event.paramString();
            }
            return event.getClass().getSimpleName() + " id=" + event.getID() + " kaynak=" + source;
        }
    }
}
