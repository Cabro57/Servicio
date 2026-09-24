package tr.cabro.servicio.application.forms;

import java.util.ArrayList;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.StatusDotCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.database.repository.DeviceTransactionRepository;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.util.PhoneHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.secondhand.PurchasePanel;
import tr.cabro.servicio.application.panels.secondhand.SalePanel;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.DocumentExportModal;
import tr.cabro.servicio.application.system.NewCustomerModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
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
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
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
        return "Cihaz Al";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/handshake.svg";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Cihaz, seri no, marka veya kişi ara…";
    }

    // --- Görünüm sekmeleri: ilk sekme (Stokta) varsayılandır ---

    private static final String VIEW_STOCK = "stock";
    private static final String VIEW_PURCHASES = "purchases";
    private static final String VIEW_SALES = "sales";
    private static final String VIEW_ALL = "all";

    @Override
    protected void initViews() {
        addView(VIEW_STOCK, "Stokta");
        addView(VIEW_PURCHASES, "Alımlar");
        addView(VIEW_SALES, "Satımlar");
        addView(VIEW_ALL, "Tümü");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_STOCK.equals(key)) return Map.of("view:stock", ColumnFilterValue.condition(
                "tr.type = 'PURCHASE' AND " + DeviceTransactionRepository.LATEST_PER_DEVICE));
        if (VIEW_PURCHASES.equals(key)) return Map.of("tr.type", ColumnFilterValue.enumOf(DeviceTransactionType.PURCHASE.name()));
        if (VIEW_SALES.equals(key)) return Map.of("tr.type", ColumnFilterValue.enumOf(DeviceTransactionType.SALE.name()));
        return Collections.emptyMap();
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return transactionService.searchFilteredPaged(currentSearchTerm, filters, false, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz 2.el işlemi yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Cihaz alım/satımı kaydettikçe stok burada birikir."; }

    /** Özet: stoktaki cihaz sayısı, stoğa bağlanan para ve en uzun bekleyen cihaz. */
    @Override
    protected void refreshStats() {
        transactionService.getCurrentStock().thenAccept(stock -> SwingUtilities.invokeLater(() -> {
            BigDecimal value = stock.stream().map(t -> t.getPrice() != null ? t.getPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long oldest = stock.stream().filter(t -> t.getTransactionDate() != null)
                    .mapToLong(t -> ChronoUnit.DAYS.between(t.getTransactionDate().toLocalDate(), LocalDate.now()))
                    .max().orElse(0);
            summary.set(
                    stock.isEmpty() ? ListSummary.Part.of("Stokta cihaz yok") : ListSummary.Part.strong(stock.size() + " cihaz stokta"),
                    stock.isEmpty() ? null : ListSummary.Part.of("alış maliyeti " + Format.formatPrice(value)),
                    oldest >= 30 ? ListSummary.Part.meaning("en eskisi " + oldest + " gündür bekliyor", "Servicio.warningColor")
                            : (stock.isEmpty() ? null : ListSummary.Part.of("en eskisi " + oldest + " gündür stokta")));
        })).exceptionally(ex -> {
            Servicio.getLogger().error("2.el stok özeti alınamadı", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<DeviceTransaction>> columns = Arrays.asList(
                new ColumnDef<DeviceTransaction>("Cihaz", DeviceTransaction.class, t -> t).alignment(SwingConstants.LEADING),
                new ColumnDef<DeviceTransaction>("İşlem", DeviceTransaction.class, t -> t).alignment(SwingConstants.CENTER),
                new ColumnDef<DeviceTransaction>("Karşı Taraf", DeviceTransaction.class, t -> t).alignment(SwingConstants.LEADING)
                        .lookupFilter("tr.customer_id", Lookups.customers()),
                new ColumnDef<DeviceTransaction>("Tarih", DeviceTransaction.class, t -> t)
                        .alignment(SwingConstants.LEADING).dateRangeFilter("transaction_date"),
                new ColumnDef<DeviceTransaction>("Fiyat", BigDecimal.class, DeviceTransaction::getPrice).alignment(SwingConstants.TRAILING),
                ColumnDef.<DeviceTransaction>actionColumn("")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);

        // Cihaz: işlem türünün rengiyle nokta (alım mavi, satım yeşil); altında seri no.
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusDotCellRenderer<DeviceTransaction>(
                t -> t.getDevice() != null ? t.getDevice().getDisplayName() : "Bilinmeyen cihaz",
                t -> t.getDevice() != null && t.getDevice().getSerialNo() != null ? "SN " + t.getDevice().getSerialNo() : "Seri no yok",
                t -> t.getType() == DeviceTransactionType.PURCHASE ? BadgeColor.BLUE : BadgeColor.GREEN));
        table.getColumnModel().getColumn(1).setCellRenderer(new ChipCellRenderer<DeviceTransaction>(t ->
                t.getType() == DeviceTransactionType.PURCHASE ? ChipCellRenderer.badge("Alım", BadgeColor.BLUE)
                        : ChipCellRenderer.badge("Satım", BadgeColor.GREEN)));
        table.getColumnModel().getColumn(2).setCellRenderer(new MultiLineTableCellRenderer<DeviceTransaction>(
                t -> t.getCustomer() != null ? t.getCustomer().getFullName() : "-",
                t -> t.getCustomer() != null ? PhoneHelper.formatForDisplay(t.getCustomer().getPhoneNumber1()) : ""));
        table.getColumnModel().getColumn(3).setCellRenderer(new MultiLineTableCellRenderer<DeviceTransaction>(
                t -> Format.formatDate(t.getTransactionDate()),
                t -> {
                    if (t.getTransactionDate() == null) return "";
                    long days = ChronoUnit.DAYS.between(t.getTransactionDate().toLocalDate(), LocalDate.now());
                    String ago = days <= 0 ? "bugün" : days + " gün önce";
                    return t.getType() == DeviceTransactionType.PURCHASE ? "alındı, " + ago : "satıldı, " + ago;
                }));
        table.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(0).setMinWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(170);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        configureActionColumn();
        headerFilters = installHeaderFilters(columns);

        addSort("NEWEST", "En yeni");
        addSort("OLDEST", "En eski");
        addSort("PRICE", "Fiyat (yüksekten)");
        addSort("NAME", "Cihaz adı (A-Z)");

        // Satır, cihazın sayfasını (2.el geçmişi dahil) açar.
        openRowsWith(tableModel, t -> {
            if (t.getDevice() != null) FormManager.showForm(new FormDevice(t.getDevice()));
        }, 5);

    }

    private void configureActionColumn() {
        int col = table.getColumnModel().getColumnCount() - 1;
        DynamicActionColumnSupport.install(table, col, tableModel, Arrays.asList(
                DynamicActionColumnSupport.button("icons/file-text.svg", SemanticColor.info(), "Belge yazdır", this::showFormMenu),
                DynamicActionColumnSupport.button("icons/tag.svg", SemanticColor.success(), "Sat", this::openSaleModal),
                DynamicActionColumnSupport.button("icons/trash-2.svg", SemanticColor.danger(), "Sil", this::deleteTransaction)
        ));
        table.getColumnModel().getColumn(col).setMaxWidth(130);
        table.getColumnModel().getColumn(col).setMinWidth(110);
    }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void loadTableData() {
        transactionService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), false, currentPage, pageSize, getSortKey()).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                setResultCount(result.getTotalItems());
                refreshLayout();
            });
        }).exceptionally(e -> {
            Servicio.getLogger().error("2.el alım-satım listesi alınamadı: ", e);
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, Messages.get("toast.list.loadFailed")));
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
        panelRef[0] = new PurchasePanel(e -> NewCustomerModal.push(MODAL_ID, c -> panelRef[0].appendNewCustomer(c)));
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

            if (!panel.validateForm()) { controller.consume(); return; }
            Customer seller = panel.getSeller();
            Device device = panel.getDevice();

            java.util.concurrent.CompletableFuture<Device> deviceFuture = device.getId() != null
                    ? java.util.concurrent.CompletableFuture.completedFuture(device)
                    : deviceService.save(device, false);

            deviceFuture.thenCompose(savedDevice -> transactionService.recordPurchase(
                    savedDevice.getId(), seller.getId(), panel.getPrice(), panel.getTransactionDate(),
                    panel.getExpertiseNotes(), null)
            ).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.secondhand.purchaseCreated"));
                refreshTable();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "2.el alım kaydı oluşturulamadı", ex);
            });
        }), MODAL_ID);
    }

    // =========================================================================
    // SATIŞ
    // =========================================================================

    private void openSaleModal(DeviceTransaction purchase) {
        if (purchase.getType() != DeviceTransactionType.PURCHASE) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.secondhand.onlyPurchaseCanSell"));
            return;
        }
        final String MODAL_ID = "secondhand_sale_modal";

        SalePanel[] panelRef = new SalePanel[1];
        panelRef[0] = new SalePanel(purchase, e -> NewCustomerModal.push(MODAL_ID, c -> panelRef[0].appendNewCustomer(c)));
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

            if (!panel.validateForm()) { controller.consume(); return; }
            Customer buyer = panel.getBuyer();

            transactionService.recordSale(purchase.getDeviceId(), buyer.getId(), panel.getPrice(),
                    panel.getTransactionDate(), panel.getWarrantyMonths(), panel.getNote()
            ).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.secondhand.saleRecorded"));
                refreshTable();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "2.el satış kaydı oluşturulamadı", ex);
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
            item.addActionListener(e -> openDocumentModal(type, transaction));
            popup.add(item);
        }
        popup.show(this, 0, 0);
    }

    /** İmza isimleri ve metinler düzenlenip belge açılır ya da farklı kaydedilir. */
    private void openDocumentModal(DeviceTransactionFormType type, DeviceTransaction transaction) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            User shop = shopOpt.orElse(null);
            String customerName = transaction.getCustomer() != null ? transaction.getCustomer().getFullName() : "";
            DocumentExportModal.Spec spec = new DocumentExportModal.Spec(
                    type.getDisplayName(), type.getFileSlug() + "-DT" + transaction.getId(),
                    type.getLeftSignerLabel(), shop != null ? shop.getBusinessName() : "",
                    type.getRightSignerLabel(), customerName,
                    false, type.getEditableTexts(), type.getSupportedFormats());
            DocumentExportModal.show(this, spec,
                    (request, format, outFile) -> type.generate(transaction, shop, request, format, outFile));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Belge penceresi açılamadı", ex));
    }

    // =========================================================================
    // SİL
    // =========================================================================

    private void deleteTransaction(DeviceTransaction transaction) {
        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                Messages.get("confirm.delete.generic"), Messages.get("confirm.delete.title"),
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {
                transactionService.delete(transaction.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.record.deletedShort"));
                    refreshTable();
                })).exceptionally(ex -> ErrorHandler.handle(this, "2.el kaydı silinemedi", ex));
            }
        }));
    }
}
