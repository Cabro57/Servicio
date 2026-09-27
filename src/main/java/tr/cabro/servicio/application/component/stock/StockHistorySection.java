package tr.cabro.servicio.application.component.stock;

import raven.modal.Toast;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.forms.FormSale;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.StockMovement;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Kalemin stok defteri: her satır bir hareket — ne oldu (kaynak + not), hangi depoda, kaç adet,
 * sonra ne kaldı. Kaynağı servis ya da satış olan satır tıklanınca o kaydı açar.
 */
public final class StockHistorySection extends DetailListSection<StockMovement> {

    /** Tek seferde okunan hareket sayısı; daha eskisi için sayfalama gerekmedi (tek dükkân hacmi). */
    private static final int LIMIT = 300;

    private final StockItemKind kind;
    private Consumer<Long> onCount;

    public StockHistorySection(String title, StockItemKind kind) {
        super(title, Arrays.asList(
                new ColumnDef<StockMovement>("Tarih", String.class,
                        m -> m.getCreatedAt() != null ? m.getCreatedAt().format(DateFormats.dateTime()) : "—")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<StockMovement>("İşlem", StockMovement.class, m -> m).alignment(SwingConstants.LEADING),
                new ColumnDef<StockMovement>("Depo", String.class, StockMovement::getWarehouseName).alignment(SwingConstants.LEADING),
                new ColumnDef<StockMovement>("Miktar", StockMovement.class, m -> m).alignment(SwingConstants.TRAILING),
                new ColumnDef<StockMovement>("Kalan", StockMovement.class, m -> m).alignment(SwingConstants.TRAILING)
        ), "Henüz stok hareketi yok", "Giriş, satış, servis, sayım ve transferler burada sırayla görünür.", false);
        this.kind = kind;

        JTable t = getTable();
        t.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<StockMovement>(
                StockHistorySection::action, m -> m.getNote() != null ? m.getNote() : ""));
        t.getColumnModel().getColumn(3).setCellRenderer(new MultiLineTableCellRenderer<StockMovement>(
                m -> (m.getQuantity() > 0 ? "+" : "−") + Math.abs(m.getQuantity()),
                m -> m.getUnitCost() != null ? Format.formatPrice(m.getUnitCost()) + " / adet" : "",
                m -> m.getQuantity() > 0 ? UIManager.getColor("Servicio.successColor") : null,
                m -> null).trailing());
        t.getColumnModel().getColumn(4).setCellRenderer(new MultiLineTableCellRenderer<StockMovement>(
                m -> m.getBalanceAfter() + " adet",
                m -> "depoda " + m.getWarehouseBalanceAfter()).trailing());
        t.getColumnModel().getColumn(0).setPreferredWidth(130);
        t.getColumnModel().getColumn(0).setMaxWidth(150);
        t.getColumnModel().getColumn(1).setPreferredWidth(260);
        t.getColumnModel().getColumn(3).setMaxWidth(120);
        t.getColumnModel().getColumn(2).setPreferredWidth(140);
        t.getColumnModel().getColumn(4).setPreferredWidth(120);
        t.getColumnModel().getColumn(4).setMinWidth(110);
        t.getColumnModel().getColumn(4).setMaxWidth(150);
        setOnOpen(this::openReference);
    }

    /** Toplam hareket sayısı okununca çağrılır (sekme sayacı için). */
    public void setOnCount(Consumer<Long> onCount) {
        this.onCount = onCount;
    }

    public void load(Long itemId) {
        showLoading();
        ServiceManager.getStockService().getHistory(kind, itemId, 1, LIMIT)
                .thenAccept((PageResult<StockMovement> page) -> SwingUtilities.invokeLater(() -> {
                    setData(page.getItems());
                    if (onCount != null) onCount.accept(page.getTotalItems());
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> showError("Stok geçmişi yüklenemedi."));
                    return ErrorHandler.handle(this, "Stok geçmişi yüklenemedi", ex);
                });
    }

    /** "Servis · SRV-18", "Satış · SAT-5", "Depo Transferi" … */
    private static String action(StockMovement m) {
        ReferenceType type = m.getReferenceType();
        if (type == null) return "—";
        String ref = reference(m);
        return ref != null ? type.getDisplayName() + "  ·  " + ref : type.getDisplayName();
    }

    private static String reference(StockMovement m) {
        if (m.getReferenceId() == null) return null;
        return switch (m.getReferenceType()) {
            case WORK_ORDER, WORK_ORDER_CANCEL, LOSS -> "SRV-" + m.getReferenceId();
            case SALE -> "SAT-" + m.getReferenceId();
            case RETURN -> "İADE-" + m.getReferenceId();
            default -> null;
        };
    }

    private void openReference(StockMovement m) {
        if (m.getReferenceId() == null || m.getReferenceType() == null) return;
        switch (m.getReferenceType()) {
            case WORK_ORDER, WORK_ORDER_CANCEL, LOSS -> ServiceManager.getWorkOrderService().get(m.getReferenceId())
                    .thenAccept(opt -> SwingUtilities.invokeLater(() -> {
                        if (opt.isPresent()) FormManager.showForm(new FormWorkOrder(opt.get()));
                        else Toasts.show(this, Toast.Type.INFO, "SRV-" + m.getReferenceId() + " silinmiş; hareket geçmişte kalır.");
                    }))
                    .exceptionally(ex -> ErrorHandler.handle(this, "Servis kaydı açılamadı", ex));
            case SALE, RETURN -> ServiceManager.getSaleService().getById(m.getReferenceId())
                    .thenAccept(opt -> SwingUtilities.invokeLater(() -> opt.ifPresent(s -> FormManager.showForm(new FormSale(s)))))
                    .exceptionally(ex -> ErrorHandler.handle(this, "Satış fişi açılamadı", ex));
            default -> { }
        }
    }
}
