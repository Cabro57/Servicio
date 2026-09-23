package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.documents.DocumentText;
import tr.cabro.servicio.documents.DocumentTexts;
import tr.cabro.servicio.i18n.Messages;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ayarlar &gt; İşletme &gt; Belge Metinleri: formlarda ve fişlerde basılan koşul, beyan, garanti
 * metinleri ile varsayılan garanti süresi. Buradaki değerler belge oluşturma penceresine
 * varsayılan olarak gelir; orada tek belge için ayrıca değiştirilebilir.
 */
public class SettingsDocumentTextsPanel extends JPanel {

    private final Map<DocumentText, JTextComponent> fields = new EnumMap<>(DocumentText.class);
    private JSpinner warrantyDays;

    public SettingsDocumentTextsPanel() {
        initComponent();
        load();
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][grow]"));

        JPanel warranty = new JPanel(new MigLayout("insets 10, gapx 8", "[][80!][]", "[]"));
        warranty.setBorder(BorderFactory.createTitledBorder("Servis Teslim Garantisi"));
        warranty.add(new JLabel("Varsayılan garanti süresi:"));
        warrantyDays = new JSpinner(new SpinnerNumberModel(DocumentText.DEFAULT_WARRANTY_DAYS, 0, 3650, 15));
        warranty.add(warrantyDays);
        warranty.add(hint("gün  ·  0 = garanti verilmez"));
        add(warranty, "growx, wrap");

        JPanel texts = new JPanel(new MigLayout("fillx, insets 10, wrap", "[grow, fill]", ""));
        texts.setBorder(BorderFactory.createTitledBorder("Belge Metinleri"));
        texts.add(hint("<html>Garanti notunda <b>{gün}</b> yazan yere garanti süresi yazılır. "
                + "Boş bırakılan metin varsayılana döner.</html>"), "wmin 0, gapbottom 4");
        for (DocumentText text : DocumentText.values()) {
            JLabel label = new JLabel(text.getLabel());
            label.putClientProperty(FlatClientProperties.STYLE, "font: -1 bold");
            texts.add(label, "gaptop 6");
            if (text.isMultiline()) {
                JTextArea area = new JTextArea(3, 40);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
                area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
                JScrollPane sp = new JScrollPane(area);
                sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                texts.add(sp, "hmin 64, wmin 0");
                fields.put(text, area);
            } else {
                JTextField field = new JTextField();
                texts.add(field, "wmin 0");
                fields.put(text, field);
            }
        }

        JButton save = new JButton("Kaydet");
        save.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        save.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Component.accentColor");
        save.addActionListener(e -> save());
        JButton reset = new JButton("Varsayılanlara Dön");
        reset.setToolTipText("Alanlara uygulamanın hazır metinlerini yazar; kalıcı olması için Kaydet'e basın.");
        reset.addActionListener(e -> resetToDefaults());
        JPanel actions = new JPanel(new MigLayout("insets 6 0 0 0", "push[][]", "[]"));
        actions.setOpaque(false);
        actions.add(reset);
        actions.add(save);
        texts.add(actions, "growx");

        add(texts, "growx, wrap");
        add(new JLabel(), "pushy, growy, wrap");
    }

    private void load() {
        warrantyDays.setValue(DocumentTexts.getWarrantyDays());
        for (Map.Entry<DocumentText, JTextComponent> entry : fields.entrySet()) {
            entry.getValue().setText(DocumentTexts.get(entry.getKey()));
            entry.getValue().setCaretPosition(0);
        }
    }

    private void resetToDefaults() {
        warrantyDays.setValue(DocumentText.DEFAULT_WARRANTY_DAYS);
        for (Map.Entry<DocumentText, JTextComponent> entry : fields.entrySet()) {
            entry.getValue().setText(entry.getKey().getDefaultText());
            entry.getValue().setCaretPosition(0);
        }
    }

    private void save() {
        DocumentTexts.setWarrantyDays(((Number) warrantyDays.getValue()).intValue());
        for (Map.Entry<DocumentText, JTextComponent> entry : fields.entrySet()) {
            DocumentTexts.set(entry.getKey(), entry.getValue().getText());
        }
        load(); // Boş bırakılanlar varsayılanla dolsun.
        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.document.textsSaved"));
    }

    private static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty("FlatLaf.styleClass", "small");
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return l;
    }
}
