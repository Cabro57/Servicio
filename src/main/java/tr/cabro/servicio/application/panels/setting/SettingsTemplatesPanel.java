package tr.cabro.servicio.application.panels.setting;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.DocumentTemplate;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.enums.TemplateType;
import tr.cabro.servicio.service.DocumentTemplateService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.TemplateEngine;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp şablonları: solda şablon listesi, ortada yerinde düzenleyici (ad + metin + tıklanınca
 * imlece eklenen alan çipleri), sağda örnek bir servis kaydıyla doldurulmuş canlı önizleme balonu.
 * Eski ekran tek kolonluk bir tablo ve ayrı bir düzenleme penceresiydi; alan adları düz metin
 * listesinde okunmuyor, sonucun nasıl görüneceği gönderene kadar bilinmiyordu.
 */
public class SettingsTemplatesPanel extends JPanel implements SettingsModal.HeaderActions {

    /** Alan çipleri: görünen ad → şablon anahtarı; gruplar alt alta. */
    private static final String[][][] TOKEN_GROUPS = {
            {{"Müşteri"}, {"Ad soyad", "musteri_adi"}, {"Telefon", "musteri_telefon"}},
            {{"Cihaz"}, {"Cihaz", "cihaz_bilgisi"}, {"Marka", "cihaz_marka"}, {"Model", "cihaz_model"}, {"Seri no", "cihaz_seri_no"}},
            {{"Servis"}, {"Servis no", "servis_no"}, {"Arıza", "ariza_aciklamasi"}, {"Durum", "servis_durumu"}, {"Teslim tarihi", "teslim_tarihi"}, {"Bugün", "bugunun_tarihi"}},
            {{"Ücret"}, {"Toplam", "toplam_tutar"}, {"Ödenen", "odenen_tutar"}, {"Kalan", "kalan_tutar"}},
            {{"İşletme"}, {"İşletme adı", "isletme_adi"}, {"Telefon", "isletme_telefon"}, {"Adres", "isletme_adres"}},
    };

    private final DocumentTemplateService templateService;
    private final DefaultListModel<DocumentTemplate> listModel = new DefaultListModel<>();
    private final JList<DocumentTemplate> list = new JList<>(listModel);
    private final JButton addButton;

    private final JTextField nameField = new JTextField();
    private final JTextArea bodyArea = new JTextArea(9, 20);
    private final JTextArea preview = new JTextArea();
    private final JButton saveButton;
    private final JButton deleteButton;
    private final JPanel editor;
    private final JPanel emptyEditor;

    private DocumentTemplate current;
    private Map<String, String> sampleTokens = sampleTokens(null);
    private boolean loading;

