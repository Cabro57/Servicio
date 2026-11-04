package tr.cabro.servicio.application.panels.loan;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.component.DateTimeField;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.DocumentExportModal;
import tr.cabro.servicio.application.system.NewCustomerModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.documents.DeviceLoanFormType;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceLoan;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.LoanDirection;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Emanet pencereleri: başlatma (ver/al), kapatma (iade al/teslim et) ve belge menüsü.
 * <p>
 * Yön cihazdan çıkar: "dükkân cihazı" işaretli cihaz müşteriye <b>verilir</b> (depozito alınabilir),
 * diğer cihazlar müşteriden emanet <b>alınır</b>. Kayıttan sonra ilgili belge penceresi açılır.
 */
public final class DeviceLoanDialogs {

    /** Teslim/iade anında en sık yazılan durum ifadeleri. */
    private static final String[] CONDITIONS = {
            "Ekran sağlam", "Kasa çiziksiz", "Kasa çizik", "Ekran çizik", "Çalışır durumda",
            "Şarj oluyor", "Kılıflı", "Ekran koruyuculu"
    };

    private DeviceLoanDialogs() {}

    public static LoanDirection directionFor(Device device) {
        return device.isLoaner() ? LoanDirection.OUT : LoanDirection.IN;
    }

    // =====================================================================
    // Başlat
    // =====================================================================

