package tr.cabro.servicio.application.panels.dashboard;

import net.miginfocom.swing.MigLayout;
import raven.swingpack.JPagination;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.table.AppPagination;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ServiceBadges;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.WorkOrderService;

import javax.swing.*;

/**
 * "Aktif servisler": atölyedeki (teslim/iade edilmemiş) tüm servisler, sayfalı. Satır Servis Kayıtları
 * listesindeki dili taşır: durum noktası, müşteri ve cihaz, sağda durum ve ödeme çipi. Bir durumda
 * çok uzun süre bekleyen iş alt satırda uyarı rengine döner.
 */
public class ActiveServicesPanel extends JPanel {

    private static final int ROWS_PER_PAGE = 5;
    /** Bir durumda bu kadar gün beklemiş açık iş uyarı rengiyle işaretlenir. */
    private static final int LINGER_DAYS = 7;

    private final WorkOrderService workOrderService;
    private final JPanel list = new JPanel(new MigLayout("insets 0, fillx, wrap, gapy 4", "[fill]", ""));
    private final JLabel count = DashboardUi.muted(" ");
    private final JPagination pagination = new AppPagination(10, 1, 1);
    private boolean adjusting;

    public ActiveServicesPanel(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 10 16, fillx, wrap", "[fill]", "[]6[]4[]");

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[]10[]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Aktif servisler"));
        header.add(count);
        header.add(DashboardUi.link("Tümünü gör", () -> {
            FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
            FormManager.showForm(form);
            form.showOpen();
        }));
        card.add(header);

        list.setOpaque(false);
        list.add(DashboardUi.emptyState("Yükleniyor…", " "));
        card.add(list);

        pagination.addChangeListener(e -> {
            if (!adjusting) loadPage(pagination.getSelectedPage());
        });
        card.add(pagination, "align center, hidemode 3");
        add(card);

        loadPage(1);
    }

    public void loadPage(int page) {
        workOrderService.getOpenPaged(page, ROWS_PER_PAGE).thenAccept(result ->
                SwingUtilities.invokeLater(() -> apply(result))
        ).exceptionally(ex -> {
            Servicio.getLogger().error("Aktif servisler yüklenirken hata oluştu", ex);
            SwingUtilities.invokeLater(() -> {
                list.removeAll();
                list.add(DashboardUi.emptyState("Liste yüklenemedi", "Veritabanına ulaşılamadı."));
                list.revalidate();
                list.repaint();
            });
            return null;
        });
    }

    private void apply(PageResult<WorkOrder> result) {
        adjusting = true;
        pagination.setPageRange(result.getPage(), result.getTotalPages());
        adjusting = false;
        pagination.setVisible(result.getTotalPages() > 1);
        count.setText(result.getTotalItems() > 0 ? result.getTotalItems() + " kayıt" : " ");

        list.removeAll();
        if (result.getItems().isEmpty()) {
            list.add(DashboardUi.emptyState("Atölyede servis yok", "Cihaz kabul ettiğinde burada takip edilir."));
        } else {
            for (WorkOrder wo : result.getItems()) list.add(row(wo));
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent row(WorkOrder wo) {
        String customer = wo.getCustomer() != null ? wo.getCustomer().getFullName() : "Müşteri #" + wo.getCustomerId();
        String device = wo.getDevice() != null ? wo.getDevice().getDisplayName() : "Cihaz";
        ServiceStatus status = wo.getServiceStatus();

        String since = ServiceBadges.sinceText(wo, status != null ? status.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "serviste");
        String sub = device + "  ·  SRV-" + wo.getId() + (since.isEmpty() ? "" : "  ·  " + since);
        boolean lingering = ServiceBadges.daysInStatus(wo) >= LINGER_DAYS;

        // Durum ve ödeme çipleri yan yana; ödeme çipi yalnızca çip (tutar farkı yazılmaz).
        JPanel right = new JPanel(new MigLayout("insets 0, gap 8", "[][]", "[center]"));
        right.setOpaque(false);
        if (status != null) right.add(RowKit.chip(new RowParts.Badge(status.getDisplayName(), status.getBadgeColor()), 24));
        right.add(RowKit.chip(ServiceBadges.payment(wo), 24));

        JButton row = RowKit.row(status != null ? status.getBadgeColor() : null, customer, sub,
                lingering ? "Label.foreground" : null, right);
        row.getAccessibleContext().setAccessibleName(customer + ", " + device);
        row.addActionListener(e -> FormManager.showForm(new FormWorkOrder(wo)));
        return row;
    }
}
