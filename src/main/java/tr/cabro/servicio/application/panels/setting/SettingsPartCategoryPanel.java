package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.renderer.list.PartCategoryListCellRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.service.PartCategoryManager;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DialogHelper;

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
        })).exceptionally(ex -> ErrorHandler.handle(this, "Parça kategorileri yüklenemedi", ex));
    }

    private void onAdd() {
        String name = categoryField.getText().trim();
        if (name.isEmpty()) {
            return;
        }

        partCategoryService.add(name).thenAccept(id -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.category.added", name));
            refreshList();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kategori eklenemedi", ex));

        categoryField.setText("");
    }

    private void onEdit(PartCategory category) {
        DialogHelper.prompt(this, "category.edit.title", "category.edit.label", category.getName(), newName -> {
            if (newName == null || newName.trim().isEmpty() || newName.trim().equals(category.getName())) {
                return;
            }

            partCategoryService.rename(category.getId(), newName.trim()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.category.updated", newName.trim()));
                refreshList();
            })).exceptionally(ex -> ErrorHandler.handle(this, "Kategori güncellenemedi", ex));
        });
    }

    private void onDelete(PartCategory category) {
        Runnable doDelete = () -> partCategoryService.delete(category.getId()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.category.deleted", category.getName()));
            refreshList();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kategori silinemedi", ex));

        if (category.getPartCount() != null && category.getPartCount() > 0) {
            DialogHelper.confirmDelete(this, "confirm.delete.category.withParts", doDelete, category.getName(), category.getPartCount());
        } else {
            DialogHelper.confirmDelete(this, "confirm.delete.category", doDelete, category.getName());
        }
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
