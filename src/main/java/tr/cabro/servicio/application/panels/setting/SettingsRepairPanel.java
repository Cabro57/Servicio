package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.LaborService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tamirler (hazır işçilik kalemleri): kartın üstünde cihaz türü sekmeleri (sayılı) ve arama,
 * altında işçilik satırları — kalın ad, altında tür ve açıklama, sağda varsayılan ücret.
 * Satıra tıklamak ya da Enter düzenler; düzenle/sil satırın üstüne gelince görünür.
 */
public class SettingsRepairPanel extends JPanel implements SettingsModal.HeaderActions {

    private static final String ALL = "all";
    private static final String GENERAL = "general";

    private final LaborService laborService = ServiceManager.getLaborService();
    private final ViewTabs views = new ViewTabs();
    private final JTextField search = new JTextField();
    private final DictionaryList<Labor> list;
    private final JButton addButton = SettingsKit.headerButton("Yeni işçilik", "icons/plus.svg");
    private List<Labor> all = List.of();
    private List<DeviceType> types = List.of();

    public SettingsRepairPanel() {
        list = new DictionaryList<>(Labor::getName)
                .subtitle(SettingsRepairPanel::subtitle)
                .trailing(l -> l.getDefaultPrice() != null && l.getDefaultPrice().signum() > 0
                        ? Format.formatPrice(l.getDefaultPrice()) : "ücret yok",
                        l -> l.getDefaultPrice() == null || l.getDefaultPrice().signum() == 0)
                .onOpen(this::edit)
                .action("icons/pencil.svg", "Düzenle", this::edit)
                .action("icons/trash-2.svg", "Sil", true, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), null, this::delete)
                .bindActionKeys();
        list.setEmptyText("Bu türde işçilik yok", "Sık yaptığınız işleri ücretiyle ekleyin; servis kaydında tek tıkla seçilir.");

        initComponent();

        views.addView(ALL, "Tümü");
        views.addView(GENERAL, "Genel");
        views.setOnChange(k -> applyView());
        addButton.addActionListener(e -> add());

        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            types = list;
            for (DeviceType t : list) views.addView(typeKey(t.getId()), t.getName());
            views.revalidate();
            updateCounts();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türleri yüklenemedi", ex));
        reload();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(addButton);
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 4 24 20 24", "[grow, fill]", "[grow, fill]"));
        setOpaque(false);

        JPanel card = new JPanel(new MigLayout("insets 12 8 8 8, fill, wrap", "[grow, fill]", "[]8[grow, fill]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel top = new JPanel(new MigLayout("insets 0 6 0 6, fillx, gap 12", "[grow, fill][180:220:260, fill]", "[center]"));
        top.setOpaque(false);
        top.add(views, "wmin 0");
        top.add(search);
        card.add(top);
        card.add(list, "grow, hmin 160");
        add(card, "grow");

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İşçilik ara");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new Ikon("icons/search.svg", 14, "Label.disabledForeground"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 3,8,3,8");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { list.filter(search.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { list.filter(search.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        search.addActionListener(e -> list.requestFocusInWindow());
    }

    private static String typeKey(Long id) {
        return "type:" + id;
    }

    private static String subtitle(Labor l) {
        String type = l.getDeviceTypeId() == null ? "Genel" : l.getDeviceTypeName();
        String desc = l.getDescription();
        return desc != null && !desc.isBlank() ? type + "  ·  " + desc : type;
    }

    private void reload() {
        laborService.getAll().thenAccept(labors -> SwingUtilities.invokeLater(() -> {
            all = labors;
            updateCounts();
            applyView();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşçilik listesi yenilenemedi", ex));
    }

    private boolean matches(Labor l, String key) {
        if (key == null || ALL.equals(key)) return true;
        if (GENERAL.equals(key)) return l.getDeviceTypeId() == null;
        return l.getDeviceTypeId() != null && key.equals(typeKey(l.getDeviceTypeId()));
    }

    private void updateCounts() {
        views.setCount(ALL, (long) all.size());
        views.setCount(GENERAL, all.stream().filter(l -> matches(l, GENERAL)).count());
        for (DeviceType t : types) views.setCount(typeKey(t.getId()), all.stream().filter(l -> matches(l, typeKey(t.getId()))).count());
    }

    private void applyView() {
        String key = views.getSelected();
        list.setItems(all.stream().filter(l -> matches(l, key)).collect(Collectors.toList()));
        list.filter(search.getText());
    }

    /** Yeni işçilik: seçili sekmenin türüyle başlar ("Telefon" sekmesindeyken telefon işçiliği). */
    private void add() {
        Labor labor = new Labor();
        String key = views.getSelected();
        if (key != null && key.startsWith("type:")) labor.setDeviceTypeId(Long.parseLong(key.substring(5)));
        edit(labor);
    }

    private void edit(Labor labor) {
        DictionaryDialogs.labor(this, labor, new ArrayList<>(types),
                l -> laborService.save(l, l.getId() != null),
                () -> {
                    reload();
                    SettingsKit.saved(this);
                });
    }

    private void delete(Labor l) {
        DictionaryDialogs.confirm(this, "\"" + l.getName() + "\" silinsin mi?",
                "İşçilik seçim listelerinden kalkar. Bu işçiliğin eklendiği eski servis kayıtları değişmez.",
                "Sil", () -> laborService.delete(l.getId()), () -> {
                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.labor.deleted"));
                    reload();
                });
    }
}
