package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Servis parçası ekleme/düzenleme formu. Ortak bölümler (barkod, ad, kategori, fiyat, stok,
 * açıklama) {@link CatalogItemEditPanel}'de; burada parçaya özgü tedarikçi ve uyumlu modeller var.
 * <p>
 * DİKKAT: alanlara başlangıç değeri verilmemeli — bkz. {@link CatalogItemEditPanel}.
 */
public class PartEditPanel extends CatalogItemEditPanel<Part> {

    private JComboBox<Supplier> supplierCombo;
    private JTextField modelsField;

    public PartEditPanel(Part data) {
        super(data);
    }

    @Override
    protected String itemNoun() {
        return "Parça";
    }

    @Override
    protected CompletableFuture<Optional<Object[]>> findByBarcode(String barcode) {
        return ServiceManager.getPartService().get(barcode)
                .thenApply(p -> p.map(part -> new Object[]{part.getId(), part.getName()}));
    }

    @Override
    protected void addIdentityFields(JPanel grid) {
        supplierCombo = new JComboBox<>();
        supplierCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof Supplier s ? s.getBusinessName() : "Tedarikçi yok");
                return this;
            }
        });
        grid.add(FormKit.cell("Tedarikçi", supplierCombo, null));

        modelsField = new JTextField();
        modelsField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. iPhone 11, iPhone 11 Pro");
        grid.add(FormKit.cell("Uyumlu Modeller", modelsField, FormKit.note("Virgülle ayırın; parça aramasında kullanılır.")), "span 2");
    }

    private void loadSuppliers(Long selectedSupplierId) {
        ServiceManager.getSupplierService().getAll().thenAccept(suppliers -> SwingUtilities.invokeLater(() -> {
            supplierCombo.removeAllItems();
            supplierCombo.addItem(null);
            Supplier target = null;
            for (Supplier s : suppliers) {
                supplierCombo.addItem(s);
                if (s.getId().equals(selectedSupplierId)) target = s;
            }
            supplierCombo.setSelectedItem(target);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Tedarikçi listesi yüklenemedi", ex);
            return null;
        });
    }

    @Override
    protected Part collectFormData(@NonNull Part data) {
        data.setBarcode(barcodeField.getText().trim());
        data.setName(nameField.getText().trim());
        data.setCategoryId(selectedCategoryId());
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
        data.setSupplierId(supplier != null ? supplier.getId() : null);
        data.setModelCompatibility(modelsField.getText().trim());

        data.setPurchaseCurrency(priceFields.getPurchaseCurrency());
        data.setPurchasePriceOriginal(priceFields.getPurchaseOriginal());
        data.setPurchasePrice(priceFields.getPurchaseTry());
        data.setSaleCurrency(priceFields.getSaleCurrency());
        data.setSalePriceOriginal(priceFields.getSaleOriginal());
        data.setSalePrice(priceFields.getSaleTry());

        data.setStockQuantity((Integer) stockSpinner.getValue());
        data.setMinStockLevel((Integer) minStockSpinner.getValue());
        data.setDescription(descriptionArea.getText().trim());
        return data;
    }

    @Override
    public void populateFormWith(Part data) {
        populateCommon(data.getId(), data.getBarcode(), data.getName(), data.getCategoryId(),
                data.getStockQuantity(), data.getMinStockLevel(), data.getDescription());
        modelsField.setText(data.getModelCompatibility() != null ? data.getModelCompatibility() : "");
        priceFields.setPurchase(data.getPurchaseCurrency(), data.getPurchasePriceOriginal(), data.getPurchasePrice());
        priceFields.setSale(data.getSaleCurrency(), data.getSalePriceOriginal(), data.getSalePrice());
        loadSuppliers(data.getSupplierId());
    }

    @Override
    public void clearForm() {
        clearCommon();
        modelsField.setText("");
        supplierCombo.setSelectedItem(null);
    }

    @Override
    protected Part createEmptyObject() {
        return new Part();
    }
}
