package tr.cabro.servicio.application.forms;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.panels.edit.DeviceEditPanel;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.DeviceTransactionType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.DeviceService;
import tr.cabro.servicio.service.DeviceTransactionService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cihaz detayı: kimlik şeridinde cihazın şu anki durumu (atölyede / 2.el stokta / satıldı),
 * servis sayısı ve servis tutarı; altta servis ve 2.el alım-satım hareketlerinin birleşik geçmişi
 * (Tümü / Servis / Alım-satım), sağda cihaz bilgileri ve son sahibi.
 */
public class FormDevice extends Form {

    private static final String VIEW_ALL = "all";
    private static final String VIEW_SERVICE = "service";
    private static final String VIEW_TRADE = "trade";

    private Device device;
    private final DeviceService deviceService;
    private final WorkOrderService workOrderService;
    private final DeviceTransactionService deviceTransactionService;

    private DetailHeader header;
    private final Badge typeBadge = new Badge(simple("Cihaz", BadgeColor.GRAY));
    private final Badge stateBadge = new Badge(simple("-", BadgeColor.BLUE));
    private DetailListSection<HistoryEntry> history;
    private ViewTabs views;
    private List<HistoryEntry> entries = Collections.emptyList();
    private Customer lastOwner;

    private JLabel factType, factBrand, factModel, factSerial, factAccessory, factCreated;
    private JLabel ownerName, ownerPhone;
    private JButton ownerLink;

    public FormDevice(Device device) {
        this.device = device;
        this.deviceService = ServiceManager.getDeviceService();
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.deviceTransactionService = ServiceManager.getDeviceTransactionService();
        init();
    }

    /** Servis kaydı ya da 2.el hareketinin birleşik geçmiş satırı. */
    private static class HistoryEntry {
        enum Kind { SERVICE, PURCHASE, SALE }

        final Kind kind;
        final LocalDateTime date;
        final Customer customer;
        final WorkOrder workOrder;          // yalnızca SERVICE
        final DeviceTransaction transaction; // yalnızca PURCHASE/SALE

        HistoryEntry(Kind kind, LocalDateTime date, Customer customer, WorkOrder workOrder, DeviceTransaction transaction) {
            this.kind = kind;
            this.date = date;
            this.customer = customer;
            this.workOrder = workOrder;
            this.transaction = transaction;
        }

        String title() {
            switch (kind) {
                case SERVICE: return "SRV-" + workOrder.getId() + " servis";
                case PURCHASE: return "2.el alım";
                default: return "2.el satış";
            }
        }

        String subtitle() {
            if (kind == Kind.SERVICE) {
                String fault = workOrder.getReportedFault();
                return fault != null && !fault.isBlank() ? fault : "Şikâyet girilmemiş";
            }
            if (kind == Kind.SALE && transaction.getWarrantyMonths() != null && transaction.getWarrantyMonths() > 0) {
                return transaction.getWarrantyMonths() + " ay garanti";
            }
            return transaction.getExpertiseNotes() != null && !transaction.getExpertiseNotes().isBlank()
                    ? transaction.getExpertiseNotes() : "";
        }

        BigDecimal amount() {
            if (kind == Kind.SERVICE) return workOrder.getTotalServiceAmount();
            return transaction.getPrice();
        }
    }

