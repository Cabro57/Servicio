package tr.cabro.servicio.application.util;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import raven.modal.Toast;

public class SoundPlayer {

    private static SoundPlayer instance;
    private final Map<Toast.Type, Clip> clipCache = new EnumMap<>(Toast.Type.class);
    @Setter
    @Getter
    private boolean enabled = true;

    // Kaynak yollarını buraya ayarlayın (resources klasörünüzde olmalı)
    private final Map<Toast.Type, String> SOUND_PATHS;

    public SoundPlayer() {
        this.SOUND_PATHS = new EnumMap<>(Toast.Type.class);
        initSoundPaths();
    }

    private void initSoundPaths() {
        SOUND_PATHS.put(Toast.Type.SUCCESS, "/sounds/success.wav");
        SOUND_PATHS.put(Toast.Type.INFO,    "/sounds/info.wav");
        SOUND_PATHS.put(Toast.Type.WARNING, "/sounds/warning.wav");
        SOUND_PATHS.put(Toast.Type.ERROR,   "/sounds/error.wav");
        SOUND_PATHS.put(Toast.Type.DEFAULT, "/sounds/default.wav");
    }

    public static SoundPlayer getInstance() {
        if (instance == null) instance = new SoundPlayer();
        return instance;
    }

    public void play(Toast.Type type) {
        if (!enabled || type == null) return;
        try {
            Clip clip = getClip(type);
            if (clip != null) {
                clip.setFramePosition(0);
                clip.start();
            }
        } catch (Exception e) {
            // Ses çalınamazsa toast'u engelleme, sadece logla
            System.err.println("Ses çalınamadı: " + e.getMessage());
        }
    }

    private Clip getClip(raven.modal.Toast.Type type) {
        if (clipCache.containsKey(type)) return clipCache.get(type);

        String path = SOUND_PATHS.get(type);
        if (path == null) return null;

        URL url = getClass().getResource(path);
        if (url == null) {
            System.err.println("Dosya bulunamadı: " + path);
            return null;
        }

        try {
            AudioInputStream rawStream = AudioSystem.getAudioInputStream(url);

            // Formatı zorla PCM 16-bit'e çevir
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    rawStream.getFormat().getSampleRate(),
                    16,
                    rawStream.getFormat().getChannels(),
                    rawStream.getFormat().getChannels() * 2,
                    rawStream.getFormat().getSampleRate(),
                    false
            );

            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, rawStream);

            Clip clip = AudioSystem.getClip();
            clip.open(convertedStream);
            clipCache.put(type, clip);
            return clip;

        } catch (Exception e) {
            System.err.println("Ses yüklenemedi [" + path + "]: " + e.getMessage());
            return null;
        }
    }

    public void dispose() {
        clipCache.values().forEach(Clip::close);
        clipCache.clear();
    }
}
