package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.service.PartCategoryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ayarlar &gt; Kategoriler: parça ve ürün kategorileri tek listede. Her kategorinin kapsamı hangi
 * ekranda seçilebildiğini söyler (Parça / Ürün / ikisi de). Sekmeler kapsama göre süzer; satırda
 * parça ve ürün sayıları ayrı yazılır. Kullanımdaki kategori silinirken kayıtları başka bir
 * kategoriye taşınabilir ya da kategorisiz bırakılabilir.
 */
public class SettingsCategoryPanel extends JPanel implements SettingsModal.HeaderActions {

    private static final String ALL = "all";

    private final PartCategoryManager categories = ServiceManager.getPartCategoryManager();
    private final ViewTabs views = new ViewTabs();
    private final JTextField search = new JTextField();
    private final DictionaryList<PartCategory> list;
    private final JButton addButton = SettingsKit.headerButton("Yeni kategori", "icons/plus.svg");
    private List<PartCategory> all = List.of();

    public SettingsCategoryPanel() {
        list = new DictionaryList<>(PartCategory::getName)
                .subtitle(c -> c.getScope() != null ? c.getScope().getLabel() : CategoryScope.BOTH.getLabel())
                .trailing(SettingsCategoryPanel::usage, c -> c.parts() == 0 && c.products() == 0)
                .onOpen(this::edit)
                .action("icons/pencil.svg", "Düzenle", this::edit)
                .action("icons/trash-2.svg", "Sil", true, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), null, this::delete)
                .bindActionKeys();
        list.setEmptyText("Henüz kategori yok", "Parça ve ürünleri gruplamak için \"Yeni kategori\" ile ekleyin.");

        initComponent();
        views.addView(ALL, "Tümü");
        views.addView(CategoryScope.PART.name(), "Parça");
        views.addView(CategoryScope.PRODUCT.name(), "Ürün");
        views.setOnChange(k -> applyView());
        addButton.addActionListener(e -> add());
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

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Kategori ara");
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

    private static String usage(PartCategory c) {
        if (c.parts() == 0 && c.products() == 0) return "kullanılmıyor";
        List<String> parts = new ArrayList<>();
        if (c.parts() > 0) parts.add(c.parts() + " parça");
        if (c.products() > 0) parts.add(c.products() + " ürün");
        return String.join("  ·  ", parts);
    }

    private void reload() {
        categories.getAll().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            all = list;
            views.setCount(ALL, (long) list.size());
            views.setCount(CategoryScope.PART.name(), list.stream().filter(c -> c.appliesTo(CategoryScope.PART)).count());
            views.setCount(CategoryScope.PRODUCT.name(), list.stream().filter(c -> c.appliesTo(CategoryScope.PRODUCT)).count());
            applyView();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kategoriler yüklenemedi", ex));
    }

    private CategoryScope selectedScope() {
        String key = views.getSelected();
        return key == null || ALL.equals(key) ? null : CategoryScope.valueOf(key);
    }

    private void applyView() {
        CategoryScope scope = selectedScope();
        list.setItems(all.stream().filter(c -> scope == null || c.appliesTo(scope)).collect(Collectors.toList()));
        list.filter(search.getText());
    }

    private void done() {
        reload();
        SettingsKit.saved(this);
    }

    /** Yeni kategori seçili sekmenin kapsamıyla başlar ("Ürün" sekmesindeyken ürün kategorisi). */
    private void add() {
        CategoryScope scope = selectedScope();
        DictionaryDialogs.category(this, null, scope != null ? scope : CategoryScope.BOTH,
                (name, s) -> categories.add(name, s), this::done);
    }

    private void edit(PartCategory category) {
        DictionaryDialogs.category(this, category, null,
                (name, s) -> categories.update(category.getId(), name, s), this::done);
    }

    private void delete(PartCategory category) {
        List<DictionaryDialogs.Impact> impacts = new ArrayList<>();
        if (category.parts() > 0) impacts.add(new DictionaryDialogs.Impact("icons/circuit-board.svg", category.parts() + " parça"));
        if (category.products() > 0) impacts.add(new DictionaryDialogs.Impact("icons/shopping-bag.svg", category.products() + " ürün"));
        DictionaryDialogs.delete(this, "kategori", category, impacts, all, "Kategorisiz bırak", null,
                target -> categories.delete(category.getId(), target != null ? target.getId() : null), this::done);
    }
}
