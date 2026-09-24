package tr.cabro.servicio.application.forms;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.panels.edit.EditModals;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Parça detayı: kimlik şeridinde stok, alış/satış fiyatı ve adet başı kâr; altta parçanın
 * kullanıldığı servisler (geniş), sağda stok/tedarikçi/açıklama kartları.
 */
public class FormPart extends Form {

    private Part part;
    private final PartService partService;
    private final WorkOrderService workOrderService;

    private DetailHeader header;
    private final Badge categoryBadge = new Badge(simple("Kategorisiz", BadgeColor.GRAY));
    private final Badge criticalBadge = new Badge(simple("Kritik stok", BadgeColor.RED)).setShowIcon(true);
    private DetailListSection<WorkOrder> usage;

    private JLabel factStock, factMin, factUsage, factRevenue, factUpdated;
    private JLabel supplierName, supplierPhone;
    private JButton supplierLink;
    private JPanel descriptionCard;
    private JTextArea description;

    public FormPart(Part part) {
        this.part = part;
        this.partService = ServiceManager.getPartService();
        this.workOrderService = ServiceManager.getWorkOrderService();
        init();
    }

    private static Visualizable simple(String name, BadgeColor color) {
        return new Visualizable() {
            @Override public String getDisplayName() { return name; }
            @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
            @Override public BadgeColor getBadgeColor() { return color; }
        };
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][340!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addBadge(categoryBadge);
        header.addBadge(criticalBadge);
        header.addStat("stock", "Stok");
        header.addStat("buy", "Alış");
        header.addStat("sell", "Satış");
        header.addStat("margin", "Adet başı kâr");
        header.addAction("Düzenle", "icons/pencil.svg", () -> EditModals.editPart(this, part, this::formRefresh));
        header.setPrimary("Stok Girişi", "icons/package-plus.svg", this::openAddStockDialog);
        add(header, "span 2, growx, wmin 0, wrap");

        usage = new DetailListSection<>("Kullanıldığı servisler", Arrays.asList(
                new ColumnDef<WorkOrder>("No", String.class, s -> "SRV-" + s.getId()).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Müşteri", WorkOrder.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Cihaz", Device.class, WorkOrder::getDevice).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Tarih", String.class, s -> s.getCreatedAt() != null ? s.getCreatedAt().format(DateFormats.dateTime()) : "-")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Adet", Integer.class, this::usedQuantity).alignment(SwingConstants.CENTER),
                ColumnDef.<WorkOrder>badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus)
        ), "Henüz bir serviste kullanılmadı", "Bu parça bir servis kaydına eklendiğinde burada görünür.", false);
        JTable t = usage.getTable();
        t.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<WorkOrder>(
                s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "Müşterisiz",
                s -> s.getCustomer() != null ? PhoneHelper.formatForDisplay(s.getCustomer().getPhoneNumber1()) : ""));
        t.getColumnModel().getColumn(2).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Bilinmeyen cihaz",
                d -> d != null && d.getSerialNo() != null ? "SN " + d.getSerialNo() : ""));
        t.getColumnModel().getColumn(0).setMaxWidth(90);
        t.getColumnModel().getColumn(4).setMaxWidth(70);
        usage.setOnOpen(wo -> FormManager.showForm(new FormWorkOrder(wo)));
        add(usage, "grow, wmin 0, hmin 0");

        add(DetailKit.scroll(buildSideColumn()), "grow, hmin 0");
        refreshData();
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        column.setOpaque(false);

        JPanel stockCard = DetailKit.card("Stok ve kullanım", null);
        JPanel facts = DetailKit.facts();
        factStock = DetailKit.fact(facts, "Mevcut stok");
        factMin = DetailKit.fact(facts, "Minimum seviye");
        factUsage = DetailKit.fact(facts, "Serviste kullanılan");
        factRevenue = DetailKit.fact(facts, "Servislerden gelir");
        factUpdated = DetailKit.fact(facts, "Son güncelleme");
        stockCard.add(facts);
        column.add(stockCard);

        supplierLink = DetailKit.link("Tedarikçiye git", () -> {
            if (part.getSupplier() != null) FormManager.showForm(new FormSupplier(part.getSupplier()));
        });
        JPanel supplierCard = DetailKit.card("Tedarikçi", supplierLink);
        supplierName = new JLabel("—");
        supplierName.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font: bold");
        supplierPhone = DetailKit.muted(" ");
        supplierCard.add(supplierName);
        supplierCard.add(supplierPhone);
        column.add(supplierCard);

        descriptionCard = DetailKit.card("Açıklama", null);
        description = new JTextArea();
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setBorder(null);
        descriptionCard.add(description, "wmin 0");
        column.add(descriptionCard);
        return column;
    }

    @Override
    public void formRefresh() {
        partService.getById(part.getId()).thenAccept(updated ->
                updated.ifPresent(p -> {
                    this.part = p;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    private boolean isCritical() {
        return part.getStockQuantity() != null && part.getMinStockLevel() != null
                && part.getStockQuantity() < part.getMinStockLevel();
    }

    private void refreshData() {
        int stock = part.getStockQuantity() != null ? part.getStockQuantity() : 0;
        header.setTitle(part.getName());
        String category = part.getCategory() != null ? part.getCategory().getName() : null;
        categoryBadge.setVisualizable(simple(category != null ? category : "Kategorisiz", BadgeColor.GRAY));
        criticalBadge.setVisible(isCritical() || stock <= 0);
        criticalBadge.setVisualizable(simple(stock <= 0 ? "Tükendi" : "Kritik stok", BadgeColor.RED));

        Supplier sup = part.getSupplier();
        String supName = sup == null ? null
                : (sup.getBusinessName() != null && !sup.getBusinessName().isBlank() ? sup.getBusinessName() : sup.getName());
        header.setMeta("SKU " + part.getBarcode(),
                part.getModelCompatibility() != null && !part.getModelCompatibility().isBlank() ? "Uyumlu: " + part.getModelCompatibility() : null,
                supName);

        header.setStat("stock", stock <= 0 ? "Tükendi" : stock + " adet", isCritical() || stock <= 0 ? "Servicio.dangerColor" : null);
        header.setStat("buy", price(part.getPurchasePrice()), null);
        header.setStatTooltip("buy", "USD".equals(part.getPurchaseCurrency()) || "EUR".equals(part.getPurchaseCurrency())
                ? "Döviz alış: " + part.getPurchasePriceOriginal() + " " + part.getPurchaseCurrency() : null);
        header.setStat("sell", price(part.getSalePrice()), null);
        BigDecimal margin = part.getSalePrice() != null && part.getPurchasePrice() != null
                ? part.getSalePrice().subtract(part.getPurchasePrice()) : null;
        header.setStat("margin", margin != null ? Format.formatPrice(margin) : "—",
                margin == null ? null : margin.signum() > 0 ? "Servicio.successColor" : margin.signum() < 0 ? "Servicio.dangerColor" : null);

        DetailKit.setFact(factStock, stock + " adet");
        DetailKit.setFact(factMin, part.getMinStockLevel() != null ? part.getMinStockLevel() + " adet" : null);
        DetailKit.setFact(factUpdated, part.getUpdatedAt() != null ? part.getUpdatedAt().format(DateFormats.dateTime()) : null);

        supplierName.setText(supName != null ? supName : "Tedarikçi seçilmemiş");
        supplierPhone.setText(sup != null && sup.getPhone() != null ? Format.formatPhoneNumber(sup.getPhone()) : " ");
        supplierLink.setVisible(sup != null);

        boolean hasDesc = part.getDescription() != null && !part.getDescription().isBlank();
        descriptionCard.setVisible(hasDesc);
        description.setText(hasDesc ? part.getDescription().trim() : "");

        usage.showLoading();
        workOrderService.getAllByPart(part.getId()).thenAccept(workOrders -> SwingUtilities.invokeLater(() -> {
            usage.setData(workOrders);
            int used = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            for (WorkOrder wo : workOrders) {
                if (wo.getItems() == null) continue;
                for (WorkOrderItem item : wo.getItems()) {
                    if (!part.getId().equals(item.getPartId())) continue;
                    used += item.getQuantity();
                    if (item.getTotalPrice() != null) revenue = revenue.add(item.getTotalPrice());
                }
            }
            DetailKit.setFact(factUsage, used + " adet, " + workOrders.size() + " serviste");
            DetailKit.setFact(factRevenue, Format.formatPrice(revenue));
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> usage.showError("Servis geçmişi yüklenemedi."));
            return ErrorHandler.handle(this, "Parça kullanım geçmişi yüklenemedi", ex);
        });
    }

    private int usedQuantity(WorkOrder wo) {
        int used = 0;
        if (wo.getItems() != null) {
            for (WorkOrderItem item : wo.getItems()) if (part.getId().equals(item.getPartId())) used += item.getQuantity();
        }
        return used;
    }

    private static String price(BigDecimal v) {
        return v != null ? Format.formatPrice(v) : "—";
    }

    private void openAddStockDialog() {
        DialogHelper.prompt(this, "stock.add.title", "stock.add.label", "", input -> {
            if (input == null || input.trim().isEmpty()) return;

            int amount;
            try {
                amount = Integer.parseInt(input.trim());
            } catch (NumberFormatException ex) {
                Toast.show(this, Toast.Type.ERROR, Messages.get("toast.stock.invalidNumber"));
                return;
            }

            if (amount <= 0) {
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.stock.mustBePositive"));
                return;
            }

            final int finalAmount = amount;
            partService.addStock(part.getId(), finalAmount, part.getWarehouseId())
                    .thenCompose(v -> partService.getById(part.getId()))
                    .thenAccept(opt -> SwingUtilities.invokeLater(() -> {
                        opt.ifPresent(updated -> {
                            this.part = updated;
                            refreshData();
                        });
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.stock.added", finalAmount));
                    }))
                    .exceptionally(ex -> ErrorHandler.handle(this, "Stok eklenemedi", ex));
        });
    }
}
