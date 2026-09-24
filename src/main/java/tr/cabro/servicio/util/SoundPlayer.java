package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.AppSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Bildirim sesleri: telefon bildirimlerini andıran kısa, yumuşak çıngıraklar (zil/marimba tınısı).
 * Ses dosyası gömülmez; her ses, birkaç notanın üst harmonikli ve üstel sönümlü sinüs toplamı olarak
 * anlık üretilip çalınır — ek kaynak gerektirmez, Windows ve Linux'ta {@code javax.sound.sampled} ile
 * çalışır. Ses cihazı yoksa/erişilemezse sessizce yutulur; iş akışını bloklayacak hata fırlatılmaz.
 * <p>
 * Ayarlar &gt; Ses'teki anahtar ve seviye uygulanır. Art arda tetiklenen sesler (ör. bir uyarı bildirimi
 * ile POS'un kendi hata sesi) üst üste binmesin diye çok yakın ikinci ses atlanır.
 */
public final class SoundPlayer {

    private static final float SAMPLE_RATE = 44100f;
    private static final long MIN_GAP_MS = 220;
    private static volatile long lastPlayed;

    /** Sesin adı: ne olduğu anlamına göre; çalınışı {@link #NOTES} tablosunda. */
    public enum Cue {
        SUCCESS("Başarı"), PAYMENT("Tahsilat"), ADDED("Kalem eklendi"), REMOVED("Kalem silindi"),
        STATUS("Durum değişti"), WARNING("Uyarı"), ERROR("Hata");

        private final String label;

        Cue(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** {frekans Hz, başlangıç ms, sönüm sabiti ms, üst ton (3. harmonik) payı}. */
    private static double[][] notes(Cue cue) {
        return switch (cue) {
            // Yükselen ikili çıngırak
            case SUCCESS -> new double[][]{{1319, 0, 110, 0.10}, {1760, 95, 150, 0.10}};
            // Madeni para gibi yükselen dörtlü
            case PAYMENT -> new double[][]{{1047, 0, 90, 0.12}, {1319, 70, 90, 0.12}, {1568, 140, 100, 0.12}, {2093, 215, 190, 0.12}};
            // Kısa tek "pop"
            case ADDED -> new double[][]{{1175, 0, 85, 0.08}};
            // Yumuşak inen ikili
            case REMOVED -> new double[][]{{988, 0, 90, 0.05}, {659, 85, 130, 0.05}};
            // Sakin iki tonlu zil
            case STATUS -> new double[][]{{784, 0, 100, 0.10}, {1175, 100, 170, 0.10}};
            // Çift vuruş
            case WARNING -> new double[][]{{880, 0, 80, 0.18}, {880, 150, 100, 0.18}};
            // Alçalan, biraz mat iki ton
            case ERROR -> new double[][]{{392, 0, 120, 0.35}, {294, 130, 190, 0.35}};
        };
    }

    private SoundPlayer() {}

    public static void success() { play(Cue.SUCCESS); }
    public static void error() { play(Cue.ERROR); }
    public static void warning() { play(Cue.WARNING); }
    public static void payment() { play(Cue.PAYMENT); }
    public static void added() { play(Cue.ADDED); }
    public static void removed() { play(Cue.REMOVED); }
    public static void statusChanged() { play(Cue.STATUS); }

    /** Ayarlar'daki "Dene" düğmeleri: anahtar kapalıyken de çalar (kullanıcı sesi duymak istiyor). */
    public static void preview(Cue cue) {
        playAsync(cue, true);
    }

    public static void play(Cue cue) {
        playAsync(cue, false);
    }

    private static void playAsync(Cue cue, boolean force) {
        int volume;
        try {
            var sound = AppSettings.get().getSound();
            if (!force && !sound.isEnabled()) return;
            volume = sound.getVolume();
        } catch (Exception e) {
            volume = 70; // ayarlar henüz yüklenmediyse
        }
        if (volume <= 0) return;

        long now = System.currentTimeMillis();
        if (!force && now - lastPlayed < MIN_GAP_MS) return;
        lastPlayed = now;

        final int vol = volume;
        Thread thread = new Thread(() -> render(cue, vol), "sound-player");
        thread.setDaemon(true);
        thread.start();
    }

    private static void render(Cue cue, int volume) {
        double[][] notes = notes(cue);
        double end = 0;
        for (double[] n : notes) end = Math.max(end, n[1] + n[2] * 5);
        int total = (int) (SAMPLE_RATE * end / 1000.0);
        double[] mix = new double[total];

        for (double[] n : notes) {
            double freq = n[0];
            int start = (int) (SAMPLE_RATE * n[1] / 1000.0);
            double tau = SAMPLE_RATE * n[2] / 1000.0;
            double third = n[3];
            for (int i = 0; start + i < total; i++) {
                double t = i / SAMPLE_RATE;
                double env = Math.exp(-i / tau);
                if (env < 0.002) break;
                // Kısa atak: tık sesi olmasın
                double attack = Math.min(1.0, i / (SAMPLE_RATE * 0.004));
                double w = 2.0 * Math.PI * freq * t;
                double v = Math.sin(w) + 0.28 * Math.sin(2 * w) + third * Math.sin(3 * w);
                mix[start + i] += v * env * attack;
            }
        }

        // Seviye: 0-100 doğrusal değil, kulağa daha doğal olsun diye kare eğrisi.
        double gain = 0.55 * Math.pow(volume / 100.0, 2);
        byte[] pcm = new byte[total * 2];
        for (int i = 0; i < total; i++) {
            double x = Math.max(-1.0, Math.min(1.0, mix[i] * gain));
            short sample = (short) (x * 32767);
            pcm[2 * i] = (byte) (sample & 0xFF);
            pcm[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, pcm.length);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (Exception e) {
            Servicio.getLogger().debug("Bildirim sesi çalınamadı: {}", e.getMessage());
        }
    }
}
