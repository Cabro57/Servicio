package tr.cabro.servicio.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;

/**
 * Ayarlardaki aktif arayüz diline göre çevrilmiş mesaj metinlerine erişim noktası.
 * <p>
 * Kapsam: onay diyalogları, hata/bilgi mesajları, güncelleme bildirimleri, toast'lar.
 * Form etiketleri ve PDF belgeleri bu kapsamın dışındadır, hep Türkçe kalır.
 */
public final class Messages {

    private Messages() {}

    /**
     * {@code key} için aktif dildeki metni döner. {@code args} verilmişse
     * {@link MessageFormat} ile yer tutucular ({@code {0}} vb.) doldurulur.
     * Argüman yoksa metin işlenmeden döner — Türkçe metinlerdeki kesme
     * işaretlerinin ({@code TCMB'den} gibi) {@code MessageFormat} tarafından
     * bozulmaması için.
     */
    public static String get(String key, Object... args) {
        String raw = lookup(key);
        if (args == null || args.length == 0) {
            return raw;
        }
        return MessageFormat.format(raw, args);
    }

    private static String lookup(String key) {
        try {
            return AppLocale.messageBundle().getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }
}
