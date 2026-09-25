package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Ayarlar'dan açılan küçük pencerelerin ortak kabuğu (işçilik, kategori, ad değiştir, taşı ve sil…).
 * <p>
 * Anatomi her pencerede aynı: modal başlığı (raven), altında isteğe bağlı soluk bir giriş cümlesi,
 * gövde (alan ızgarası ya da bilgi satırları), en altta solda durum/uyarı satırı, sağda "Vazgeç" ve
 * tek birincil eylem. Birincil eylem geri alınamaz bir işse (sil, taşı ve sil) tehlike renginde
 * dolgulu çizilir; değilse vurgu renginde. Enter birincil eylemi çalıştırır (çok satırlı alanda
 * değil), Esc pencereyi kapatır.
 * <pre>{@code
 * SettingsDialog d = new SettingsDialog("Yeni işçilik", 460);
 * d.lead("Servis kaydında tek tıkla seçilir.");
 * d.body().add(FormKit.cell("Ad", nameField, nameError), "span 2");
 * d.primary("Kaydet", false, () -> save(d));
 * d.show(this, nameField);
 * }</pre>
 */
final class SettingsDialog extends JPanel {

    private static int sequence;

    private final String title;
    private final String modalId = "settings-dialog-" + (++sequence);
    private final JPanel body = new JPanel(new MigLayout("wrap 2, insets 0, fillx, gapx 14, gapy 12, hidemode 3",
            "[grow, fill, sg col][grow, fill, sg col]", "[top]"));
    private final JLabel status = new JLabel();
    private final JPanel footer = new JPanel(new MigLayout("insets 0, fillx, gap 8, hidemode 3", "[grow][][]", "[center]"));
    private JButton primary;
    private Runnable primaryAction;
    private boolean busy;

    SettingsDialog(String title, int width) {
        super(new MigLayout("wrap, insets 2 24 18 24, fillx, gap 0, hidemode 3, width " + width + "!", "[grow, fill]"));
        this.title = title;
        body.setOpaque(false);
        footer.setOpaque(false);
        status.setVisible(false);
        status.setIconTextGap(6);
        add(body, "gaptop 4");
        footer.add(status, "wmin 0, growx");
        footer.add(DetailKit.secondaryButton("Vazgeç", null, this::close));
        add(footer, "gaptop 20");

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "primary");
        getActionMap().put("primary", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (primary != null && primary.isEnabled()) primary.doClick();
            }

            @Override
            public boolean accept(Object sender) {
                // Çok satırlı alanda Enter yeni satırdır; açık bir açılır listede seçimdir.
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus instanceof JTextArea) return false;
                if (focus instanceof JComboBox<?> combo && combo.isPopupVisible()) return false;
                return true;
            }
        });
    }

    /** Başlığın altındaki soluk giriş cümlesi: pencerenin neyi değiştirdiği, neyi etkilediği. */
    SettingsDialog lead(String text) {
        JTextArea lead = FormKit.hint(text);
        lead.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; background: null; margin: 0,0,0,0");
        add(lead, "wmin 0, gapbottom 14", 0);
        return this;
    }

    /** İki kolonlu alan ızgarası; tam genişlik için {@code "span 2"}. */
    JPanel body() {
        return body;
    }

    /**
     * Tek birincil eylem. {@code danger} geri alınamaz eylemler içindir (tehlike dolgusu).
     * Eylem çalışırken pencere açık kalır; iş bitince {@link #close()} ya da {@link #idle()} çağrılır.
     */
    SettingsDialog primary(String text, boolean danger, Runnable action) {
        primaryAction = action;
        primary = danger ? dangerButton(text) : DetailKit.primaryButton(text, null, null);
        primary.addActionListener(e -> {
            if (busy) return;
            primaryAction.run();
        });
        footer.add(primary);
        return this;
    }

    void setPrimaryText(String text) {
        if (primary != null) primary.setText(text);
    }

    void setPrimaryEnabled(boolean enabled) {
        if (primary != null) primary.setEnabled(enabled);
    }

    /** Kayıt sürüyor: birincil eylem kilitlenir (çift tıklamada çift kayıt olmasın). */
    void busy() {
        busy = true;
        if (primary != null) primary.setEnabled(false);
    }

    /** İş hata ile döndü: pencere açık kalır, operatör düzeltip yeniden dener. */
    void idle() {
        busy = false;
        if (primary != null) primary.setEnabled(true);
    }

    /** Alt satırdaki durum/uyarı; {@code colorKey} null ise soluk. Boş metin gizler. */
    void status(String text, String iconPath, String colorKey) {
        status.setVisible(text != null && !text.isBlank());
        status.setText(text);
        String color = colorKey != null ? colorKey : "Label.disabledForeground";
        status.setIcon(iconPath != null ? new Ikon(iconPath, 14, color) : null);
        status.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $" + color);
    }

    void show(Component parent, JComponent focus) {
        AppModal.showModal(parent, new SimpleModalBorder(this, title, null, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED && focus != null) {
                focus.requestFocusInWindow();
                if (focus instanceof JTextComponent text && !(focus instanceof JTextArea)) text.selectAll();
            }
        }), modalId);
    }

    void close() {
        AppModal.closeModal(modalId);
    }

    // ------------------------------------------------------------------ yapı taşları

    /** Tehlike dolgulu birincil düğme: DetailKit.primaryButton anatomisi, vurgu yerine tehlike rengi. */
    static JButton dangerButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,14,7,14; font: bold;"
                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Servicio.dangerColor; foreground: $Servicio.onDangerForeground;"
                + " hoverBackground: darken($Servicio.dangerColor,6%); pressedBackground: darken($Servicio.dangerColor,12%)");
        return b;
    }

    /** Pencere gövdesinde tek satırlık etki bilgisi: soluk ikon + metin ("212 cihaz kaydı bu türde"). */
    static JLabel impact(String iconPath, String text) {
        JLabel l = new JLabel(text, new Ikon(iconPath, 16, "Label.disabledForeground"), SwingConstants.LEADING);
        l.setIconTextGap(10);
        return l;
    }
}
