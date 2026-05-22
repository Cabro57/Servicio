package tr.cabro.servicio.updater;

import tr.cabro.servicio.Servicio;

import javax.swing.*;
import java.util.prefs.Preferences;

/**
 * Güncelleme dialog açma yardımcısı.
 * <p>
 * Toast bildirimleri ve badge yönetimi MainForm içindedir.
 * Bu sınıf sadece UpdateDialog açmayı merkezileştirir;
 * hem helper popup'tan hem de periyodik kontrolden çağrılır.
 */
public class UpdateNotifier {

    private static final String PREF_NODE            = "tr/cabro/servicio";
    private static final String PREF_SKIPPED_VERSION = "update.skipped_version";

    private UpdateNotifier() {}

    /**
     * UpdateDialog'u açar.
     *
     * @param frame     Dialog'un owner JFrame'i.
     * @param manifest  Güncelleme manifesti.
     * @param isHotfix  true → aynı sürüm yamasi; false → yeni sürüm.
     */
    public static void openUpdateDialog(JFrame frame,
                                        UpdateManifest manifest,
                                        boolean isHotfix) {
        Preferences   prefs   = Preferences.userRoot().node(PREF_NODE);
        UpdateChecker checker = Servicio.getInstance().getUpdateChecker();

        UpdateDialog dialog = new UpdateDialog(
                frame, manifest, checker.getManager(), isHotfix);

        dialog.setOnSkipVersion(v -> prefs.put(PREF_SKIPPED_VERSION, v));
        dialog.setVisible(true);
        // Modal — buraya dialog kapandıktan sonra gelir
    }
}
