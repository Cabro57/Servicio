package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
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
            categoryList.setItems(categories, null);
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
        setLayout(new MigLayout("insets 4 24 20 24, fill, wrap 2", "[grow][pref!]", "[][grow, fill]"));
        setOpaque(false);

        categoryField = new JTextField();
        categoryField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni kategori adı — Enter ile ekle");

        addButton = new JButton("Ekle", new Ikon("icons/plus.svg", 16));
        addButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,12,4,12; iconTextGap: 6");

        categoryList = new DictionaryList<PartCategory>(PartCategory::getName, null,
                c -> c.getPartCount() == null || c.getPartCount() == 0 ? "parça yok" : c.getPartCount() + " parça")
                .onEdit(this::onEdit)
                .onDelete(this::onDelete);
        categoryList.setEmptyText("Henüz kategori yok", "Parça ve ürünleri gruplamak için yukarıdan kategori ekleyin.");

        add(categoryField, "growx");
        add(addButton, "wrap");
        add(categoryList, "span 2, grow, hmin 160");
    }

    private JTextField categoryField;
    private JButton addButton;
    private DictionaryList<PartCategory> categoryList;
}
