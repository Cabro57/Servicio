package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.SoundPlayer;

import javax.swing.*;

/**
 * Ayarlar &gt; Uygulama &gt; Ses: bildirim seslerinin anahtarı, seviyesi ve tek tek denenmesi.
 * Ayar makineye bağlıdır ({@code config.json}).
 */
public class SettingsSoundPanel extends JPanel {

    private JSlider volume;
    private JLabel volumeValue;

    public SettingsSoundPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Anahtar ve seviye ---
        JCheckBox enabled = new JCheckBox("Bildirim seslerini çal", AppSettings.get().getSound().isEnabled());
        enabled.addActionListener(e -> {
            AppSettings.get().getSound().setEnabled(enabled.isSelected());
            AppSettings.save();
            setControlsEnabled(enabled.isSelected());
            SettingsKit.saved(this);
            if (enabled.isSelected()) SoundPlayer.preview(SoundPlayer.Cue.SUCCESS);
        });

        volume = new JSlider(0, 100, AppSettings.get().getSound().getVolume());
        volume.setMajorTickSpacing(25);
        volume.getAccessibleContext().setAccessibleName("Ses seviyesi");
        volumeValue = SettingsKit.note(volume.getValue() + "%");
        volume.addChangeListener(e -> {
            volumeValue.setText(volume.getValue() + "%");
            // Sürüklerken kaydetme ve çalma yok; bırakınca bir kez.
            if (volume.getValueIsAdjusting()) return;
            AppSettings.get().getSound().setVolume(volume.getValue());
            AppSettings.save();
            SettingsKit.saved(this);
            SoundPlayer.preview(SoundPlayer.Cue.SUCCESS);
        });

        JPanel rows = SettingsKit.rows();
        rows.add(SettingsKit.check(enabled, "Tahsilat, kalem ekleme/silme, durum değişikliği ve tüm uyarı ile hata bildirimlerinde kısa bir ses çalar. POS'taki barkod ve satış sesleri de buna bağlıdır."), "span 2, growx, wrap");
        rows.add(SettingsKit.label("Ses seviyesi"));
        JPanel level = new JPanel(new MigLayout("insets 0, gap 10", "[220!][40!]", "[center]"));
        level.setOpaque(false);
        level.add(volume, "growx");
        level.add(volumeValue);
        rows.add(level);
        SettingsKit.section(page, "Bildirim sesleri", "Telefon bildirimlerini andıran kısa, yumuşak sesler.", rows);

        // --- Deneme ---
        JPanel test = new JPanel(new MigLayout("insets 0, gap 8, wrap 4", "[][][][]", "[]"));
        test.setOpaque(false);
        for (SoundPlayer.Cue cue : SoundPlayer.Cue.values()) {
            JButton b = new JButton(cue.label(), new Ikon("icons/volume-2.svg", 14));
            b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
            b.setToolTipText(cue.label() + " sesini dinle");
            b.addActionListener(e -> SoundPlayer.preview(cue));
            test.add(b, "sgx cue, growx");
        }
        SettingsKit.section(page, "Sesleri dene", "Her sesin ne zaman çaldığı adında yazıyor; buradan dinleyebilirsiniz.", test);

        setControlsEnabled(enabled.isSelected());
        return page;
    }

    private void setControlsEnabled(boolean on) {
        volume.setEnabled(on);
        volumeValue.setEnabled(on);
    }
}
