package tr.cabro.servicio.application.themes;

import raven.modal.Toast;
import raven.modal.toast.ToastPanel;
import raven.modal.toast.option.ToastBorderStyle;
import raven.modal.toast.option.ToastOption;
import raven.modal.toast.option.ToastStyle;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Bildirimleri (raven {@link Toast}) uygulamanın renk diline bağlar.
 * <p>
 * Kütüphane kendi paletini getirir (#1EA97C, #3B82F6, #CC8925, #FF5757, iki temada da aynı) ve
 * zemini bu renklerle boyar: dört bildirim türü dört ayrı renkli kart olarak, uygulamanın
 * hiçbir yerinde geçmeyen tonlarda çıkıyordu. Burada:
 * <ul>
 *   <li>İkon rengi anlamsal token'dan gelir ({@link SemanticColor}): bildirimdeki yeşil, tutarlardaki
 *       ve rozetlerdeki yeşille aynıdır.</li>
 *   <li>Zemin tüm türlerde aynı nötr karttır ({@code BackgroundType.NONE}: kütüphanenin panel tonu;
 *       {@code DEFAULT} zemini türün rengiyle boyuyordu). Renk yalnızca ikonda kalır; ince çizgi
 *       ({@code Component.borderColor}) ve yumuşak gölge kartı sayfadan ayırır.</li>
 * </ul>
 * Kütüphane renkleri hex olarak sakladığı için tema her değiştiğinde yeniden uygulanır.
 */
public final class ToastTheme {

    private static boolean listening;

    private ToastTheme() {
    }

    /** Uygular ve tema değişikliklerini dinlemeye başlar; EDT'de, tema kurulduktan sonra çağrılır. */
    public static void install() {
        apply();
        if (listening) return;
        listening = true;
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) SwingUtilities.invokeLater(ToastTheme::apply);
        });
    }

    private static void apply() {
        String surface = SemanticColor.hex(UIManager.getColor("Table.background"));
        Map<Toast.Type, ToastPanel.ThemesData> themes = Toast.getThemesData();
        put(themes, Toast.Type.SUCCESS, SemanticColor.success(), surface);
        put(themes, Toast.Type.INFO, SemanticColor.info(), surface);
        put(themes, Toast.Type.WARNING, SemanticColor.warning(), surface);
        put(themes, Toast.Type.ERROR, SemanticColor.danger(), surface);
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) put(themes, Toast.Type.DEFAULT, muted, surface);

        ToastOption option = Toast.getDefaultOption();
        ToastStyle style = option.getStyle();
        style.setBackgroundType(ToastStyle.BackgroundType.NONE)
                .setShowLabel(false)
                .setPaintTextColor(false)
                .setShowCloseButton(true);
        ToastBorderStyle border = style.getBorderStyle();
        border.setBorderType(ToastBorderStyle.BorderType.OUTLINE)
                .setBorderWidth(1)
                .setLineColor(UIManager.getColor("Component.borderColor"))
                .setRound(12)
                .setShadowSize(new Insets(1, 3, 6, 3))
                .setShadowOpacity(isDark() ? 0.35f : 0.10f);
    }

    /** Türün ikonunu korur, rengini ve zeminini değiştirir (iki tema yuvası da o anki tema için). */
    private static void put(Map<Toast.Type, ToastPanel.ThemesData> themes, Toast.Type type, Color tone, String surface) {
        ToastPanel.ThemesData old = themes.get(type);
        if (old == null) return;
        String hex = SemanticColor.hex(tone);
        themes.put(type, new ToastPanel.ThemesData(old.getIcon(), new String[]{hex, hex}, new String[]{surface, surface}));
    }

    private static boolean isDark() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }
}