    private static Visualizable simple(String name, BadgeColor color) {
        return new Visualizable() {
            @Override public String getDisplayName() { return name; }
            @Override public String getIconPath() { return "icons/tablet-smartphone.svg"; }
            @Override public BadgeColor getBadgeColor() { return color; }
        };
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][340!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addBadge(typeBadge);
        header.addBadge(stateBadge);
        header.addStat("services", "Servis");
        header.addStat("serviceTotal", "Servis tutarı");
        header.addStat("last", "Son işlem");
        header.addAction("Düzenle", "icons/pencil.svg", () -> DeviceEditPanel.open(this, device, this::formRefresh));
        header.setPrimary("Yeni Servis", "icons/wrench.svg", () -> {
            FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
            FormManager.showForm(form);
            form.startNewFor(lastOwner, device);
        }, tr.cabro.servicio.application.system.QuickAction.NEW_SERVICE);
        add(header, "span 2, growx, wmin 0, wrap");

        history = new DetailListSection<>("Cihaz geçmişi", Arrays.asList(
                new ColumnDef<HistoryEntry>("İşlem", HistoryEntry.class, h -> h).alignment(SwingConstants.LEADING),
                new ColumnDef<HistoryEntry>("Kişi", HistoryEntry.class, h -> h).alignment(SwingConstants.LEADING),
                new ColumnDef<HistoryEntry>("Tarih", String.class, h -> h.date != null ? h.date.format(DateFormats.shortDate()) : "-")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<HistoryEntry>("Tutar", BigDecimal.class, HistoryEntry::amount).alignment(SwingConstants.TRAILING),
                ColumnDef.<HistoryEntry>badge("Durum", ServiceStatus.class,
                        h -> h.kind == HistoryEntry.Kind.SERVICE ? h.workOrder.getServiceStatus() : null)
        ), "Henüz geçmiş yok", "Bu cihaz servise geldiğinde ya da 2.el alınıp satıldığında burada görünür.", false);
        views = new ViewTabs();
        views.addView(VIEW_ALL, "Tümü");
        views.addView(VIEW_SERVICE, "Servis");
        views.addView(VIEW_TRADE, "Alım-satım");
        views.setOnChange(k -> applyView());
        history.addHeaderAction(views);

        JTable t = history.getTable();
        t.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<HistoryEntry>(
                HistoryEntry::title, HistoryEntry::subtitle));
        t.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<HistoryEntry>(
                h -> h.customer != null ? h.customer.getFullName() : "—",
                h -> h.customer != null ? PhoneHelper.formatForDisplay(h.customer.getPhoneNumber1()) : ""));
        t.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        t.getColumnModel().getColumn(0).setPreferredWidth(300);
        t.getColumnModel().getColumn(1).setPreferredWidth(200);
        t.getColumnModel().getColumn(1).setMinWidth(150);
        t.getColumnModel().getColumn(2).setMinWidth(100);
        t.getColumnModel().getColumn(3).setMinWidth(110);
        t.getColumnModel().getColumn(4).setMinWidth(130);
        history.setOnOpen(h -> {
            if (h.kind == HistoryEntry.Kind.SERVICE) FormManager.showForm(new FormWorkOrder(h.workOrder));
            else if (h.customer != null) FormManager.showForm(new FormCustomer(h.customer));
        });
        add(history, "grow, wmin 0, hmin 0");

        add(DetailKit.scroll(buildSideColumn()), "grow, hmin 0");
        refreshData();
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        column.setOpaque(false);

        JPanel info = DetailKit.card("Cihaz bilgileri", null);
        JPanel facts = DetailKit.facts();
        factType = DetailKit.fact(facts, "Tür");
        factBrand = DetailKit.fact(facts, "Marka");
        factModel = DetailKit.fact(facts, "Model");
        factSerial = DetailKit.fact(facts, "Seri no / IMEI");
        factAccessory = DetailKit.fact(facts, "Aksesuar");
        factCreated = DetailKit.fact(facts, "Kayıt tarihi");
        info.add(facts);
        column.add(info);

        ownerLink = DetailKit.link("Müşteriye git", () -> {
            if (lastOwner != null) FormManager.showForm(new FormCustomer(lastOwner));
        });
        JPanel owner = DetailKit.card("Son sahibi", ownerLink);
        ownerName = new JLabel("—");
        ownerName.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font: bold");
        ownerPhone = DetailKit.muted(" ");
        owner.add(ownerName);
        owner.add(ownerPhone);
        column.add(owner);
        return column;
    }

    @Override
    public void formRefresh() {
        deviceService.get(device.getId()).thenAccept(updated ->
                updated.ifPresent(d -> {
                    this.device = d;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    private void refreshData() {
        header.setTitle(device.getDisplayName());
        typeBadge.setVisualizable(simple(device.getDeviceType() != null ? device.getDeviceType().getName() : "Türü belirsiz", BadgeColor.GRAY));
        header.setMeta(device.getSerialNo() != null && !device.getSerialNo().isBlank() ? "SN " + device.getSerialNo() : "Seri no girilmemiş",
                device.getAccessory() != null && !device.getAccessory().isBlank() ? "Aksesuar: " + device.getAccessory() : null,
                device.getCreatedAt() != null ? "Kayıt " + device.getCreatedAt().format(DateFormats.shortDate()) : null);

        DetailKit.setFact(factType, device.getDeviceType() != null ? device.getDeviceType().getName() : null);
        DetailKit.setFact(factBrand, device.getBrand() != null ? device.getBrand().getName() : null);
        DetailKit.setFact(factModel, device.getModel());
        DetailKit.setFact(factSerial, device.getSerialNo());
        DetailKit.setFact(factAccessory, device.getAccessory());
        DetailKit.setFact(factCreated, device.getCreatedAt() != null ? device.getCreatedAt().format(DateFormats.dateTime()) : null);

        history.showLoading();
        workOrderService.getAllByDevice(device.getId()).thenCombine(deviceTransactionService.getHistoryByDeviceId(device.getId()),
                (workOrders, transactions) -> {
                    List<HistoryEntry> list = new ArrayList<>();
                    for (WorkOrder wo : workOrders) {
                        list.add(new HistoryEntry(HistoryEntry.Kind.SERVICE, wo.getCreatedAt(), wo.getCustomer(), wo, null));
                    }
                    for (DeviceTransaction tr : transactions) {
                        HistoryEntry.Kind kind = tr.getType() == DeviceTransactionType.PURCHASE
                                ? HistoryEntry.Kind.PURCHASE : HistoryEntry.Kind.SALE;
                        list.add(new HistoryEntry(kind, tr.getTransactionDate(), tr.getCustomer(), null, tr));
                    }
                    list.sort(Comparator.comparing((HistoryEntry h) -> h.date, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                    SwingUtilities.invokeLater(() -> applyHistory(list, workOrders));
                    return null;
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> history.showError("Cihaz geçmişi yüklenemedi."));
                    return ErrorHandler.handle(this, "Cihaz geçmişi yüklenemedi", ex);
                });
    }

    private void applyHistory(List<HistoryEntry> list, List<WorkOrder> workOrders) {
        entries = list;
        views.setCount(VIEW_ALL, (long) list.size());
        views.setCount(VIEW_SERVICE, (long) workOrders.size());
        views.setCount(VIEW_TRADE, (long) (list.size() - workOrders.size()));
        applyView();

        BigDecimal serviceTotal = workOrders.stream().map(WorkOrder::getTotalServiceAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        header.setStat("services", String.valueOf(workOrders.size()), null);
        header.setStat("serviceTotal", tr.cabro.servicio.util.Format.formatPrice(serviceTotal), null);
        header.setStat("last", list.isEmpty() || list.get(0).date == null ? "—" : list.get(0).date.format(DateFormats.shortDate()), null);

        // Şu anki durum: açık servis > son 2.el hareketi.
        boolean inShop = workOrders.stream().anyMatch(w -> w.getServiceStatus() != ServiceStatus.DELIVERED
                && w.getServiceStatus() != ServiceStatus.RETURN);
        HistoryEntry lastTrade = list.stream().filter(h -> h.kind != HistoryEntry.Kind.SERVICE).findFirst().orElse(null);
        if (inShop) stateBadge.setVisualizable(simple("Atölyede", BadgeColor.BLUE));
        else if (lastTrade != null && lastTrade.kind == HistoryEntry.Kind.PURCHASE) stateBadge.setVisualizable(simple("2.el stokta", BadgeColor.PURPLE));
        else if (lastTrade != null) stateBadge.setVisualizable(simple("2.el satıldı", BadgeColor.GREEN));
        stateBadge.setVisible(inShop || lastTrade != null);

        // Son sahibi: en yeni hareketin kişisi (2.el alımda cihaz artık dükkânda, satıcı eski sahip).
        HistoryEntry latest = list.stream().filter(h -> h.customer != null).findFirst().orElse(null);
        lastOwner = latest != null && latest.kind != HistoryEntry.Kind.PURCHASE ? latest.customer : null;
        ownerName.setText(lastOwner != null ? lastOwner.getFullName()
                : (latest != null ? "Dükkânda (2.el stok)" : "Bilinmiyor"));
        ownerPhone.setText(lastOwner != null ? PhoneHelper.formatForDisplay(lastOwner.getPhoneNumber1()) : " ");
        ownerLink.setVisible(lastOwner != null);
    }

    private void applyView() {
        String key = views.getSelected();
        List<HistoryEntry> rows = VIEW_SERVICE.equals(key)
                ? entries.stream().filter(h -> h.kind == HistoryEntry.Kind.SERVICE).collect(Collectors.toList())
                : VIEW_TRADE.equals(key)
                ? entries.stream().filter(h -> h.kind != HistoryEntry.Kind.SERVICE).collect(Collectors.toList())
                : entries;
        history.setData(rows);
    }
}
