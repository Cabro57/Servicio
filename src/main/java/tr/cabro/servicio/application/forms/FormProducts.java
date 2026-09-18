package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.AppPagination;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.ProductEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * POS satış (ürün) kataloğu listesi — bağımsız {@code products} tablosu üzerinde çalışır.
 * Bkz. {@link FormParts} (servis parçası muadili, ayrı tablo/model — V21 migration).
 */
@SystemForm(name = "Ürünler", description = "Satış (POS) ekranında listelenecek ürünleri eklemek ve düzenlemek için kullanılabilir")
public class FormProducts extends AbstractTableForm {

    private final ProductService productService;
    private GenericTableModel<Product> tableModel;

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getPartPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private JPagination pagination;

    public FormProducts() {
        this.productService = ServiceManager.getProductService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Ürün Ekle";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/package-plus.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Ürün Listesi";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Ürün adı, SKU, Marka veya Kategori ara...";
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/package-check.svg", 0.7f), "Ürün Çeşidi");
        cardBox.addCardItem(new Ikon("icons/sigma.svg", 0.7f), "Toplam Stok");
        cardBox.addCardItem(new Ikon("icons/circle-alert.svg", 0.7f), "Kritik Stok");
        cardBox.addCardItem(new Ikon("icons/turkish-lira.svg", 0.7f), "Envanter Değeri");
    }

    @Override
    protected void refreshStats() {
        productService.getStats().thenAccept(stats -> SwingUtilities.invokeLater(() -> {
            cardBox.setValueAt(0, String.valueOf(stats.getPartVarietyCount()), "Ürün Çeşidi", "", true);
            cardBox.setValueAt(1, String.valueOf(stats.getTotalStock()), "Toplam Stok", "", true);
            cardBox.setValueAt(2, String.valueOf(stats.getCriticalStockCount()), "Kritik Stok", "", true);
            cardBox.setValueAt(3, Format.formatPrice(stats.getTotalInventoryValue()), "Envanter Değeri", "", true);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Ürün istatistikleri yüklenemedi", ex));
    }

    @Override
    protected JComponent createPaginationComponent() {
        pagination = new AppPagination(5, 1, 1);
        pagination.addChangeListener(e -> {
            currentPage = pagination.getSelectedPage();
            refreshTable();
        });

        JComboBox<Integer> pageSizeCombo = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeCombo.setSelectedItem(pageSize);
        pageSizeCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        pageSizeCombo.addActionListener(e -> {
            pageSize = (Integer) pageSizeCombo.getSelectedItem();
            currentPage = 1;
            AppSettings.get().getTables().setPartPageSize(pageSize);
            AppSettings.save();
            refreshTable();
        });

        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 10", "[][]", "[]"));
        panel.setOpaque(false);
        panel.add(new JLabel("Sayfa başına:"));
        panel.add(pageSizeCombo);
        panel.add(pagination);
        return panel;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Product>> columns = Arrays.asList(
                new ColumnDef<>("SKU", String.class, Product::getBarcode),
                new ColumnDef<>("Ürün Adı", String.class, Product::getName),
                new ColumnDef<>("Marka", String.class, Product::getBrand),
                new ColumnDef<>("Kategori", String.class, p -> p.getCategory() != null ? p.getCategory().getName() : "-"),
                new ColumnDef<>("Stok", Integer.class, Product::getStockQuantity),
                new ColumnDef<>("Birim Fiyat", BigDecimal.class, Product::getSalePrice),
                new ColumnDef<>("İşlem", String.class, p -> "Detay")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        configureTableColumns();
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.CENTER, SwingConstants.TRAILING, SwingConstants.CENTER
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: +1"));

        table.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });

        table.getColumnModel().getColumn(5).setCellRenderer(new CurrencyTableCellRenderer());

        table.getColumnModel().getColumn(6).setCellRenderer(new ActionButtonRenderer());
        TableActionColumnSupport.install(table, 6, tableModel, new TableActionColumnSupport.Handlers<Product>() {
            @Override
            public void onEdit(Product product) {
                openEditModal(product);
            }

            @Override
            public void onDelete(Product selectedProduct) {
                ModalDialog.showModal(FormProducts.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        Messages.get("confirm.delete.part"), Messages.get("confirm.delete.title"),
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {

                    if (action == SimpleModalBorder.YES_OPTION) {
                        productService.delete(selectedProduct.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormProducts.this, Toast.Type.SUCCESS, Messages.get("toast.part.deleted"));
                                refreshTable();
                            });
                        }).exceptionally(ex -> ErrorHandler.handle(FormProducts.this, "Ürün silinemedi", ex));
                    }
                }));
            }

            @Override
            public void onView(Product product) {
                if (product != null) FormManager.showForm(new FormProduct(product));
            }
        });

        table.getColumnModel().getColumn(0).setMinWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setMaxWidth(70);
        table.getColumnModel().getColumn(6).setMaxWidth(180);
        table.getColumnModel().getColumn(6).setMinWidth(120);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz ürün yok"; }

    @Override
    protected String getEmptyStateDescription() { return "POS ekranında satabilmek için önce ürün ekleyin."; }

    @Override
    protected void loadTableData() {
        CompletableFuture<PageResult<Product>> future = currentSearchTerm.isEmpty()
                ? productService.getAllPaged(currentPage, pageSize)
                : productService.searchPaged(currentSearchTerm, currentPage, pageSize);

        future.thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (pagination != null) pagination.setPageRange(result.getPage(), result.getTotalPages());
                refreshStats();
                refreshLayout();
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::resetKeyboardActions);
            return ErrorHandler.handle(this, "Ürün tablosu yenilenemedi", ex);
        });
    }

    @Override
    protected void onNew() {
        final String id = "ProductNew";
        ProductEditPanel panel = new ProductEditPanel(new Product());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Yeni Ürün Ekle", options,
            (controller, action) -> {
                if (action == SimpleModalBorder.OK_OPTION) {
                    Product updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    updated.setCreatedAt(LocalDateTime.now());
                    productService.save(updated, false).thenAccept(product -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Ürün eklenemedi", ex);
                    });
                }
            })
        , id);
    }

    private void openEditModal(Product product) {
        final String id = "ProductEdit";
        ProductEditPanel panel = new ProductEditPanel(product);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Ürün Düzenle", options,
            (controller, action) -> {
                 if (action == SimpleModalBorder.OK_OPTION) {
                    Product updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    productService.save(updated, true).thenAccept(upgrade -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Ürün güncellenemedi", ex);
                    });
                }
            })
        , id);
    }
}
