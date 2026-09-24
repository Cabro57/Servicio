package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.FormAccounts;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.panels.CollectionPanel;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.CustomerBalanceDto;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Dikkat bekleyenler": kullanıcıdan hamle bekleyen üç kuyruk tek kartta, sekmelerle.
 * Teslime hazır (müşteri aranacak), borçlu müşteriler (tahsilat) ve parça bekleyen
 * servisler (tedarik takibi). Her satır kaydını açar; borçlu satırında doğrudan
 * "Tahsilat" düğmesi vardır.
 */
public class AttentionPanel extends JPanel {

    private static final int ROWS = 4;

    private enum Tab { READY, DEBT, WAITING }

    private final JToggleButton tabReady = tab("Teslime hazır");
    private final JToggleButton tabDebt = tab("Borçlu");
    private final JToggleButton tabWaiting = tab("Parça bekliyor");
    private final JPanel list = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0", "[fill]", ""));
    private final JButton btnSeeAll;

    private Tab current;
    private boolean userPickedTab;

    private PageResult<WorkOrder> ready;
    private PageResult<WorkOrder> waiting;
    private PageResult<CustomerBalanceDto> debt;
    private Map<Long, Customer> debtCustomers = Collections.emptyMap();

    /** Tahsilat alındığında çağrılır: kasa, hareketler ve durum çubuğu da tazelensin. */
    private final Runnable onMoneyChanged;

    public AttentionPanel(Runnable onMoneyChanged) {
        this.onMoneyChanged = onMoneyChanged;
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 10 16, fillx, wrap", "[fill]", "[]6[]");

        JToolBar tabs = new JToolBar();
        tabs.setFloatable(false);
        tabs.putClientProperty(FlatClientProperties.STYLE, "background: null");
        ButtonGroup group = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{tabReady, tabDebt, tabWaiting}) {
            group.add(b);
            tabs.add(b);
        }
        tabReady.addActionListener(e -> select(Tab.READY, true));
        tabDebt.addActionListener(e -> select(Tab.DEBT, true));
        tabWaiting.addActionListener(e -> select(Tab.WAITING, true));

        btnSeeAll = DashboardUi.link("Tümünü gör", this::openAll);

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[]12[]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Dikkat bekleyenler"));
        header.add(tabs);
        header.add(btnSeeAll);
        card.add(header);

