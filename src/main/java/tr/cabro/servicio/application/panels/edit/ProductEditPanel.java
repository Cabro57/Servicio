package tr.cabro.servicio.application.panels.edit;

import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.model.enums.StockItemKind;
import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * POS ürün kataloğu ekleme/düzenleme formu. {@link Product} alan kümesi parçadan bilinçli olarak
 * farklı: marka var, tedarikçi/uyumlu model yok (bkz. V21 migration notu). Ortak bölümler
 * {@link CatalogItemEditPanel}'de.
 * <p>
 * DİKKAT: alanlara başlangıç değeri verilmemeli — bkz. {@link CatalogItemEditPanel}.
 */
public class ProductEditPanel extends CatalogItemEditPanel<Product> {

    private JTextField brandField;
    private JComboBox<Supplier> wholesalerCombo;

    public ProductEditPanel(Product data) {
        super(data);
    }

    @Override
    protected String itemNoun() {
        return "Ürün";
    }

    @Override
    protected CategoryScope categoryScope() {
        return CategoryScope.PRODUCT;
    }

    @Override
    protected CompletableFuture<Optional<Object[]>> findByBarcode(String barcode) {
        return ServiceManager.getProductService().get(barcode)
                .thenApply(p -> p.map(product -> new Object[]{product.getId(), product.getName()}));
    }

    @Override
    protected void addIdentityFields(JPanel grid) {
        brandField = new JTextField();
        brandField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. Anker, Samsung");
        grid.add(FormKit.cell("Marka", brandField, null));
        wholesalerCombo = supplierCombo("Toptancı yok");
        grid.add(FormKit.cell("Toptancı", wholesalerCombo, null));
    }

    @Override
    protected StockItemKind stockKind() {
        return StockItemKind.PRODUCT;
    }

    @Override
    protected Product collectFormData(@NonNull Product data) {
        data.setBarcode(barcodeField.getText().trim());
        data.setName(nameField.getText().trim());
        data.setBrand(brandField.getText().trim());
        data.setCategoryId(selectedCategoryId());
        data.setSupplierId(selectedSupplierId(wholesalerCombo));

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
    public void populateFormWith(Product data) {
        populateCommon(data.getId(), data.getBarcode(), data.getName(), data.getCategoryId(),
                data.getStockQuantity(), data.getMinStockLevel(), data.getDescription());
        brandField.setText(data.getBrand() != null ? data.getBrand() : "");
        priceFields.setPurchase(data.getPurchaseCurrency(), data.getPurchasePriceOriginal(), data.getPurchasePrice());
        priceFields.setSale(data.getSaleCurrency(), data.getSalePriceOriginal(), data.getSalePrice());
        loadSupplierCombo(wholesalerCombo, true, data.getSupplierId());
    }

    @Override
    public void clearForm() {
        clearCommon();
        brandField.setText("");
        wholesalerCombo.setSelectedItem(null);
    }

    @Override
    protected Product createEmptyObject() {
        return new Product();
    }
}
