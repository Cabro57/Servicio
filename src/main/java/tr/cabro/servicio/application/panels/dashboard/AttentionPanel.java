package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.FormAccounts;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.application.forms.FormDevice;
import tr.cabro.servicio.application.forms.FormDevices;
import tr.cabro.servicio.model.DeviceLoan;
import tr.cabro.servicio.model.enums.LoanDirection;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.panels.CollectionPanel;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.utils.ServiceBadges;
import tr.cabro.servicio.model.enums.BadgeColor;
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
 * "Dikkat bekleyenler": kullanıcıdan hamle bekleyen dört kuyruk tek kartta, sekmelerle.
 * Teslime hazır (müşteri aranacak), borçlu müşteriler (tahsilat) ve parça bekleyen
 * servisler (tedarik takibi) ve açık emanetler (gecikmişler kırmızı). Her satır kaydını açar; borçlu satırında doğrudan
 * "Tahsilat" düğmesi vardır.
 */
public class AttentionPanel extends JPanel {

    private static final int ROWS = 4;

    private enum Tab { READY, DEBT, WAITING, LOAN }

    private final ViewTabs tabs = new ViewTabs();
    private final JPanel list = new JPanel(new MigLayout("insets 0, fillx, wrap, gapy 4", "[fill]", ""));
    private final JButton btnSeeAll;

    private Tab current;
    private boolean userPickedTab;

    private PageResult<WorkOrder> ready;
    private PageResult<WorkOrder> waiting;
    private PageResult<CustomerBalanceDto> debt;
    private List<DeviceLoan> openLoans = Collections.emptyList();
    private Map<Long, Customer> debtCustomers = Collections.emptyMap();

    /** Tahsilat alındığında çağrılır: kasa, hareketler ve durum çubuğu da tazelensin. */
    private final Runnable onMoneyChanged;

