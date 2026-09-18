package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.AppPagination;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Satış geçmişi — tek atomik yazma olan {@link SaleService#checkout} dışında bu ekrandan kayıt oluşturulmaz. */
@SystemForm(name = "Satışlar", description = "Tamamlanmış satışların listesi ve detayları")
public class FormSales extends AbstractTableForm {

    private final SaleService saleService;
    private GenericTableModel<Sale> tableModel;

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = 25;
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private JPagination pagination;

    public FormSales() {
        this.saleService = ServiceManager.getSaleService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Satış (POS)";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/credit-card.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Satış Geçmişi";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Fiş no veya müşteri ara...";
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
        List<ColumnDef<Sale>> columns = Arrays.asList(
                new ColumnDef<>("Fiş No", String.class, s -> s.getType() == tr.cabro.servicio.model.enums.SaleType.RETURN
                        ? "İADE-" + s.getId() + " (SAT-" + s.getParentSaleId() + ")"
                        : "SAT-" + s.getId()),
                new ColumnDef<>("Tarih", String.class, s -> s.getSaleDate() != null ? s.getSaleDate().format(DateFormats.dateTime()) : "-"),
                new ColumnDef<>("Müşteri", String.class, s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "Perakende"),
                new ColumnDef<>("Toplam", BigDecimal.class, Sale::getTotalAmount),
                ColumnDef.badge("Durum", PaymentStatus.class,
                        s -> PaymentService.resolveStatus(s.getTotalAmount(), s.getTotalPaid())),
                new ColumnDef<Sale>("", String.class, s -> "").editable(true)
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        table.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());

        DynamicActionColumnSupport.install(table, 5, tableModel, List.of(
                DynamicActionColumnSupport.button("icons/eye.svg", new Color(13, 110, 253), "Detay",
                        sale -> FormManager.showForm(new FormSale(sale)))
        ));
        table.getColumnModel().getColumn(5).setMaxWidth(60);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz satış yok"; }

    @Override
    protected String getEmptyStateDescription() { return "POS ekranından tamamlanan satışlar burada listelenir."; }

    @Override
    protected void loadTableData() {
        CompletableFuture<PageResult<Sale>> future = currentSearchTerm.isEmpty()
                ? saleService.getAllPaged(currentPage, pageSize)
                : saleService.searchPaged(currentSearchTerm, currentPage, pageSize);

        future.thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (pagination != null) pagination.setPageRange(result.getPage(), result.getTotalPages());
                refreshLayout();
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::resetKeyboardActions);
            return ErrorHandler.handle(this, "Satış tablosu yenilenemedi", ex);
        });
    }

    @Override
    protected void onNew() {
        FormManager.showForm(tr.cabro.servicio.application.system.AllForms.getForm(FormPos.class));
    }
}
