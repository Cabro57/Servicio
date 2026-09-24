package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.documents.DocumentText;
import tr.cabro.servicio.documents.DocumentTexts;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static tr.cabro.servicio.documents.DocumentText.*;

/**
 * Ayarlar &gt; İşletme &gt; Belge Metinleri: formlarda ve fişlerde basılan koşul, beyan, garanti
 * metinleri ile varsayılan garanti süresi. Buradaki değerler belge oluşturma penceresine
 * varsayılan olarak gelir; orada tek belge için ayrıca değiştirilebilir.
 * <p>
 * Metinler uzun ve birbirine bağlı olduğu için anında değil, başlıktaki Kaydet ile kaydedilir.
 */
public class SettingsDocumentTextsPanel extends JPanel implements SettingsModal.HeaderActions {

    private final Map<DocumentText, JTextComponent> fields = new EnumMap<>(DocumentText.class);
    private JSpinner warrantyDays;
    private final JButton saveButton = new JButton("Kaydet");
    private final JButton resetButton = new JButton("Varsayılanlar");

    public SettingsDocumentTextsPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());

        saveButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,16,5,16; font: bold; "
                + "background: $Component.accentColor; foreground: $Servicio.onAccentForeground");
        saveButton.addActionListener(e -> save());
        resetButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12");
        resetButton.setToolTipText("Alanlara uygulamanın hazır metinlerini yazar; kalıcı olması için Kaydet'e basın.");
        resetButton.addActionListener(e -> resetToDefaults());

        load();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(resetButton, saveButton);
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Servis ---
        warrantyDays = new JSpinner(new SpinnerNumberModel(DocumentText.DEFAULT_WARRANTY_DAYS, 0, 3650, 15));
        JPanel warrantyRow = new JPanel(new MigLayout("insets 0, gap 8", "[][80!][]", "[center]"));
        warrantyRow.setOpaque(false);
        warrantyRow.add(SettingsKit.label("Varsayılan garanti"));
        warrantyRow.add(warrantyDays, "growx");
        warrantyRow.add(SettingsKit.note("gün · 0 = garanti verilmez"));

        JPanel service = SettingsKit.stack();
        service.add(warrantyRow, "gapbottom 4");
        addTexts(service, INTAKE_TERMS, INTAKE_SLIP_NOTE, QUOTE_APPROVAL_NOTE, DELIVERY_WARRANTY_NOTE, DELIVERY_NO_WARRANTY_NOTE);
        service.add(SettingsKit.wrappingNote("Garanti notunda {gün} yazan yere garanti süresi yazılır."), "wmin 0");
        SettingsKit.section(page, "Servis belgeleri", "Cihaz kabul, teklif onayı ve teslim formları.", service);

        // --- 2. el ---
        JPanel secondHand = SettingsKit.stack();
        addTexts(secondHand, PURCHASE_CONTRACT_NOTE, SALE_CONTRACT_NOTE, WARRANTY_CERTIFICATE_NOTE);
        SettingsKit.section(page, "2. el alım-satım", "Sözleşmelerdeki beyanlar ve garanti belgesinin kapsamı.", secondHand);

        // --- Fişler ---
        JPanel slips = SettingsKit.stack();
        addTexts(slips, SLIP_FOOTER);
        // Sonradan eklenen ve yukarıda bir gruba konmamış metinler de düzenlenebilsin.
        for (DocumentText text : DocumentText.values()) {
            if (!fields.containsKey(text)) addTexts(slips, text);
        }
        slips.add(SettingsKit.wrappingNote("Boş bırakılan her metin kaydedince uygulamanın hazır metnine döner."), "wmin 0");
        SettingsKit.section(page, "Fişler", "Satış, tahsilat ve kabul fişlerinin en altı.", slips);

        return page;
    }

    private void addTexts(JPanel target, DocumentText... texts) {
        for (DocumentText text : texts) {
            JComponent input;
            if (text.isMultiline()) {
                JTextArea area = FormKit.textArea(3);
                fields.put(text, area);
                input = FormKit.areaScroll(area);
            } else {
                JTextField field = new JTextField();
                fields.put(text, field);
                input = field;
            }
            target.add(FormKit.cell(text.getLabel(), input, null), "wmin 0, gaptop 4");
        }
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
        SettingsKit.saved(this);
    }
}
