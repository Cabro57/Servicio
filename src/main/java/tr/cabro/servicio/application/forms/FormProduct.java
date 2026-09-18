package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;

/** Ürün detayı — salt okunur. Bkz. {@link tr.cabro.servicio.application.forms.FormPart} (servis parçası muadili). */
public class FormProduct extends Form {

    private Product product;
    private final ProductService productService;

    private JLabel lblProductName;
    private JLabel valSku, valBrand, valCategory, valPurchasePrice, valSalePrice, valStock, valMinStock;

    public FormProduct(Product product) {
        this.product = product;
        this.productService = ServiceManager.getProductService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 20", "[::320][grow]", "[pref][grow]"));
        createHeader();
        createInfoCard();
        refreshData();
    }

    @Override
    public void formRefresh() {
        productService.getById(product.getId()).thenAccept(updated ->
                updated.ifPresent(p -> {
                    this.product = p;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    private void createHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 15", "[][grow]", "[]"));
        header.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 1.2f));
        btnBack.putClientProperty(FlatClientProperties.STYLE,
                "arc: 15; background: lighten($Panel.background, 5%); borderWidth: 0; margin: 8,10,8,10;");
        btnBack.addActionListener(e -> FormManager.undo());
        header.add(btnBack, "aligny center");

        JPanel titleBox = new JPanel(new MigLayout("insets 0, gap 0", "[grow]", "[][]"));
        titleBox.setOpaque(false);

        lblProductName = new JLabel(product.getName());
        lblProductName.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");

        JLabel lblSubtitle = new JLabel("Ürün Detayı");
        lblSubtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        titleBox.add(lblProductName, "wrap");
        titleBox.add(lblSubtitle);
        header.add(titleBox, "grow");

        add(header, "span 2, growx, wrap");
    }

    private void createInfoCard() {
        JPanel card = new JPanel(new MigLayout("insets 20, gapy 15, fillx", "[25!][grow]", "[]15[][][][][][]"));
        card.putClientProperty(FlatClientProperties.STYLE, "arc: 20; background: lighten($Panel.background, 3%);");

        JLabel title = new JLabel("Ürün Bilgileri");
        title.setIcon(new Ikon("icons/shopping-bag.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "span 2, wrap");

        valSku = new JLabel("-");
        valBrand = new JLabel("-");
        valCategory = new JLabel("-");
        valPurchasePrice = new JLabel("-");
        valSalePrice = new JLabel("-");
        valStock = new JLabel("-");
        valMinStock = new JLabel("-");

        addInfoRow(card, "icons/barcode.svg", "SKU / Barkod", valSku);
        addInfoRow(card, "icons/tag.svg", "Marka", valBrand);
        addInfoRow(card, "icons/tag.svg", "Kategori", valCategory);
        addInfoRow(card, "icons/turkish-lira.svg", "Alış Fiyatı", valPurchasePrice);
        addInfoRow(card, "icons/turkish-lira.svg", "Satış Fiyatı", valSalePrice);
        addInfoRow(card, "icons/sigma.svg", "Stok", valStock);
        addInfoRow(card, "icons/circle-alert.svg", "Minimum Stok", valMinStock);

        add(card, "cell 0 1, aligny top, growx");
    }

    private void addInfoRow(JPanel parent, String iconPath, String label, JLabel valueLabel) {
        JLabel icon = new JLabel(new Ikon(iconPath, 0.75f));
        icon.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        JLabel lblLabel = new JLabel(label);
        lblLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        valueLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");

        parent.add(icon, "aligny top, span 1 2");
        parent.add(lblLabel, "wrap");
        parent.add(valueLabel, "gapbottom 10, wrap");
    }

    private void refreshData() {
        lblProductName.setText(product.getName());
        valSku.setText(product.getBarcode());
        valBrand.setText(product.getBrand() != null && !product.getBrand().isBlank() ? product.getBrand() : "-");
        valCategory.setText(product.getCategory() != null ? product.getCategory().getName() : "-");
        valPurchasePrice.setText(Format.formatPrice(product.getPurchasePrice()));
        valSalePrice.setText(Format.formatPrice(product.getSalePrice()));
        valStock.setText(String.valueOf(product.getStockQuantity()));
        valMinStock.setText(String.valueOf(product.getMinStockLevel()));
    }
}
