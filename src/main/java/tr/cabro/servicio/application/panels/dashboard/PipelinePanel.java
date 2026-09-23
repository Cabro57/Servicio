package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.model.enums.ServiceStatus;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Servis hattı: atölyedeki (teslim/iade edilmemiş) cihazların duruma göre dağılımı.
 * Her aşama tıklanınca Servisler listesini o duruma süzülmüş olarak açar.
 * Sıra işin akışıdır: Tamirde → Parça Bekliyor → Başka Serviste → Hazır; bugün açılanlar başlıkta.
 */
public class PipelinePanel extends JPanel {

    private static final ServiceStatus[] STAGES = {
            ServiceStatus.UNDER_REPAIR, ServiceStatus.WAITING_FOR_PART,
            ServiceStatus.ANOTHER_SERVICE, ServiceStatus.READY
    };

    private final Map<ServiceStatus, JLabel> countLabels = new EnumMap<>(ServiceStatus.class);
    private final JLabel lblTotal = DashboardUi.muted("—");
    private final JButton btnToday = DashboardUi.link("bugün —", PipelinePanel::openToday);

    public PipelinePanel() {
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 12 16, fillx, wrap", "[fill]", "[]8[]");

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[][][]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Atölye"));
        header.add(lblTotal, "gapleft 8");
        btnToday.setToolTipText("Bugün açılan servisleri listele");
        header.add(btnToday);
        header.add(DashboardUi.link("Atölyedekileri listele", PipelinePanel::openOpen));
        card.add(header);

        JPanel stages = new JPanel(new MigLayout("insets 0, fillx, gap 6", "[fill,sg s,0:pref][fill,sg s,0:pref][fill,sg s,0:pref][fill,sg s,0:pref]", "[fill]"));
        stages.setOpaque(false);
        for (ServiceStatus status : STAGES) {
            JLabel count = new JLabel("—");
            countLabels.put(status, count);
            stages.add(stage(status.getDisplayName(),
                    DashboardUi.badgeIcon(status.getIconPath(), 16, status.getBadgeColor()), count,
                    () -> openList(status)), "wmin 0");
        }
        card.add(stages);

        add(card);
    }

    private JButton stage(String name, Icon icon, JLabel count, Runnable action) {
        JButton button = DashboardUi.rowButton();
        button.setLayout(new MigLayout("insets 0, wrap, gap 2", "[]", "[][]"));
        button.putClientProperty(FlatClientProperties.STYLE,
                "background: null; arc: 12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1; margin: 8,10,8,10");
        count.putClientProperty(FlatClientProperties.STYLE, "font: bold +12");
        JLabel label = new JLabel(name, icon, SwingConstants.LEADING);
        label.setIconTextGap(6);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        button.add(label, "wmin 0");
        button.add(count);
        button.getAccessibleContext().setAccessibleName(name);
        button.setToolTipText(name + " — listeyi aç");
        button.addActionListener(e -> action.run());
        return button;
    }

    public void setCounts(Map<ServiceStatus, Long> counts) {
        long total = 0;
        for (ServiceStatus status : STAGES) {
            long value = counts.getOrDefault(status, 0L);
            total += value;
            JLabel label = countLabels.get(status);
            label.setText(String.valueOf(value));
            // Sıfır olan aşama geri çekilir; göz dolu aşamalara gider.
            label.putClientProperty(FlatClientProperties.STYLE, value == 0
                    ? "font: bold +12; foreground: $Label.disabledForeground"
                    : "font: bold +12; foreground: $Label.foreground");
        }
        lblTotal.setText(total == 0 ? "atölye boş" : total + " cihaz içeride");
    }

    public void setTodayCount(long count) {
        btnToday.setText("·  bugün " + count + " alındı");
    }

    private static void openList(ServiceStatus status) {
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showStatus(status);
    }

    private static void openToday() {
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showCreatedOn(java.time.LocalDate.now());
    }

    private static void openOpen() {
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showOpen();
    }
}
