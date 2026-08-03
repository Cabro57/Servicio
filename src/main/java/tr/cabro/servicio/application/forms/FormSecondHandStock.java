package tr.cabro.servicio.application.forms;

import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.secondhand.PurchasePanel;
import tr.cabro.servicio.application.panels.secondhand.SalePanel;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.documents.DeviceTransactionFormType;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.enums.DeviceTransactionType;
import tr.cabro.servicio.service.DeviceService;
import tr.cabro.servicio.service.DeviceTransactionService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 2.el alım-satım stok listesi — cihaz merkezli tasarım (bkz. proje belleği,
 * "2.el Alım-Satım Modülü" planı). Her satır bir {@link DeviceTransaction}'dır (PURCHASE veya
 * SALE); varsayılan görünüm yalnızca şu an stokta olan (en son işlemi PURCHASE olan) cihazları
 * gösterir, "Tümü" filtresiyle tüm alım/satım hareketleri görülebilir.
 */
@SystemForm(name = "2.el Alım-Satım", description = "İkinci el cihaz alım-satım stok takibi")
public class FormSecondHandStock extends AbstractTableForm {

    private final DeviceTransactionService transactionService;
    private final DeviceService deviceService;
    private GenericTableModel<DeviceTransaction> tableModel;
    private TableHeaderFilterSupport<DeviceTransaction> headerFilters;

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = 25;
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private boolean onlyInStock = true;
    private PaginationBar paginationBar;

    public FormSecondHandStock() {
        this.transactionService = ServiceManager.getDeviceTransactionService();
        this.deviceService = ServiceManager.getDeviceService();
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> { pageSize = newSize; currentPage = 1; refreshTable(); });
        return paginationBar;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    @Override
    protected String getNewButtonText() {
        return "Alım Ekle";
    }

    @Override
    protected String getTableTitleText() {
        return "İkinci El Cihazlar";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Cihaz, marka veya müşteri ara...";
    }

    @Override
    protected boolean hasFilterCombo() {
        return true;
    }

    @Override
    protected JComboBox<Object> createFilterCombo() {
        JComboBox<Object> combo = new JComboBox<>(new Object[]{"Sadece Stoktakiler", "Tümü"});
        combo.addActionListener(e -> {
            onlyInStock = combo.getSelectedIndex() == 0;
            currentPage = 1;
            refreshTable();
        });
        return combo;
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/package-check.svg", 0.7f), "Stoktaki Cihaz");
    }

    @Override
    protected void refreshStats() {
        transactionService.getCurrentStock().thenAccept(stock -> SwingUtilities.invokeLater(() ->
                cardBox.setValueAt(0, String.valueOf(stock.size()), "Satılmayı bekliyor", "", true)
        )).exceptionally(ex -> {
            Servicio.getLogger().error("2.el stok istatistikleri alınamadı", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<DeviceTransaction>> columns = Arrays.asList(
                new ColumnDef<DeviceTransaction>("Cihaz", String.class,
                        t -> t.getDevice() != null ? t.getDevice().getDisplayName() + " (" + t.getDevice().getSerialNo() + ")" : "-")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<DeviceTransaction>("Tür", String.class,
                        t -> t.getType() == DeviceTransactionType.PURCHASE ? "Alım" : "Satım")
                        .alignment(SwingConstants.CENTER),
                new ColumnDef<DeviceTransaction>("Karşı Taraf", String.class,
                        t -> t.getCustomer() != null ? t.getCustomer().getFullName() : "-")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<DeviceTransaction>("Tarih", String.class, t -> Format.formatDate(t.getTransactionDate()))
                        .alignment(SwingConstants.LEADING).dateRangeFilter("transaction_date"),
                ColumnDef.<DeviceTransaction>currency("Fiyat", DeviceTransaction::getPrice),
                ColumnDef.<DeviceTransaction>actionColumn("İşlem")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureActionColumn();
        headerFilters = installHeaderFilters(columns);
    }

    private void configureActionColumn() {
        int col = table.getColumnModel().getColumnCount() - 1;
        DynamicActionColumnSupport.install(table, col, tableModel, Arrays.asList(
                DynamicActionColumnSupport.button("icons/file-text.svg", new Color(41, 98, 255), "Form", this::showFormMenu),
                DynamicActionColumnSupport.button("icons/tag.svg", new Color(16, 163, 74), "Sat", this::openSaleModal),
                DynamicActionColumnSupport.button("icons/trash-2.svg", new Color(220, 38, 38), "Sil", this::deleteTransaction)
        ));
        table.getColumnModel().getColumn(col).setMaxWidth(150);
        table.getColumnModel().getColumn(col).setMinWidth(120);
    }

    @Override
    protected void refreshTable() {
        Map<String, ColumnFilterValue> filters = headerFilters != null ? headerFilters.getActiveFilters() : java.util.Collections.emptyMap();

        transactionService.searchFilteredPaged(currentSearchTerm, filters, onlyInStock, currentPage, pageSize).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                refreshStats();
                refreshLayout();
            });
        }).exceptionally(e -> {
            Servicio.getLogger().error("2.el alım-satım listesi alınamadı: ", e);
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Liste alınamadı!"));
            return null;
        });
    }

