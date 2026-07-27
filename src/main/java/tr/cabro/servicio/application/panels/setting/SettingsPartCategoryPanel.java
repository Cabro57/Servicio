package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.renderer.list.PartCategoryListCellRenderer;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.service.PartCategoryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

public class SettingsPartCategoryPanel extends JPanel {

    private final DefaultListModel<PartCategory> categoryModel;
    private final PartCategoryManager partCategoryService;

    public SettingsPartCategoryPanel() {
        this.partCategoryService = ServiceManager.getPartCategoryManager();
        this.categoryModel = new DefaultListModel<>();
        init();
    }

    private void init() {
        initComponent();
        refreshList();

        categoryField.addActionListener(e -> onAdd());
        addButton.addActionListener(e -> onAdd());
    }

    private void refreshList() {
        partCategoryService.getAll().thenAccept(categories -> SwingUtilities.invokeLater(() -> {
            categoryModel.clear();
            categories.forEach(categoryModel::addElement);
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.WARNING, ex.getMessage()));
            Servicio.getLogger().error(ex.getMessage(), ex);
            return null;
        });
    }

    private void onAdd() {
        String name = categoryField.getText().trim();
        if (name.isEmpty()) {
            return;
        }

        partCategoryService.add(name).thenAccept(id -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, "Kategori eklendi: " + name);
            refreshList();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.WARNING, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
            return null;
        });

        categoryField.setText("");
    }

    private void onEdit(PartCategory category) {
        String newName = (String) JOptionPane.showInputDialog(this, "Yeni kategori adı:",
                "Kategori Düzenle", JOptionPane.PLAIN_MESSAGE, null, null, category.getName());
        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(category.getName())) {
            return;
        }

        partCategoryService.rename(category.getId(), newName.trim()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, "Kategori güncellendi: " + newName.trim());
            refreshList();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.WARNING, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
            return null;
        });
    }

    private void onDelete(PartCategory category) {
        int confirm = JOptionPane.showConfirmDialog(this,
                (category.getPartCount() != null && category.getPartCount() > 0)
                        ? category.getName() + " kategorisini silmek istediğinize emin misiniz?\n" +
                          "Bu kategorideki " + category.getPartCount() + " parça \"kategorisiz\" olarak kalacak."
                        : category.getName() + " kategorisini silmek istediğinize emin misiniz?",
                "Kategori Sil", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        partCategoryService.delete(category.getId()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, "Kategori silindi: " + category.getName());
            refreshList();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.WARNING, ex.getMessage()));
            return null;
        });
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 5, fill, wrap 2", "[grow][pref!]", "[]1[]15[][grow][]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        JLabel title = new JLabel("Parça Kategorileri");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h2.font");

        JLabel subtitle = new JLabel("Stok/parça ekranlarında seçilecek kategoriler.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        categoryField = new JTextField();
        categoryField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni Kategori...");

        addButton = new JButton(new Ikon("icons/plus.svg", categoryField.getFont().getSize()));

        categoryList = new JList<>();
        categoryList.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        categoryList.setCellRenderer(new PartCategoryListCellRenderer(categoryList, this::onEdit, this::onDelete));
        categoryList.setModel(categoryModel);

        add(title, "cell 0 0");
        add(subtitle, "cell 0 1, wrap");

        add(categoryField, "growx");
        add(addButton, "wrap");
        add(categoryList, "span 2, grow");
    }

    private JTextField categoryField;
    private JButton addButton;
    private JList<PartCategory> categoryList;
}