    public static void openStart(Component owner, Device device, Runnable onDone) {
        final String modalId = "device_loan_start";
        LoanDirection direction = directionFor(device);
        StartPanel[] ref = new StartPanel[1];
        ref[0] = new StartPanel(device, direction, () -> NewCustomerModal.push(modalId, c -> ref[0].appendNewCustomer(c)));
        StartPanel panel = ref[0];
        ServiceManager.getCustomerService().getAll().thenAccept(customers ->
                SwingUtilities.invokeLater(() -> panel.setCustomers(customers)));

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(direction == LoanDirection.OUT ? "Emanet Ver" : "Emanet Al", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        String title = (direction == LoanDirection.OUT ? "Emanet Ver · " : "Emanet Al · ") + device.getDisplayName();
        AppModal.showModal(owner, new SimpleModalBorder(panel, title, options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) { panel.customerBox.grabFocus(); return; }
            if (action != SimpleModalBorder.OK_OPTION) return;
            if (!panel.validateForm()) { controller.consume(); return; }

            DeviceLoan loan = panel.collect();
            Customer customer = panel.customerBox.getSelectedItem();
            try {
                ServiceManager.getDeviceLoanService().start(loan, device.isLoaner(), customer.getFullName())
                        .thenCompose(saved -> ServiceManager.getDeviceLoanService().get(saved.getId()))
                        .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(owner, Toast.Type.SUCCESS, direction == LoanDirection.OUT
                                    ? "Emanet verildi" + (loan.hasDeposit() ? ", depozito kasaya işlendi" : "")
                                    : "Emanet alındı");
                            if (onDone != null) onDone.run();
                            saved.ifPresent(l -> openDocument(owner, DeviceLoanFormType.defaultFor(l), l));
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(controller::consume);
                            return ErrorHandler.handle(owner, "Emanet kaydedilemedi", ex);
                        });
            } catch (RuntimeException ex) {
                controller.consume();
                ErrorHandler.handle(owner, "Emanet kaydedilemedi", ex);
            }
        }), modalId);
    }

    private static final class StartPanel extends JPanel {
        private final Device device;
        private final LoanDirection direction;
        final CustomerSelectBox customerBox;
        private final JLabel customerError = FormKit.errorLabel();
        private final DateTimeField givenAt = new DateTimeField();
        private final JFormattedTextField dueField = new JFormattedTextField();
        private final DatePicker duePicker = new DatePicker();
        private final CurrencyField depositField = new CurrencyField();
        private final JComboBox<PaymentType> depositType = new JComboBox<>(PaymentType.values());
        private final JComboBox<Object> workOrderBox = new JComboBox<>();
        private final JTextArea conditionArea = FormKit.textArea(3);
        private final JTextField accessoriesField = new JTextField();
        private final JTextField noteField = new JTextField();
        private int customerToken;

        StartPanel(Device device, LoanDirection direction, Runnable onNewCustomer) {
            super(new BorderLayout());
            this.device = device;
            this.direction = direction;
            boolean out = direction == LoanDirection.OUT;

            JPanel form = FormKit.railForm(680);
            add(FormKit.scroll(form), BorderLayout.CENTER);

            // --- Müşteri ---
            form.add(FormKit.rail("Müşteri", out ? "Yedek cihazı teslim alan kişi." : "Cihazı emanet bırakan kişi."), "top");
            customerBox = new CustomerSelectBox(e -> onNewCustomer.run());
            customerBox.setOnSelectionChanged(this::onCustomerChanged);
            JPanel customerCell = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]3[]"));
            customerCell.setOpaque(false);
            customerCell.add(customerBox, "wmin 0");
            customerCell.add(customerError, "wmin 0");
            form.add(customerCell);

            // --- Tarihler ve depozito ---
            form.add(FormKit.rail("Emanet", out
                    ? "Depozito kasaya giriş olarak işlenir; iadede hasar bedeli kesilip kalanı geri ödenir."
                    : "Planlanan teslim tarihi isteğe bağlıdır."), "top");
            JPanel grid = FormKit.grid(2);
            givenAt.setValue(LocalDateTime.now());
            grid.add(FormKit.cell(out ? "Veriliş zamanı" : "Bırakılış zamanı", givenAt, null));
            duePicker.setDateFormat("dd/MM/yyyy");
            duePicker.setEditor(dueField);
            if (out) duePicker.setSelectedDate(LocalDate.now().plusDays(7));
            grid.add(FormKit.cell(out ? "Son iade tarihi" : "Planlanan teslim", dueField, FormKit.note("Boş bırakılabilir")));
            if (out) {
                depositField.setAvailableCurrencies(List.of("TRY"));
                grid.add(FormKit.cell("Depozito (TL)", depositField, FormKit.note("0 = depozito alınmadı")));
                grid.add(FormKit.cell("Depozito ödeme yöntemi", depositType, null));
                workOrderBox.addItem("— Bağlı servis yok —");
                grid.add(FormKit.cell("Bağlı servis", workOrderBox, FormKit.note("Müşterinin cihazı tamirdeyse seçin")), "span 2");
            }
            form.add(grid);

            // --- Durum ---
            form.add(FormKit.rail("Durum", "Teslim anındaki görünüm ve eksikler; belgede yer alır."), "top");
            JPanel state = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]8[]10[]"));
            state.setOpaque(false);
            JPanel chips = WrapLayout.panel(6, 6);
            for (String c : CONDITIONS) {
                JButton chip = FormKit.chipButton(c);
                chip.addActionListener(e -> FormKit.appendPhrase(conditionArea, c));
                chips.add(chip);
            }
            state.add(chips, "wmin 0");
            state.add(FormKit.areaScroll(conditionArea), "hmin 70");
            accessoriesField.setText(device.getAccessory() != null ? device.getAccessory() : "");
            accessoriesField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Şarj aleti, kablo, kılıf…");
            JPanel more = FormKit.grid(1);
            more.add(FormKit.cell("Aksesuarlar", accessoriesField, null));
            noteField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsteğe bağlı");
            more.add(FormKit.cell("Not", noteField, null));
            state.add(more);
            form.add(state);
        }

        void setCustomers(List<Customer> customers) {
            customerBox.setCustomers(customers);
        }

        void appendNewCustomer(Customer customer) {
            customerBox.appendCustomer(customer);
        }

        private void onCustomerChanged(Customer customer) {
            customerBox.setError(false);
            FormKit.clear(null, customerError);
            if (direction != LoanDirection.OUT) return;
            int token = ++customerToken;
            workOrderBox.removeAllItems();
            workOrderBox.addItem("— Bağlı servis yok —");
            if (customer == null) return;
            ServiceManager.getWorkOrderService().getAll(customer.getId()).thenAccept(list -> SwingUtilities.invokeLater(() -> {
                if (token != customerToken) return;
                for (WorkOrder wo : list) {
                    if (wo.getServiceStatus() == ServiceStatus.DELIVERED || wo.getServiceStatus() == ServiceStatus.RETURN) continue;
                    workOrderBox.addItem(new WorkOrderOption(wo));
                }
                // Tek açık servis varsa emanet büyük olasılıkla onun için: önerilir.
                if (workOrderBox.getItemCount() == 2) workOrderBox.setSelectedIndex(1);
            })).exceptionally(ex -> null);
        }

        boolean validateForm() {
            if (customerBox.getSelectedItem() == null) {
                customerBox.setError(true);
                FormKit.fail(new JLabel(), customerError, "Müşteri seçin ya da yeni müşteri ekleyin.");
                customerBox.grabFocus();
                return false;
            }
            return true;
        }

        DeviceLoan collect() {
            DeviceLoan loan = new DeviceLoan();
            loan.setDeviceId(device.getId());
            loan.setCustomerId(customerBox.getSelectedItem().getId());
            loan.setDirection(direction);
            LocalDateTime given = givenAt.getValue();
            loan.setGivenAt(given != null ? given : LocalDateTime.now());
            loan.setDueAt(duePicker.getSelectedDate() != null ? duePicker.getSelectedDate().atStartOfDay() : null);
            if (direction == LoanDirection.OUT) {
                loan.setDeposit(BigDecimal.valueOf(depositField.getDoubleValue()).setScale(2, RoundingMode.HALF_UP));
                loan.setDepositPaymentType((PaymentType) depositType.getSelectedItem());
                if (workOrderBox.getSelectedItem() instanceof WorkOrderOption o) loan.setWorkOrderId(o.workOrder.getId());
            }
            loan.setConditionOut(conditionArea.getText().trim());
            loan.setAccessories(accessoriesField.getText().trim());
            loan.setNote(noteField.getText().trim());
            return loan;
        }
    }

    private record WorkOrderOption(WorkOrder workOrder) {
        @Override public String toString() {
            Device d = workOrder.getDevice();
            return "SRV-" + workOrder.getId() + (d != null ? " · " + d.getDisplayName() : "");
        }
    }

    // =====================================================================
    // Kapat
    // =====================================================================

    public static void openClose(Component owner, DeviceLoan loan, Runnable onDone) {
        final String modalId = "device_loan_close";
        ClosePanel panel = new ClosePanel(loan);
        LoanDirection direction = loan.getDirection();
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(direction == LoanDirection.OUT ? "İadeyi Kaydet" : "Teslimi Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        String customerName = loan.getCustomer() != null ? loan.getCustomer().getFullName() : null;
        AppModal.showModal(owner, new SimpleModalBorder(panel, direction.getCloseActionName() + " · " + loan.getCode(), options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) { panel.conditionArea.requestFocusInWindow(); return; }
            if (action != SimpleModalBorder.OK_OPTION) return;
            if (!panel.validateForm()) { controller.consume(); return; }
            try {
                ServiceManager.getDeviceLoanService().close(loan.getId(), panel.returnedAt(), panel.conditionArea.getText().trim(),
                                panel.damage(), (PaymentType) panel.refundType.getSelectedItem(), customerName)
                        .thenCompose(saved -> ServiceManager.getDeviceLoanService().get(saved.getId()))
                        .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(owner, Toast.Type.SUCCESS, direction == LoanDirection.OUT
                                    ? "Emanet iade alındı" + (loan.hasDeposit() ? ", depozito hesabı kapatıldı" : "")
                                    : "Emanet cihaz teslim edildi");
                            if (onDone != null) onDone.run();
                            saved.ifPresent(l -> openDocument(owner, DeviceLoanFormType.defaultFor(l), l));
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(controller::consume);
                            return ErrorHandler.handle(owner, "Emanet kapatılamadı", ex);
                        });
            } catch (RuntimeException ex) {
                controller.consume();
                ErrorHandler.handle(owner, "Emanet kapatılamadı", ex);
            }
        }), modalId);
    }

    private static final class ClosePanel extends JPanel {
        private final DeviceLoan loan;
        private final DateTimeField returnedField = new DateTimeField();
        final JTextArea conditionArea = FormKit.textArea(3);
        private final CurrencyField damageField = new CurrencyField();
        private final JLabel damageError = FormKit.errorLabel();
        final JComboBox<PaymentType> refundType = new JComboBox<>(PaymentType.values());
        private final JLabel refundLabel = new JLabel();

        ClosePanel(DeviceLoan loan) {
            super(new BorderLayout());
            this.loan = loan;
            boolean out = loan.getDirection() == LoanDirection.OUT;
            boolean deposit = out && loan.hasDeposit();

            JPanel form = FormKit.railForm(620);
            add(FormKit.scroll(form), BorderLayout.CENTER);

            form.add(FormKit.rail("Emanet", loan.isOverdue() ? "İade tarihi geçmiş." : "Kayıt özeti."), "top");
            JPanel summary = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]3[]"));
            summary.setOpaque(false);
            JLabel who = new JLabel((loan.getCustomer() != null ? loan.getCustomer().getFullName() : "—"));
            who.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            summary.add(who, "wmin 0");
            summary.add(FormKit.note((out ? "Verildi " : "Bırakıldı ") + fmt(loan.getGivenAt())
                    + (loan.getDueAt() != null ? "  ·  " + (out ? "son iade " : "planlanan ") + loan.getDueAt().format(DateFormats.shortDate()) : "")
                    + (deposit ? "  ·  depozito " + Format.formatPrice(loan.getDeposit()) : "")), "wmin 0");
            if (loan.getConditionOut() != null && !loan.getConditionOut().isBlank()) {
                summary.add(FormKit.note("Teslimdeki durum: " + loan.getConditionOut()), "wmin 0");
            }
            form.add(summary);

            form.add(FormKit.rail(out ? "İade" : "Teslim", out
                    ? "Hasar varsa açıklayın; bedeli depozitodan kesilir."
                    : "Cihazı müşteriye teslim ederken durumunu not edin."), "top");
            JPanel body = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]10[]10[]"));
            body.setOpaque(false);
            JPanel grid = FormKit.grid(2);
            returnedField.setValue(LocalDateTime.now());
            grid.add(FormKit.cell(out ? "İade zamanı" : "Teslim zamanı", returnedField, null), "span 2");
            if (deposit) {
                damageField.setAvailableCurrencies(List.of("TRY"));
                grid.add(FormKit.cell("Hasar kesintisi (TL)", damageField, damageError));
                refundType.setSelectedItem(loan.getDepositPaymentType() != null ? loan.getDepositPaymentType() : PaymentType.CASH);
                grid.add(FormKit.cell("İade ödeme yöntemi", refundType, null));
                refundLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                grid.add(refundLabel, "span 2");
                damageField.addPropertyChangeListener("value", e -> updateRefund());
                updateRefund();
            }
            body.add(grid);
            JPanel chips = WrapLayout.panel(6, 6);
            for (String c : CONDITIONS) {
                JButton chip = FormKit.chipButton(c);
                chip.addActionListener(e -> FormKit.appendPhrase(conditionArea, c));
                chips.add(chip);
            }
            conditionArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, out ? "Dönüşteki durum, hasar açıklaması…" : "Teslimdeki durum…");
            body.add(chips, "wmin 0");
            body.add(FormKit.areaScroll(conditionArea), "hmin 70");
            form.add(body);
        }

        private void updateRefund() {
            BigDecimal refund = loan.getDeposit().subtract(damage());
            refundLabel.setText(refund.signum() >= 0
                    ? "Müşteriye iade edilecek: " + Format.formatPrice(refund)
                    : "Hasar bedeli depozitoyu aşıyor");
            FormKit.clear(damageField, damageError);
        }

        BigDecimal damage() {
            return BigDecimal.valueOf(damageField.getDoubleValue()).setScale(2, RoundingMode.HALF_UP);
        }

        LocalDateTime returnedAt() {
            LocalDateTime v = returnedField.getValue();
            return v != null ? v : LocalDateTime.now();
        }

        boolean validateForm() {
            if (loan.getDirection() == LoanDirection.OUT && loan.hasDeposit() && damage().compareTo(loan.getDeposit()) > 0) {
                FormKit.fail(damageField, damageError, "Depozitodan fazla kesilemez; aşan kısmı ayrıca tahsil edin.");
                damageField.requestFocusInWindow();
                return false;
            }
            return true;
        }
    }

    // =====================================================================
    // İptal
    // =====================================================================

    /** Yanlış açılmış emaneti onayla iptal eder (kayıt ve depozito kasa hareketi kalkar). */
    public static void confirmCancel(Component owner, DeviceLoan loan, Runnable onDone) {
        String money = loan.hasDeposit()
                ? " Alınan " + Format.formatPrice(loan.getDeposit()) + " depozito kasa raporundan da silinir." : "";
        tr.cabro.servicio.application.component.MessageModal.of(
                        tr.cabro.servicio.application.component.MessageModal.Tone.DANGER, "Emaneti iptal et",
                        loan.getCode() + " kaydı yanlış açıldıysa iptal edin; geçmişte görünmez." + money
                                + " Cihaz gerçekten verilip geri geldiyse iptal yerine \"" + loan.getDirection().getCloseActionName() + "\" kullanın.")
                .primary("İptal et", () -> ServiceManager.getDeviceLoanService().cancel(loan.getId())
                        .thenRun(() -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(owner, Toast.Type.SUCCESS, loan.getCode() + " iptal edildi");
                            if (onDone != null) onDone.run();
                        }))
                        .exceptionally(ex -> ErrorHandler.handle(owner, "Emanet iptal edilemedi", ex)))
                .show(owner);
    }

    // =====================================================================
    // Belgeler
    // =====================================================================

    /** Emanete uygun belgelerin menüsü ({@code invoker} altında açılır). */
    public static void showDocumentMenu(Component invoker, Component owner, DeviceLoan loan) {
        JPopupMenu popup = new JPopupMenu();
        for (DeviceLoanFormType type : DeviceLoanFormType.values()) {
            if (!type.isApplicableTo(loan)) continue;
            JMenuItem item = new JMenuItem(type.getDisplayName());
            item.addActionListener(e -> openDocument(owner, type, loan));
            popup.add(item);
        }
        // Düğmeden açılınca altında; tablo satırından açılınca imlecin yanında.
        Point mouse = invoker instanceof JButton ? null : invoker.getMousePosition();
        if (mouse != null) popup.show(invoker, mouse.x, mouse.y);
        else popup.show(invoker, 0, invoker.getHeight());
    }

    public static void openDocument(Component owner, DeviceLoanFormType type, DeviceLoan loan) {
        ServiceManager.getBusinessService().get().thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            Business shop = shopOpt.orElse(null);
            String customerName = loan.getCustomer() != null ? loan.getCustomer().getFullName() : "";
            DocumentExportModal.Spec spec = new DocumentExportModal.Spec(
                    type.getDisplayName(), type.getFileSlug() + "-" + loan.getCode(),
                    type.getLeftSignerLabel(), shop != null ? shop.getBusinessName() : "",
                    type.getRightSignerLabel(), customerName,
                    false, type.getEditableTexts(), type.getSupportedFormats(), false);
            DocumentExportModal.show(owner, spec, (request, format, outFile) -> type.generate(loan, shop, request, format, outFile));
        })).exceptionally(ex -> ErrorHandler.handle(owner, "Belge penceresi açılamadı", ex));
    }

    private static String fmt(LocalDateTime t) {
        return t != null ? t.format(DateFormats.dateTime()) : "—";
    }
}
