package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.OpenDocumentDto;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Müşteri detayının Genel Bakış bölümü: tezgahta sorulan üç soruyu — "cihazım ne durumda?",
 * "ne kadar borcum var?", "en son ne yaptık?" — bölüm değiştirmeden cevaplar.
 */
public class CustomerOverviewPanel extends JPanel {

    private final JLabel valActive = statValue();
    private final JLabel valServices = statValue();
    private final JLabel valCollected = statValue();
    private final JLabel valLastVisit = statValue();

    private final CustomerListSection<WorkOrder> activeServices;
    private final CustomerListSection<OpenDocumentDto> openDocuments;
    private final CustomerListSection<CustomerActivity> recentActivity;
    private final JLabel lblOpenTotal = new JLabel();

    public CustomerOverviewPanel(Consumer<WorkOrder> onOpenWorkOrder,
                                 Consumer<OpenDocumentDto> onOpenDocument,
                                 Consumer<CustomerActivity> onOpenActivity,
                                 Runnable onNewService) {
        setOpaque(false);
        // Aktif servisler geniş tarafta: dar ekranda durum rozetleri 60/40 bölüşümde kesiliyordu.
        setLayout(new MigLayout("insets 0, fill, gap 16", "[grow 70, fill][grow 30, fill]", "[]16[grow 50, fill]16[grow 50, fill]"));

        add(createStatStrip(), "span 2, growx, wrap");

        activeServices = new CustomerListSection<>("Aktif Servisler", Arrays.asList(
                new ColumnDef<>("Cihaz", WorkOrder.class, w -> w),
                ColumnDef.badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus),
                ColumnDef.currency("Ücret", WorkOrder::getTotalServiceAmount)
        ), "Serviste cihazı yok", "Teslim edilmemiş servis kaydı bulunmuyor.", true);
        activeServices.setEmptyAction("Yeni servis kaydı", onNewService);
        activeServices.setOnOpen(onOpenWorkOrder);
        JTable activeTable = activeServices.getTable();
        // Kayıt no ayrı kolon yerine cihazın alt satırında: dar ekranda iki kolon da kesiliyordu.
        activeTable.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<WorkOrder>(
                w -> w.getDevice() != null ? w.getDevice().getBrand() + " " + w.getDevice().getModel() : "Cihaz belirtilmedi",
                w -> "SRV-" + w.getId() + (w.getDevice() != null && w.getDevice().getSerialNo() != null
                        ? " · SN: " + w.getDevice().getSerialNo() : "")));
        activeTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        activeTable.getColumnModel().getColumn(1).setMinWidth(130);
        activeTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        add(activeServices, "wmin 0, hmin 160");

        openDocuments = new CustomerListSection<>("Açık Belgeler", Arrays.asList(
                new ColumnDef<>("Belge", String.class, OpenDocumentDto::getDocumentLabel),
                new ColumnDef<>("Tarih", String.class, d -> d.getDocumentDate() != null ? d.getDocumentDate().format(DateFormats.shortDate()) : "-"),
                ColumnDef.currency("Kalan", OpenDocumentDto::getRemainingAmount)
        ), "Ödenmemiş belge yok", "Müşterinin açık borcu bulunmuyor.", true);
        openDocuments.setOnOpen(onOpenDocument);
        lblOpenTotal.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        openDocuments.addHeaderAction(lblOpenTotal);
        add(openDocuments, "wmin 0, hmin 160, wrap");

        recentActivity = new CustomerListSection<>("Son Hareketler", Arrays.asList(
                new ColumnDef<>("Tarih", String.class, a -> a.getDate() != null ? a.getDate().format(DateFormats.dateTime()) : "-"),
                new ColumnDef<>("İşlem", CustomerActivity.Kind.class, CustomerActivity::getKind),
                new ColumnDef<>("Açıklama", String.class, CustomerActivity::getDescription),
                ColumnDef.currency("Tutar", CustomerActivity::getAmount)
        ), "Henüz hareket yok", "Servis, satış ve tahsilatlar burada tarih sırasıyla listelenir.", true);
        recentActivity.setOnOpen(onOpenActivity);
        JTable activityTable = recentActivity.getTable();
        activityTable.getColumnModel().getColumn(1).setCellRenderer(new KindRenderer());
        activityTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        activityTable.getColumnModel().getColumn(0).setMaxWidth(190);
        activityTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        activityTable.getColumnModel().getColumn(1).setMaxWidth(160);
        activityTable.getColumnModel().getColumn(2).setPreferredWidth(360);
        add(recentActivity, "span 2, wmin 0, hmin 180");
    }

    /**
     * Dört sayı tek satırda, ince ayraçlarla: eski büyük kutular aynı bilgiyi dört kat alanda veriyordu.
     */
    private JPanel createStatStrip() {
        JPanel strip = new JPanel(new MigLayout("insets 14 20 14 20, fillx, gapx 0",
                "[grow, sg s]20[]20[grow, sg s]20[]20[grow, sg s]20[]20[grow, sg s]", "[]"));
        strip.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        strip.add(stat("icons/activity.svg", "Serviste", valActive), "wmin 0");
        strip.add(new JSeparator(SwingConstants.VERTICAL), "growy");
        strip.add(stat("icons/wrench.svg", "Toplam servis", valServices), "wmin 0");
        strip.add(new JSeparator(SwingConstants.VERTICAL), "growy");
        strip.add(stat("icons/hand-coins.svg", "Toplam tahsilat", valCollected), "wmin 0");
        strip.add(new JSeparator(SwingConstants.VERTICAL), "growy");
        strip.add(stat("icons/clock.svg", "Son işlem", valLastVisit), "wmin 0");
        return strip;
    }

    private static JPanel stat(String iconPath, String caption, JLabel value) {
        JPanel panel = new JPanel(new MigLayout("insets 0, gap 12 2", "[][grow]", "[][]"));
        panel.setOpaque(false);
        JLabel icon = new JLabel(new Ikon(iconPath, 1f, "Label.disabledForeground"));
        JLabel lblCaption = new JLabel(caption);
        lblCaption.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        panel.add(icon, "span 1 2, aligny center");
        panel.add(lblCaption, "wmin 0, wrap");
        panel.add(value, "wmin 0");
        return panel;
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("—");
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        return label;
    }

    public void setWorkOrders(List<WorkOrder> workOrders) {
        List<WorkOrder> active = workOrders.stream()
                .filter(w -> w.getServiceStatus() != ServiceStatus.DELIVERED && w.getServiceStatus() != ServiceStatus.RETURN)
                .toList();
        activeServices.setData(active);
        valActive.setText(String.valueOf(active.size()));
        valServices.setText(String.valueOf(workOrders.size()));
    }

    public void setOpenDocuments(List<OpenDocumentDto> documents) {
        openDocuments.setData(documents);
        BigDecimal total = documents.stream()
                .map(OpenDocumentDto::getRemainingAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblOpenTotal.setText(documents.isEmpty() ? "" : documents.size() + " belge · " + Format.formatPrice(total));
    }

    public void setTotalCollected(BigDecimal total) {
        valCollected.setText(Format.formatPrice(total != null ? total : BigDecimal.ZERO));
    }

    /** @param activities tarihe göre en yeni önce sıralı, önceden kısaltılmış liste */
    public void setRecentActivity(List<CustomerActivity> activities) {
        recentActivity.setData(activities);
        valLastVisit.setText(activities.isEmpty() || activities.get(0).getDate() == null
                ? "—"
                : activities.get(0).getDate().format(DateFormats.shortDate()));
    }

    public void showError(String message) {
        activeServices.showError(message);
        openDocuments.showError(message);
        recentActivity.showError(message);
    }

    /** Hareket türünü ikonuyla gösterir; tür sütunu satırları göz ucuyla ayırt ettirir. */
    private static class KindRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof CustomerActivity.Kind kind) {
                setText(kind.getLabel());
                setIcon(new Ikon(kind.getIconPath(), 0.8f, "Label.disabledForeground"));
                setIconTextGap(8);
            } else {
                setIcon(null);
            }
            return this;
        }
    }
}
