package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.Modal;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Onay, uyarı, bilgi ve hata pencerelerinin tek kabuğu ("karar kartı"). {@code raven.modal}
 * üzerinde çalışır ve {@link AppModal} ile açılır, bu yüzden otomatik kilitte kaybolmaz.
 * <p>
 * Anatomi her pencerede aynı: solda tonun renginde yumuşak zeminli ikon karosu, sağında kalın
 * başlık ve altında mesaj; gerekiyorsa mesajın altında ek bir bileşen (onay kutusu, metin alanı);
 * en altta sağa yaslı "Vazgeç" ve tek birincil eylem. Birincil eylem fiil adıyla yazılır ("Sil",
 * "Üzerine yaz"), "Evet/Hayır" değil.
 * <p>
 * {@link Tone#DANGER} geri alınamaz eylemler içindir: birincil düğme tehlike renginde dolgulu
 * çizilir ve açılışta odak "Vazgeç"tedir, yanlışlıkla basılan Enter hiçbir şeyi silmez. Diğer
 * tonlarda birincil düğme vurgu rengindedir ve odak ondadır (ek bileşen varsa onda). Enter odaktaki
 * düğmeye basar, Esc pencereyi kapatır.
 * <pre>{@code
 * MessageModal.of(Tone.DANGER, "Silme Onayı", "Bu not silinecek.")
 *         .primary("Sil", this::deleteNote)
 *         .show(this);
 * }</pre>
 */
public final class MessageModal extends Modal {

    /** Pencerenin anlamı: ikonu, karonun rengini ve birincil düğmenin dolgusunu belirler. */
    public enum Tone {
        /** Geri alınamaz eylem (silme, geri yükleme): tehlike ikonu ve dolgusu. */
        DANGER("icons/trash-2.svg", "Servicio.dangerColor"),
        /** Sonucu olan ama geri alınabilir onay (çıkış, üzerine yazma, stok aşımı). */
        WARNING("icons/triangle-alert.svg", "Servicio.warningColor"),
        /** Bilgilendirme. */
        INFO("icons/info.svg", "Servicio.infoColor"),
        /** Tamamlanan iş. */
        SUCCESS("icons/circle-check.svg", "Servicio.successColor"),
        /** Başarısız iş: ne olduğunu söyler, tek "Tamam" ile kapanır. */
        ERROR("icons/circle-alert.svg", "Servicio.dangerColor"),
        /** Operatörden bir değer istenir (metin/miktar girişi). */
        INPUT("icons/pen-line.svg", "Component.accentColor");

        final String icon;
        final String colorKey;

        Tone(String icon, String colorKey) {
            this.icon = icon;
            this.colorKey = colorKey;
        }
    }

    private static final int WIDTH = 440;
    private static int sequence;

    private final Tone tone;
    private final String modalId = "message-modal-" + (++sequence);
    private final JPanel text = new JPanel(new MigLayout("wrap, insets 0, fillx, gap 0, hidemode 3", "[grow, fill]"));
    private final JPanel footer = new JPanel(new MigLayout("insets 0, gap 8", "push[][]", "[center]"));
    private String primaryText;
    private Runnable primaryAction;
    private String secondaryText;
    private Runnable secondaryAction;
    private boolean secondaryVisible = true;
    private JComponent focusTarget;
    private JButton primary;
    private JButton secondary;
    private boolean done;

    private MessageModal(Tone tone, String title, String message) {
        this.tone = tone;
        setLayout(new MigLayout("insets 22 24 18 24, fillx, gap 0, width " + WIDTH + "!",
                "[]16[grow, fill]", "[top][]"));
        text.setOpaque(false);
        footer.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        text.add(titleLabel, "wmin 0, gaptop 1");
        if (message != null && !message.isBlank()) {
            text.add(messageArea(message), "wmin 0, gaptop 6");
        }

        add(new ToneTile(tone), "w 40!, h 40!");
        add(text, "wmin 0, wrap");
        add(footer, "skip, growx, gaptop 22");
    }

    /** Yeni bir pencere; {@link #show(Component)} çağrılana kadar ekrana gelmez. */
    public static MessageModal of(Tone tone, String title, String message) {
        return new MessageModal(tone, title, message);
    }

    /** Mesajın altına ek bir bileşen (onay kutusu, metin alanı, liste). */
    public MessageModal extra(JComponent component) {
        text.add(component, "wmin 0, gaptop 12");
        return this;
    }

    /** Birincil eylem: fiil adıyla ("Sil", "Kaydet"). Tıklanınca pencere kapanır, sonra eylem çalışır. */
    public MessageModal primary(String label, Runnable action) {
        this.primaryText = label;
        this.primaryAction = action;
        return this;
    }

    /** İkincil düğmenin metni ve eylemi (varsayılan "Vazgeç", eylemsiz). */
    public MessageModal secondary(String label, Runnable action) {
        this.secondaryText = label;
        this.secondaryAction = action;
        return this;
    }

    /** Tek düğmeli pencere (bilgi, hata): ikincil düğme gösterilmez. */
    public MessageModal single() {
        this.secondaryVisible = false;
        return this;
    }

    /** Açılışta odaklanacak bileşen (ör. {@link #extra} ile eklenen metin alanı). */
    public MessageModal focus(JComponent component) {
        this.focusTarget = component;
        return this;
    }

    public void show(Component parent) {
        buildFooter();
        installKeys();
        AppModal.showModal(parent, this, modalId);
    }

    public void close() {
        AppModal.closeModal(modalId);
    }

    @Override
    protected void modalOpened() {
        super.modalOpened();
        JComponent target = focusTarget != null ? focusTarget
                : (tone == Tone.DANGER && secondary != null) ? secondary
                : primary;
        if (target == null) return;
        target.requestFocusInWindow();
        if (target instanceof JTextComponent field && !(target instanceof JTextArea)) field.selectAll();
    }

    // ------------------------------------------------------------------ yapı

    private void buildFooter() {
        if (secondaryVisible) {
            String label = secondaryText != null ? secondaryText : Messages.get("dialog.button.dismiss");
            secondary = DetailKit.secondaryButton(label, null, () -> finish(secondaryAction));
            footer.add(secondary);
        }
        String label = primaryText != null ? primaryText : Messages.get("dialog.button.ok");
        primary = tone == Tone.DANGER ? dangerButton(label) : DetailKit.primaryButton(label, null, null);
        primary.addActionListener(e -> finish(primaryAction));
        footer.add(primary);
    }

    /** Enter odaktaki düğmeye basar; odak bir metin alanındaysa birincil eylemi çalıştırır. */
    private void installKeys() {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
        getActionMap().put("enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus instanceof JButton button && SwingUtilities.isDescendingFrom(button, MessageModal.this)) {
                    button.doClick();
                } else if (primary != null && primary.isEnabled()) {
                    primary.doClick();
                }
            }

            @Override
            public boolean accept(Object sender) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                return !(focus instanceof JTextArea);
            }
        });
    }

    private void finish(Runnable action) {
        if (done) return;
        done = true;
        close();
        if (action != null) action.run();
    }

    private static JTextArea messageArea(String message) {
        JTextArea area = new JTextArea(message);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.putClientProperty(FlatClientProperties.STYLE,
                "background: null; margin: 0,0,0,0;"
                        + " [light]foreground: lighten($Label.foreground,18%);"
                        + " [dark]foreground: darken($Label.foreground,12%)");
        return area;
    }

    /** Tehlike dolgulu birincil düğme: DetailKit.primaryButton anatomisi, vurgu yerine tehlike rengi. */
    private static JButton dangerButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,14,7,14; font: bold;"
                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Servicio.dangerColor; foreground: $Servicio.onDangerForeground;"
                + " hoverBackground: darken($Servicio.dangerColor,6%); pressedBackground: darken($Servicio.dangerColor,12%)");
        return b;
    }

    /**
     * Tonun ikon karosu: 40px, 12px yuvarlatılmış, tonun renginin soluk zemini üstünde aynı renkte
     * 20px ikon. Renk her boyamada temadan okunur, tema/vurgu değişince doğru çizilir.
     */
    private static final class ToneTile extends JComponent {

        private final Tone tone;
        private final Ikon icon;

        ToneTile(Tone tone) {
            this.tone = tone;
            this.icon = new Ikon(tone.icon, 20, tone.colorKey);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color base = UIManager.getColor(tone.colorKey);
            if (base == null) base = UIManager.getColor("Label.foreground");
            boolean dark = com.formdev.flatlaf.FlatLaf.isLafDark();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ColorFunctions.fade(base, dark ? 0.20f : 0.12f));
                float arc = UIScale.scale(12f);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            } finally {
                g2.dispose();
            }
            icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
        }
    }
}
