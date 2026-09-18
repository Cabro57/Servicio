package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * POS akışında (barkod okutma, satışı tamamlama) başarı/hata bildirimi için kısa uyarı sesleri.
 * Ses dosyası gömmek yerine sinüs dalgası anlık üretilip çalınır — ek kaynak dosyası gerektirmez,
 * her platformda (Windows/Linux) {@code javax.sound.sampled} ile çalışır. Ses cihazı yoksa/erişilemezse
 * sessizce yutulur; POS akışını bloklayacak bir hata asla fırlatılmaz.
 */
public final class SoundPlayer {

    private static final float SAMPLE_RATE = 44100f;

    private SoundPlayer() {}

    /** Kısa, yükselen çift ton — başarılı işlem (ürün eklendi, satış tamamlandı). */
    public static void success() {
        playAsync(() -> {
            playTone(880, 90);
            playTone(1320, 110);
        });
    }

    /** Tek, kısa alçak ton — hata (barkod bulunamadı, satış başarısız). */
    public static void error() {
        playAsync(() -> playTone(220, 220));
    }

    private static void playAsync(Runnable task) {
        Thread thread = new Thread(task, "sound-player");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playTone(int frequencyHz, int durationMs) {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, (int) SAMPLE_RATE);
            line.start();

            int sampleCount = (int) (SAMPLE_RATE * durationMs / 1000.0);
            byte[] buffer = new byte[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double angle = 2.0 * Math.PI * i * frequencyHz / SAMPLE_RATE;
                // Tık sesini azaltmak için kısa fade-in/out
                double fade = Math.min(1.0, Math.min(i, sampleCount - i) / (SAMPLE_RATE * 0.01));
                buffer[i] = (byte) (Math.sin(angle) * 90 * fade);
            }

            line.write(buffer, 0, buffer.length);
            line.drain();
        } catch (Exception e) {
            Servicio.getLogger().debug("Bildirim sesi çalınamadı: {}", e.getMessage());
        }
    }
}
