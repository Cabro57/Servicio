package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ayarlar ekranı: solda kategori ağacı, sağda seçilen sayfa (IntelliJ'in ayar penceresi gibi).
 * <p>
 * Eskiden bu ekran sekmeli bir {@code Form}'du ve ana pencerede açılıyordu; artık modal olarak
 * açılıyor (bkz. {@code FormManager.showSettings}) — böylece ayar yapmak için üzerinde çalışılan
 * form kapanmıyor.
 * <p>
 * Sayfalar ilk seçildiklerinde oluşturulur: sözlük panelleri veritabanından okuduğu için
 * hepsini birden kurmak modalın açılışını gereksiz yavaşlatırdı.
 */
public class SettingsModal extends JPanel {

    public static final String MODAL_ID = "app-settings";

    /** Kategori adı → sayfa adı → sayfayı üreten fonksiyon. */
    private final Map<String, Map<String, Supplier<JComponent>>> categories = new LinkedHashMap<>();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, Boolean> createdPages = new LinkedHashMap<>();

    private JTree tree;

    public SettingsModal() {
        buildCategories();
        initComponent();
    }

    private void buildCategories() {
        page("Uygulama", "Genel", SettingsMainPanel::new);
        page("Uygulama", "Görünüm", AppearancePanel::new);

        page("İşletme", "İşletme Bilgileri", SettingsBusinessPanel::new);
        page("İşletme", "WhatsApp Şablonları", SettingsTemplatesPanel::new);

        page("Sözlükler", "Cihazlar", SettingsDevicePanel::new);
        page("Sözlükler", "Tamirler", SettingsRepairPanel::new);
        page("Sözlükler", "Parça Kategorileri", SettingsPartCategoryPanel::new);

        page("Sistem", "Güvenlik", SettingsSecurityPanel::new);
        page("Sistem", "Yedekleme", SettingsDatabasePanel::new);
    }

    private void page(String category, String name, Supplier<JComponent> factory) {
        categories.computeIfAbsent(category, key -> new LinkedHashMap<>()).put(name, factory);
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 0", "[220!]0[grow,fill]", "[grow,fill]"));
        setPreferredSize(new Dimension(940, 580));

        add(createTreePane(), "growy");
        add(contentPanel, "grow");

        selectFirstPage();
    }

    private JComponent createTreePane() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Ayarlar");

        for (Map.Entry<String, Map<String, Supplier<JComponent>>> category : categories.entrySet()) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category.getKey());
            for (String page : category.getValue().keySet()) {
                categoryNode.add(new DefaultMutableTreeNode(page));
            }
            root.add(categoryNode);
        }

        tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.addTreeSelectionListener(e -> showSelectedPage());

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,0,1");
        return scrollPane;
    }

    private void showSelectedPage() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || !node.isLeaf()) return;

        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        String categoryName = String.valueOf(parent.getUserObject());
        String pageName = String.valueOf(node.getUserObject());

        showPage(categoryName, pageName);
    }

    private void showPage(String categoryName, String pageName) {
        String cardId = categoryName + "/" + pageName;

        if (!createdPages.containsKey(cardId)) {
            Supplier<JComponent> factory = categories.get(categoryName).get(pageName);
            JComponent page = factory.get();

            JScrollPane scrollPane = new JScrollPane(page);
            scrollPane.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,0,0");
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            contentPanel.add(scrollPane, cardId);
            createdPages.put(cardId, true);
        }

        cardLayout.show(contentPanel, cardId);
    }

    /** Modal açıldığında boş bir sağ panel görünmesin diye ilk sayfayı seçer. */
    private void selectFirstPage() {
        if (tree.getRowCount() > 0) {
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.setSelectionRow(row);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (node != null && node.isLeaf()) return;
            }
        }
    }
}
