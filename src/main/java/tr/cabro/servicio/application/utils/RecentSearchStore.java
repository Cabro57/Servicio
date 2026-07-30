package tr.cabro.servicio.application.utils;

import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;

import java.util.List;

/**
 * Global arama kutusundaki "son aramalar" ve "favoriler" listeleri.
 * <p>
 * Eskiden registry'de tek bir metin alanına virgülle birleştirilerek yazılıyordu; artık
 * {@code config.json} içinde gerçek liste olarak tutuluyor (bkz. {@link AppConfig.Ui}).
 * Bir kayıt favorilere alındığında normal listeden çıkarılır — iki listede birden görünmez.
 */
public final class RecentSearchStore {

    private RecentSearchStore() {}

    private static AppConfig.Ui ui() {
        return AppSettings.get().getUi();
    }

    private static List<String> list(boolean favorite) {
        return favorite ? ui().getRecentSearchFavorite() : ui().getRecentSearch();
    }

    /** İlgili listenin kopyası; boşsa boş dizi döner. */
    public static String[] get(boolean favorite) {
        return list(favorite).toArray(new String[0]);
    }

    /** Kaydı listenin başına ekler (zaten varsa öne taşır). */
    public static void add(String value, boolean favorite) {
        if (value == null || value.isBlank()) return;

        if (favorite) {
            ui().getRecentSearch().remove(value);
        } else if (ui().getRecentSearchFavorite().contains(value)) {
            // Favorilerde olan bir kayıt ayrıca "son aramalar"a düşmez
            return;
        }

        List<String> target = list(favorite);
        target.remove(value);
        target.add(0, value);
        AppSettings.save();
    }

    public static void remove(String value, boolean favorite) {
        if (list(favorite).remove(value)) {
            AppSettings.save();
        }
    }
}