    // =========================================================================
    // ALIM EKLE
    // =========================================================================

    @Override
    protected void onNew() {
        final String MODAL_ID = "secondhand_purchase_modal";

        PurchasePanel[] panelRef = new PurchasePanel[1];
        panelRef[0] = new PurchasePanel(e -> AppModal.pushModalDeferred(() -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            return new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                if (a1 != SimpleModalBorder.YES_OPTION) return;
                Customer newCustomer = newCustomerPanel.getData();
                if (newCustomer == null) { c1.consume(); return; }
                c1.consume();
                newCustomer.setCreatedAt(LocalDateTime.now());
                ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved ->
                        SwingUtilities.invokeLater(() -> {
                            panelRef[0].appendNewCustomer(saved);
                            AppModal.popModal(MODAL_ID);
                        }));
            });
        }, MODAL_ID));
        PurchasePanel panel = panelRef[0];

        ServiceManager.getCustomerService().getAll().thenAccept(customers ->
                SwingUtilities.invokeLater(() -> panel.setCustomers(customers)));

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "İkinci El Alım Ekle", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) { panel.requestInitialFocus(); return; }
            if (action != SimpleModalBorder.OK_OPTION) return;

            Customer seller = panel.getSeller();
            Device device = panel.getDevice();
            if (seller == null) { controller.consume(); Toast.show(this, Toast.Type.WARNING, "Lütfen satıcı müşteri seçin."); return; }
            if (device == null) { controller.consume(); Toast.show(this, Toast.Type.WARNING, "Cihaz türü ve marka bilgileri zorunludur."); return; }

            java.util.concurrent.CompletableFuture<Device> deviceFuture = device.getId() != null
                    ? java.util.concurrent.CompletableFuture.completedFuture(device)
                    : deviceService.save(device, false);

            deviceFuture.thenCompose(savedDevice -> transactionService.recordPurchase(
                    savedDevice.getId(), seller.getId(), panel.getPrice(), panel.getTransactionDate(),
                    panel.getExpertiseNotes(), null)
            ).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, "Alım kaydı oluşturuldu.");
                refreshTable();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    controller.consume();
                    String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    Toast.show(this, Toast.Type.ERROR, "Hata: " + cause);
                });
                Servicio.getLogger().error("2.el alım kaydı oluşturma hatası", ex);
                return null;
            });
        }), MODAL_ID);
    }

    // =========================================================================
    // SATIŞ
    // =========================================================================

    private void openSaleModal(DeviceTransaction purchase) {
        if (purchase.getType() != DeviceTransactionType.PURCHASE) {
            Toast.show(this, Toast.Type.WARNING, "Yalnızca stoktaki (alım) kayıtları satılabilir.");
            return;
        }
        final String MODAL_ID = "secondhand_sale_modal";

        SalePanel[] panelRef = new SalePanel[1];
        panelRef[0] = new SalePanel(purchase.getDevice(), e -> AppModal.pushModalDeferred(() -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            return new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                if (a1 != SimpleModalBorder.YES_OPTION) return;
                Customer newCustomer = newCustomerPanel.getData();
                if (newCustomer == null) { c1.consume(); return; }
                c1.consume();
                newCustomer.setCreatedAt(LocalDateTime.now());
                ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved ->
                        SwingUtilities.invokeLater(() -> {
                            panelRef[0].appendNewCustomer(saved);
                            AppModal.popModal(MODAL_ID);
                        }));
            });
        }, MODAL_ID));
        SalePanel panel = panelRef[0];

        ServiceManager.getCustomerService().getAll().thenAccept(customers ->
                SwingUtilities.invokeLater(() -> panel.setCustomers(customers)));

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Satışı Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "İkinci El Satış", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) { panel.requestInitialFocus(); return; }
            if (action != SimpleModalBorder.OK_OPTION) return;

            Customer buyer = panel.getBuyer();
            if (buyer == null) { controller.consume(); Toast.show(this, Toast.Type.WARNING, "Lütfen alıcı müşteri seçin."); return; }

            transactionService.recordSale(purchase.getDeviceId(), buyer.getId(), panel.getPrice(),
                    panel.getTransactionDate(), panel.getWarrantyMonths(), panel.getNote()
            ).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, "Satış kaydedildi.");
                refreshTable();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    controller.consume();
                    String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    Toast.show(this, Toast.Type.ERROR, "Hata: " + cause);
                });
                Servicio.getLogger().error("2.el satış kaydı oluşturma hatası", ex);
                return null;
            });
        }), MODAL_ID);
    }

    // =========================================================================
    // BELGE (PDF) OLUŞTURMA — "Form" popup menüsü
    // =========================================================================

    private void showFormMenu(DeviceTransaction transaction) {
        JPopupMenu popup = new JPopupMenu();
        for (DeviceTransactionFormType type : DeviceTransactionFormType.values()) {
            if (!type.isApplicableTo(transaction)) continue;
            JMenuItem item = new JMenuItem(type.getDisplayName());
            item.addActionListener(e -> {
                if (type.requiresSigners()) {
                    openSignerNamesModal(type, transaction);
                } else {
                    generateAndOpenDocument(type, transaction, null, null);
                }
            });
            popup.add(item);
        }
        popup.show(this, 0, 0);
    }

    private void openSignerNamesModal(DeviceTransactionFormType type, DeviceTransaction transaction) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            User shop = shopOpt.orElse(null);

            JPanel panel = new JPanel(new MigLayout("fillx, wrap, insets 10, width 350", "[fill,grow]", "[][][][]"));
            panel.add(new JLabel(type.getLeftSignerLabel() + ":"));
            JTextField txtLeft = new JTextField(shop != null ? shop.getBusinessName() : "");
            panel.add(txtLeft, "growx");
            panel.add(new JLabel(type.getRightSignerLabel() + ":"));
            JTextField txtRight = new JTextField(transaction.getCustomer() != null ? transaction.getCustomer().getFullName() : "");
            panel.add(txtRight, "growx");

            SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                    new SimpleModalBorder.Option("Oluştur", SimpleModalBorder.YES_OPTION),
                    new SimpleModalBorder.Option("Çık", SimpleModalBorder.CANCEL_OPTION)
            };

            AppModal.showModal(this, new SimpleModalBorder(panel, type.getDisplayName(), options, (controller, action) -> {
                if (action != SimpleModalBorder.YES_OPTION) return;
                generateAndOpenDocument(type, transaction, txtLeft.getText(), txtRight.getText());
            }), "secondhand_generate_document_modal");
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, ex.getMessage()));
            return null;
        });
    }

    private void generateAndOpenDocument(DeviceTransactionFormType type, DeviceTransaction transaction, String leftSignerName, String rightSignerName) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = type.generate(transaction, shop, leftSignerName, rightSignerName);
                SwingUtilities.invokeLater(() -> {
                    if (DesktopHelper.openFile(pdf)) {
                        Toast.show(this, Toast.Type.SUCCESS, "Belge oluşturuldu.");
                    } else {
                        Toast.show(this, Toast.Type.WARNING, "Belge oluşturuldu ama açılamadı: " + pdf.getAbsolutePath());
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Belge oluşturulamadı: " + ex.getMessage()));
                Servicio.getLogger().error("2.el belge oluşturma hatası", ex);
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, ex.getMessage()));
            return null;
        });
    }

    // =========================================================================
    // SİL
    // =========================================================================

    private void deleteTransaction(DeviceTransaction transaction) {
        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Bu kaydı silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {
                transactionService.delete(transaction.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Kayıt silindi.");
                    refreshTable();
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Silinemedi: " + ex.getCause().getMessage()));
                    return null;
                });
            }
        }));
    }
}