    public SettingsTemplatesPanel() {
        templateService = ServiceManager.getDocumentTemplateService();
        setLayout(new MigLayout("fill, insets 4 24 20 24, gap 16", "[210!, fill][grow, fill]", "[grow, fill]"));
        setOpaque(false);

        addButton = SettingsKit.headerButton("Yeni şablon", "icons/plus.svg");
        addButton.addActionListener(e -> startNew());

        // --- Liste ---
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new TemplateRenderer());
        list.putClientProperty(FlatClientProperties.STYLE, "selectionArc: 10; selectionInsets: 2,4,2,4");
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) edit(list.getSelectedValue());
        });
        JPanel listCard = new JPanel(new MigLayout("insets 6, fill", "[grow, fill]", "[grow, fill]"));
        listCard.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listCard.add(listScroll);
        add(listCard, "grow");

        // --- Düzenleyici ---
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ör. Cihazınız hazır");
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Merhaba {musteri_adi}, {cihaz_bilgisi} cihazınız teslime hazır…");
        DocumentListener onEdit = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        };
        nameField.getDocument().addDocumentListener(onEdit);
        bodyArea.getDocument().addDocumentListener(onEdit);

        saveButton = new JButton("Kaydet");
        saveButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,16,6,16; font: bold; borderWidth: 0; focusWidth: 0;"
                + " background: $Component.accentColor; foreground: $Servicio.onAccentForeground;"
                + " hoverBackground: darken($Component.accentColor,6%)");
        saveButton.addActionListener(e -> save());
        deleteButton = new JButton("Sil");
        deleteButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        deleteButton.putClientProperty(FlatClientProperties.STYLE, "foreground: $Servicio.dangerColor; margin: 4,10,4,10");
        deleteButton.addActionListener(e -> delete());

        editor = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 6, hidemode 3", "[grow, fill]", "[][]10[][grow, fill]10[]12[]12[]"));
        editor.setOpaque(false);
        editor.add(SettingsKit.label("Şablon adı"));
        editor.add(nameField);
        editor.add(SettingsKit.label("Mesaj"));
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        editor.add(bodyScroll, "hmin 140");
        editor.add(buildTokenChips());
        editor.add(buildPreview());
        JPanel actions = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[center]"));
        actions.setOpaque(false);
        actions.add(deleteButton);
        actions.add(saveButton);
        editor.add(actions);

        emptyEditor = new JPanel(new MigLayout("insets 40 0 0 0, wrap, fillx", "[center]", "[]6[]12[]"));
        emptyEditor.setOpaque(false);
        JLabel emptyTitle = new JLabel("Henüz şablon yok");
        emptyTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        emptyEditor.add(emptyTitle);
        emptyEditor.add(SettingsKit.note("Servis detayındaki WhatsApp düğmesi bu şablonlardan birini doldurup gönderir."));
        JButton firstButton = SettingsKit.headerButton("İlk şablonu oluştur", "icons/plus.svg");
        firstButton.addActionListener(e -> startNew());
        emptyEditor.add(firstButton);

        JPanel center = new JPanel(new CardLayout());
        center.setOpaque(false);
        center.add(editor, "editor");
        center.add(emptyEditor, "empty");
        add(center, "grow");

        loadShopForPreview();
        refreshList(null);
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(addButton);
    }

    private JComponent buildTokenChips() {
        JPanel box = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 4", "[grow, fill]", ""));
        box.setOpaque(false);
        box.add(SettingsKit.note("Alan eklemek için tıklayın; gönderirken servis kaydının bilgisiyle dolar."));
        for (String[][] group : TOKEN_GROUPS) {
            JPanel row = WrapLayout.panel(6, 4);
            row.setOpaque(false);
            JLabel cap = new JLabel(group[0][0]);
            cap.putClientProperty(FlatClientProperties.STYLE, "font: -1 bold; foreground: $Label.disabledForeground");
            cap.setPreferredSize(new Dimension(56, cap.getPreferredSize().height));
            row.add(cap);
            for (int i = 1; i < group.length; i++) {
                String label = group[i][0];
                String key = group[i][1];
                JButton chip = new JButton(label);
                chip.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 2,10,2,10; font: -1");
                chip.setToolTipText("{" + key + "}");
                chip.setFocusable(false);
                chip.addActionListener(e -> insertToken(key));
                row.add(chip);
            }
            box.add(row, "wmin 0");
        }
        return box;
    }

    private JComponent buildPreview() {
        JPanel card = new JPanel(new MigLayout("insets 12 14 12 14, fillx, wrap", "[grow, fill]", "[]8[]6[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JLabel title = new JLabel("Önizleme");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        card.add(title);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFocusable(false);
        preview.putClientProperty(FlatClientProperties.STYLE,
                "background: fade($Servicio.successColor, 14%); border: 10,12,10,12");
        card.add(preview, "wmin 0");
        card.add(SettingsKit.note("Örnek servis kaydıyla dolduruldu."));
        return card;
    }

    // -------------------------------------------------------------------------
    // Akış
    // -------------------------------------------------------------------------

    private void refreshList(Long selectId) {
        templateService.getByType(TemplateType.WHATSAPP_MESSAGE).thenAccept(templates -> SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (DocumentTemplate t : templates) listModel.addElement(t);
            boolean empty = templates.isEmpty() && current == null;
            showEditor(!empty);
            if (empty) return;
            int index = 0;
            for (int i = 0; i < listModel.size(); i++) {
                if (selectId != null && selectId.equals(listModel.get(i).getId())) index = i;
            }
            if (!templates.isEmpty()) list.setSelectedIndex(index);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Şablonlar yüklenemedi", ex));
    }

    private void showEditor(boolean editorVisible) {
        CardLayout cl = (CardLayout) editor.getParent().getLayout();
        cl.show(editor.getParent(), editorVisible ? "editor" : "empty");
    }

    private void edit(DocumentTemplate t) {
        current = t;
        loading = true;
        nameField.setText(t.getName());
        bodyArea.setText(t.getBody());
        bodyArea.setCaretPosition(0);
        loading = false;
        deleteButton.setVisible(t.getId() != null);
        saveButton.setEnabled(false);
        updatePreview();
        showEditor(true);
    }

    private void startNew() {
        list.clearSelection();
        DocumentTemplate t = new DocumentTemplate();
        t.setType(TemplateType.WHATSAPP_MESSAGE);
        t.setName("");
        t.setBody("");
        edit(t);
        saveButton.setEnabled(true);
        nameField.requestFocusInWindow();
    }

    private void changed() {
        if (loading) return;
        saveButton.setEnabled(true);
        updatePreview();
    }

    private void insertToken(String key) {
        bodyArea.replaceSelection("{" + key + "}");
        bodyArea.requestFocusInWindow();
    }

    private void updatePreview() {
        String body = bodyArea.getText();
        preview.setText(body.isBlank() ? "Mesaj metni burada görünür." : TemplateEngine.render(body, sampleTokens));
    }

    private void save() {
        String name = nameField.getText().trim();
        String body = bodyArea.getText().trim();
        if (name.isEmpty() || body.isEmpty()) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.template.missing"));
            (name.isEmpty() ? nameField : bodyArea).requestFocusInWindow();
            return;
        }
        boolean update = current != null && current.getId() != null;
        DocumentTemplate t = new DocumentTemplate();
        if (update) t.setId(current.getId());
        t.setType(TemplateType.WHATSAPP_MESSAGE);
        t.setName(name);
        t.setBody(body);
        saveButton.setEnabled(false);
        templateService.save(t, update).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
            SettingsKit.saved(this);
            current = saved;
            refreshList(saved.getId());
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> saveButton.setEnabled(true));
            return ErrorHandler.handle(this, "Şablon kaydedilemedi", ex);
        });
    }

    private void delete() {
        if (current == null || current.getId() == null) return;
        DocumentTemplate t = current;
        DialogHelper.confirmDelete(this, "confirm.delete.template", () ->
                        templateService.delete(t.getId()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.template.deleted"));
                            current = null;
                            refreshList(null);
                        })).exceptionally(ex -> ErrorHandler.handle(this, "Şablon silinemedi", ex)),
                t.getName());
    }

    // -------------------------------------------------------------------------
    // Önizleme verisi
    // -------------------------------------------------------------------------

    private void loadShopForPreview() {
        ServiceManager.getUserService().get(1L).thenAccept(shop -> SwingUtilities.invokeLater(() -> {
            sampleTokens = sampleTokens(shop.orElse(null));
            updatePreview();
        })).exceptionally(ex -> null);
    }

    /** Önizleme için örnek servis kaydı; işletme alanları gerçek işletme bilgisinden gelir. */
    private static Map<String, String> sampleTokens(User shop) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("musteri_adi", "Ayşe Yılmaz");
        t.put("musteri_telefon", "+90 532 123 45 67");
        t.put("cihaz_bilgisi", "Apple iPhone 13");
        t.put("cihaz_marka", "Apple");
        t.put("cihaz_model", "iPhone 13");
        t.put("cihaz_seri_no", "F2LX12ABCD");
        t.put("servis_no", "SRV-128");
        t.put("ariza_aciklamasi", "Ekran kırık");
        t.put("servis_durumu", "Teslime hazır");
        t.put("toplam_tutar", Format.formatPrice(new java.math.BigDecimal("3200")));
        t.put("odenen_tutar", Format.formatPrice(new java.math.BigDecimal("1000")));
        t.put("kalan_tutar", Format.formatPrice(new java.math.BigDecimal("2200")));
        t.put("teslim_tarihi", Format.formatDate(LocalDate.now().plusDays(1)));
        t.put("bugunun_tarihi", Format.formatDate(LocalDate.now()));
        t.put("isletme_adi", shop != null && shop.getBusinessName() != null ? shop.getBusinessName() : "İşletmeniz");
        t.put("isletme_telefon", shop != null && shop.getPhoneNumber() != null ? PhoneHelper.formatForDisplay(shop.getPhoneNumber()) : "+90 212 000 00 00");
        t.put("isletme_adres", shop != null && shop.getAddress() != null ? shop.getAddress() : "İşletme adresi");
        return t;
    }

    /** Liste satırı: kalın şablon adı + metnin ilk satırı (soluk). */
    private static final class TemplateRenderer extends JPanel implements ListCellRenderer<DocumentTemplate> {
        private final JLabel name = new JLabel();
        private final JLabel line = new JLabel();

        TemplateRenderer() {
            super(new MigLayout("insets 8 10 8 10, wrap, gap 0 2, fillx", "[grow, fill]", "[][]"));
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            line.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            add(name, "wmin 0");
            add(line, "wmin 0");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DocumentTemplate> list, DocumentTemplate value,
                                                      int index, boolean selected, boolean focus) {
            name.setText(value.getName());
            String body = value.getBody() != null ? value.getBody().replace('\n', ' ').trim() : "";
            line.setText(body.isEmpty() ? " " : body);
            setOpaque(selected);
            setBackground(selected ? UIManager.getColor("Servicio.rowSelectedBackground") : list.getBackground());
            name.setForeground(list.getForeground());
            return this;
        }
    }
}
