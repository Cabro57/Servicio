package tr.cabro.servicio.application.panels.edit;

import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.model.enums.StockItemKind;
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
    protected CategoryScope categoryScope() {
        return CategoryScope.PART;
    }

    @Override
    protected CompletableFuture<Optional<Object[]>> findByBarcode(String barcode) {
        return ServiceManager.getPartService().get(barcode)
                .thenApply(p -> p.map(part -> new Object[]{part.getId(), part.getName()}));
    }

    @Override
    protected void addIdentityFields(JPanel grid) {
        supplierCombo = supplierCombo("Tedarikçi yok");
        grid.add(FormKit.cell("Tedarikçi", supplierCombo, null));

        modelsField = new JTextField();
        modelsField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. iPhone 11, iPhone 11 Pro");
        grid.add(FormKit.cell("Uyumlu Modeller", modelsField, FormKit.note("Virgülle ayırın; parça aramasında kullanılır.")), "span 2");
    }

    @Override
    protected StockItemKind stockKind() {
        return StockItemKind.PART;
    }

    @Override
    protected Part collectFormData(@NonNull Part data) {
        data.setBarcode(barcodeField.getText().trim());
        data.setName(nameField.getText().trim());
        data.setCategoryId(selectedCategoryId());
        data.setSupplierId(selectedSupplierId(supplierCombo));
        data.setModelCompatibility(modelsField.getText().trim());

        data.setPurchaseCurrency(priceFields.getPurchaseCurrency());
        data.setPurchasePriceOriginal(priceFields.getPurchaseOriginal());
        data.setPurchasePrice(priceFields.getPurchaseTry());
        data.setSaleCurrency(priceFields.getSaleCurrency());
        data.setSalePriceOriginal(priceFields.getSaleOriginal());
        data.setSalePrice(priceFields.getSaleTry());

        // Stok yalnızca yeni kayıtta açılış olarak yazılır; düzenlemede servis bu alanı yok sayar.
        data.setStockQuantity((Integer) stockSpinner.getValue());
        data.setOpeningWarehouseId(selectedOpeningWarehouseId());
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
        loadSupplierCombo(supplierCombo, false, data.getSupplierId());
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