    public AttentionPanel(Runnable onMoneyChanged) {
        this.onMoneyChanged = onMoneyChanged;
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 10 16, fillx, wrap", "[fill]", "[]6[]");

        tabs.addView(Tab.READY.name(), "Teslime hazır");
        tabs.addView(Tab.DEBT.name(), "Borçlu");
        tabs.addView(Tab.WAITING.name(), "Parça bekliyor");
        tabs.addView(Tab.LOAN.name(), "Emanette");
        tabs.setOnChange(key -> select(Tab.valueOf(key), true));

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

        CompletableFuture<List<DeviceLoan>> loansF = ServiceManager.getDeviceLoanService().getOpen();

        CompletableFuture.allOf(readyF, waitingF, customersF, loansF).thenRun(() -> SwingUtilities.invokeLater(() -> {
            ready = readyF.join();
            openLoans = loansF.join();
            waiting = waitingF.join();
            debt = debtF.join();
            debtCustomers = customersF.join();
            tabs.setCount(Tab.READY.name(), ready.getTotalItems());
            tabs.setCount(Tab.DEBT.name(), debt.getTotalItems());
            tabs.setCount(Tab.WAITING.name(), waiting.getTotalItems());
            tabs.setCount(Tab.LOAN.name(), (long) openLoans.size());
            boolean overdueLoan = openLoans.stream().anyMatch(DeviceLoan::isOverdue);
            // Kullanıcı sekme seçmediyse ilk dolu kuyruk öne gelir: gecikmiş emanet (cihaz/depozito
            // risk altında), sonra teslim, para, tedarik ve diğer açık emanetler.
            Tab target = current;
            if (!userPickedTab || target == null) {
                if (overdueLoan) target = Tab.LOAN;
                else if (ready.getTotalItems() > 0) target = Tab.READY;
                else if (debt.getTotalItems() > 0) target = Tab.DEBT;
                else if (waiting.getTotalItems() > 0) target = Tab.WAITING;
                else if (!openLoans.isEmpty()) target = Tab.LOAN;
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
        tabs.select(tab.name(), false);
        render();
    }

    private void render() {
        list.removeAll();
        if (current == Tab.LOAN) {
            if (openLoans.isEmpty()) {
                list.add(DashboardUi.emptyState("Emanette cihaz yok",
                        "Cihaz sayfasından yedek cihaz verdiğinde ya da emanet aldığında burada takip edilir."));
            } else {
                // Gecikmişler önce, sonra iade tarihi en yakın olan (sorgu sırası).
                openLoans.stream().sorted(java.util.Comparator.comparing((DeviceLoan l) -> !l.isOverdue()))
                        .limit(ROWS).forEach(l -> list.add(loanRow(l)));
            }
            btnSeeAll.setVisible(!openLoans.isEmpty());
        } else if (current == Tab.DEBT) {
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

    /**
     * Teslime hazır ve parça bekleyen satırı: durum rengiyle nokta, müşteri; altında cihaz, servis no ve
     * durumun süresi (hazır 3 günü, parça beklerken 7 günü aşınca uyarı rengi). Sağda kalan tutar ve ödeme çipi.
     */
    private JButton workOrderRow(WorkOrder wo) {
        String customer = wo.getCustomer() != null ? wo.getCustomer().getFullName() : "Müşteri #" + wo.getCustomerId();
        String device = wo.getDevice() != null ? wo.getDevice().getDisplayName() : "Cihaz";
        boolean ready = current == Tab.READY;

        String since = ServiceBadges.sinceText(wo, ready ? "hazır" : "bekliyor");
        String sub = device + "  ·  SRV-" + wo.getId() + (since.isEmpty() ? "" : "  ·  " + since);
        boolean lingering = ServiceBadges.daysInStatus(wo) >= (ready ? 3 : 7);

        BigDecimal remaining = wo.getRemainingAmount();
        boolean owes = remaining != null && remaining.signum() > 0;
        JComponent right = RowKit.money(owes ? Format.formatPrice(remaining) : null, null,
                null, ServiceBadges.payment(wo));

        JButton row = RowKit.row(ready ? BadgeColor.PURPLE : BadgeColor.YELLOW, customer, sub,
                lingering ? "Label.foreground" : null, right);
        row.getAccessibleContext().setAccessibleName(customer + ", " + device);
        row.addActionListener(e -> FormManager.showForm(new FormWorkOrder(wo)));
        return row;
    }

    /** Borçlu müşteri satırı: sarı nokta (bize borçlu), ad ve telefon; sağda bakiye ve satır içinde "Tahsilat" düğmesi. */
    private JComponent debtRow(CustomerBalanceDto b) {
        Customer c = debtCustomers.get(b.getCustomerId());
        String nameText = c != null ? c.getFullName() : "Müşteri #" + b.getCustomerId();
        String phone = c != null && c.getPhoneNumber1() != null && !c.getPhoneNumber1().isEmpty()
                ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : "Telefon kayıtlı değil";

        JButton collect = new JButton("Tahsilat", new Ikon("icons/hand-coins.svg", 14, "Label.foreground"));
        collect.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        collect.setEnabled(c != null);
        collect.addActionListener(e -> CollectionPanel.open(this, c, onMoneyChanged));

        JPanel right = new JPanel(new MigLayout("insets 0, gap 14", "[right][]", "[center]"));
        right.setOpaque(false);
        right.add(RowKit.money(Format.formatPrice(b.getBalance()), null, null, null));
        right.add(collect);

        JButton row = RowKit.row(BadgeColor.YELLOW, nameText, phone, null, right);
        row.getAccessibleContext().setAccessibleName(nameText + ", borç " + Format.formatPrice(b.getBalance()));
        row.addActionListener(e -> {
            if (c != null) FormManager.showForm(new FormCustomer(c));
        });
        return row;
    }

    /** Emanet satırı: gecikmişte kırmızı nokta; müşteri, altında cihaz, kod ve süre; sağda depozito. */
    private JButton loanRow(DeviceLoan l) {
        boolean out = l.getDirection() == LoanDirection.OUT;
        String customer = l.getCustomer() != null ? l.getCustomer().getFullName() : "Müşteri #" + l.getCustomerId();
        String device = l.getDevice() != null ? l.getDevice().getDisplayName() : "Cihaz";
        long days = l.getGivenAt() != null ? ChronoUnit.DAYS.between(l.getGivenAt().toLocalDate(), LocalDate.now()) : 0;
        String when;
        if (l.isOverdue()) {
            when = (out ? "iade " : "teslim ") + ChronoUnit.DAYS.between(l.getDueAt().toLocalDate(), LocalDate.now()) + " gün gecikti";
        } else {
            when = (out ? "verileli " : "bırakılalı ") + (days == 0 ? "bugün" : days + " gün");
        }
        String sub = device + "  ·  " + l.getCode() + (out ? "" : " (emanet alındı)") + "  ·  " + when;
        JComponent right = l.hasDeposit() ? RowKit.money(Format.formatPrice(l.getDeposit()), null, "depozito", null) : null;
        JButton row = RowKit.row(l.isOverdue() ? BadgeColor.RED : BadgeColor.YELLOW, customer, sub,
                l.isOverdue() ? "Servicio.dangerColor" : null, right);
        row.getAccessibleContext().setAccessibleName(customer + ", " + device + ", emanet");
        row.addActionListener(e -> ServiceManager.getDeviceService().get(l.getDeviceId())
                .thenAccept(opt -> SwingUtilities.invokeLater(() -> opt.ifPresent(d -> FormManager.showForm(new FormDevice(d))))));
        return row;
    }

    private void openAll() {
        if (current == Tab.LOAN) {
            FormDevices form = (FormDevices) AllForms.getForm(FormDevices.class);
            FormManager.showForm(form);
            form.showOnLoan();
            return;
        }
        if (current == Tab.DEBT) {
            FormManager.showForm(AllForms.getForm(FormAccounts.class));
            return;
        }
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showStatus(current == Tab.READY ? ServiceStatus.READY : ServiceStatus.WAITING_FOR_PART);
    }
}