        list.setOpaque(false);
        list.add(DashboardUi.emptyState("Yükleniyor…", " "));
        card.add(list);
        add(card);
    }

    private static JToggleButton tab(String text) {
        JToggleButton b = new JToggleButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "toolbar.margin: 3,8,3,8; arc: 10");
        return b;
    }

    // ── Veri ────────────────────────────────────────────────────────────────

    public void load() {
        WorkOrderService workOrders = ServiceManager.getWorkOrderService();
        CompletableFuture<PageResult<WorkOrder>> readyF = workOrders.getAllPaged(1, ROWS, ServiceStatus.READY.name());
        CompletableFuture<PageResult<WorkOrder>> waitingF = workOrders.getAllPaged(1, ROWS, ServiceStatus.WAITING_FOR_PART.name());
        CompletableFuture<PageResult<CustomerBalanceDto>> debtF = ServiceManager.getPaymentService()
                .getCustomersWithBalancePaged(null, 1, ROWS);
        CompletableFuture<Map<Long, Customer>> customersF = debtF.thenCompose(page -> {
            List<Long> ids = page.getItems().stream().map(CustomerBalanceDto::getCustomerId).collect(Collectors.toList());
            if (ids.isEmpty()) return CompletableFuture.completedFuture(Collections.<Long, Customer>emptyMap());
            return ServiceManager.getCustomerService().getAll(ids)
                    .thenApply(cs -> cs.stream().collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a)));
        });

        CompletableFuture.allOf(readyF, waitingF, customersF).thenRun(() -> SwingUtilities.invokeLater(() -> {
            ready = readyF.join();
            waiting = waitingF.join();
            debt = debtF.join();
            debtCustomers = customersF.join();
            tabReady.setText("Teslime hazır  " + ready.getTotalItems());
            tabDebt.setText("Borçlu  " + debt.getTotalItems());
            tabWaiting.setText("Parça bekliyor  " + waiting.getTotalItems());
            // Kullanıcı sekme seçmediyse ilk dolu kuyruk öne gelir: önce teslim, sonra para, sonra tedarik.
            Tab target = current;
            if (!userPickedTab || target == null) {
                if (ready.getTotalItems() > 0) target = Tab.READY;
                else if (debt.getTotalItems() > 0) target = Tab.DEBT;
                else if (waiting.getTotalItems() > 0) target = Tab.WAITING;
                else target = Tab.READY;
            }
            select(target, false);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Dikkat bekleyenler yüklenemedi", ex);
            SwingUtilities.invokeLater(this::renderError);
            return null;
        });
    }

    private void renderError() {
        list.removeAll();
        JPanel panel = DashboardUi.emptyState("Liste yüklenemedi", "Veritabanına ulaşılamadı.");
        panel.add(DashboardUi.link("Tekrar dene", this::load));
        list.add(panel);
        btnSeeAll.setVisible(false);
        list.revalidate();
        list.repaint();
    }

    private void select(Tab tab, boolean byUser) {
        if (byUser) userPickedTab = true;
        current = tab;
        tabReady.setSelected(tab == Tab.READY);
        tabDebt.setSelected(tab == Tab.DEBT);
        tabWaiting.setSelected(tab == Tab.WAITING);
        render();
    }

    private void render() {
        list.removeAll();
        if (current == Tab.DEBT) {
            if (debt == null || debt.getItems().isEmpty()) {
                list.add(DashboardUi.emptyState("Borçlu müşteri yok",
                        "Ödemesi kalan servis ya da açık hesap satış olduğunda burada listelenir."));
            } else {
                for (CustomerBalanceDto b : debt.getItems()) list.add(debtRow(b));
            }
            btnSeeAll.setVisible(debt != null && debt.getTotalItems() > 0);
        } else {
            PageResult<WorkOrder> page = current == Tab.READY ? ready : waiting;
            if (page == null || page.getItems().isEmpty()) {
                list.add(current == Tab.READY
                        ? DashboardUi.emptyState("Teslime hazır cihaz yok",
                        "Tamiri biten servisin durumunu \"Hazır\" yaptığında burada görünür.")
                        : DashboardUi.emptyState("Parça bekleyen servis yok",
                        "Durumu \"Parça Bekliyor\" olan servisler burada takip edilir."));
            } else {
                for (WorkOrder wo : page.getItems()) list.add(workOrderRow(wo));
            }
            btnSeeAll.setVisible(page != null && page.getTotalItems() > 0);
        }
        list.revalidate();
        list.repaint();
    }

    // ── Satırlar ───────────────────────────────────────────────────────────

    private JButton workOrderRow(WorkOrder wo) {
        JButton row = DashboardUi.rowButton();
        row.setLayout(new MigLayout("insets 0, fillx, gap 2 1", "[grow,fill]12[right]", "[][]"));

        String customer = wo.getCustomer() != null ? wo.getCustomer().getFullName() : "Müşteri #" + wo.getCustomerId();
        String device = wo.getDevice() != null ? wo.getDevice().getDisplayName() : "Cihaz";
        JLabel name = new JLabel(customer);
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        row.add(name, "wmin 0");

        BigDecimal remaining = wo.getRemainingAmount();
        boolean owes = remaining != null && remaining.signum() > 0;
        JLabel amount = new JLabel(owes ? Format.formatPrice(remaining) + " kalan" : "Ödendi");
        amount.putClientProperty(FlatClientProperties.STYLE, owes
                ? "font: bold; foreground: $Servicio.dangerColor"
                : "foreground: $Label.disabledForeground");
        row.add(amount, "wrap");

        String since = sinceText(wo, current == Tab.READY ? "hazır" : "bekliyor");
        row.add(DashboardUi.small(device + "  ·  SRV-" + wo.getId() + (since.isEmpty() ? "" : "  ·  " + since)), "span 2");

        row.getAccessibleContext().setAccessibleName(customer + ", " + device);
        row.addActionListener(e -> FormManager.showForm(new FormWorkOrder(wo)));
        return row;
    }

    private JComponent debtRow(CustomerBalanceDto b) {
        Customer c = debtCustomers.get(b.getCustomerId());
        String nameText = c != null ? c.getFullName() : "Müşteri #" + b.getCustomerId();

        JButton row = DashboardUi.rowButton();
        row.setLayout(new MigLayout("insets 0, fillx, gap 2 1", "[grow,fill]12[right]", "[][]"));
        JLabel name = new JLabel(nameText);
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        row.add(name, "wmin 0");
        JLabel amount = new JLabel(Format.formatPrice(b.getBalance()));
        amount.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.dangerColor");
        row.add(amount, "wrap");
        String phone = c != null && c.getPhoneNumber1() != null && !c.getPhoneNumber1().isEmpty()
                ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : "Telefon kayıtlı değil";
        row.add(DashboardUi.small(phone), "span 2");
        row.getAccessibleContext().setAccessibleName(nameText + ", borç " + Format.formatPrice(b.getBalance()));
        row.addActionListener(e -> {
            if (c != null) FormManager.showForm(new FormCustomer(c));
        });

        JButton collect = new JButton("Tahsilat", new Ikon("icons/hand-coins.svg", 14, "Label.foreground"));
        collect.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 3,8,3,8");
        collect.setEnabled(c != null);
        collect.addActionListener(e -> CollectionPanel.open(this, c, onMoneyChanged));

        JPanel wrap = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow,fill][]", "[center]"));
        wrap.setOpaque(false);
        wrap.add(row);
        wrap.add(collect);
        return wrap;
    }

    /**
     * Durumun ne zamandan beri sürdüğü. updated_at her düzenlemede değiştiği için kullanılmaz;
     * durumun değiştiği an bilinmiyorsa (V23 öncesi kayıtlar) cihazın geliş tarihinden
     * "gündür serviste" gösterilir. Gün, saat farkı değil takvim günü olarak sayılır.
     */
    private static String sinceText(WorkOrder wo, String verb) {
        LocalDateTime changed = wo.getStatusChangedAt();
        LocalDateTime time = changed != null ? changed : wo.getCreatedAt();
        if (time == null) return "";
        if (changed == null) verb = "serviste";
        long days = ChronoUnit.DAYS.between(time.toLocalDate(), LocalDate.now());
        if (days <= 0) return "bugün " + (changed != null ? verb : "geldi");
        return days + " gündür " + verb;
    }

    private void openAll() {
        if (current == Tab.DEBT) {
            FormManager.showForm(AllForms.getForm(FormAccounts.class));
            return;
        }
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showStatus(current == Tab.READY ? ServiceStatus.READY : ServiceStatus.WAITING_FOR_PART);
    }
}
