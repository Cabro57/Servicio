package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.DateTimeField;
import tr.cabro.servicio.application.component.MessageModal;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.WorkOrderStatusHistory;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Servis durumuyla ilgili karar kartları: durum değiştirme (geçiş tarihiyle), bir geçişin
 * tarihini düzeltme ve teslimi/iadeyi geri alma. Tarih kuralları (gelecek olamaz, sıra
 * bozulamaz) service katmanında; burada ihlal hata bildirimi olarak döner.
 */
public final class WorkOrderStatusDialogs {

    private WorkOrderStatusDialogs() {}

    /** Açık (kaydı kilitlemeyen) durumlar, iş akışı sırasıyla; kapatanlar ayrı grupta gelir. */
    private static final ServiceStatus[] OPEN_ORDER = {
            ServiceStatus.ACCEPTED, ServiceStatus.UNDER_REPAIR, ServiceStatus.WAITING_FOR_PART,
            ServiceStatus.READY, ServiceStatus.ANOTHER_SERVICE};
    private static final ServiceStatus[] CLOSING_ORDER = {ServiceStatus.DELIVERED, ServiceStatus.RETURN};

    /**
     * Durum değiştirme penceresi: yeni durum ve geçiş tarihi tek pencerede seçilir. Durumlar
     * rozet renginde karolar olarak dizilir; teslim ve iade "Kaydı kapatır" başlığı altında ayrı
     * durur, seçilince kilidin ne getireceği yazılır ve birincil düğme eylemin adını alır
     * ("Teslim et", "İade et"). Seçim yapılana kadar birincil düğme kapalıdır.
     *
     * @param since     bir önceki geçişin tarihi (takvimde daha eskisi seçilemez); bilinmiyorsa null
     * @param onChanged kaydedilen yeni durumla çağrılır
     */
    public static void changeStatus(Component parent, Long workOrderId, ServiceStatus current,
                                    LocalDateTime since, Consumer<ServiceStatus> onChanged) {
        DateTimeField field = new DateTimeField();
        field.setEarliest(since);
        JLabel dateCaption = WorkOrderPanelSupport.createCaption("Geçiş tarihi");
        LockNote lockNote = new LockNote();

        MessageModal modal = MessageModal.of(MessageModal.Tone.INPUT, "Servis durumunu değiştir",
                "Şu an " + current.getDisplayName() + ". Yeni durumu seçin; şimdi olmadıysa tarihi düzeltin.");
        ServiceStatus[] selected = new ServiceStatus[1];

        ButtonGroup group = new ButtonGroup();
        JPanel choices = new JPanel(new MigLayout("insets 0, wrap 2, fillx, gap 6 6, hidemode 3",
                "[grow, fill, sg tile][grow, fill, sg tile]", ""));
        choices.setOpaque(false);
        JToggleButton first = null;
        for (ServiceStatus st : OPEN_ORDER) {
            JToggleButton tile = statusTile(st, st == current, group, () -> {
                selected[0] = st;
                onPick(st, modal, dateCaption, lockNote);
            });
            choices.add(tile);
            if (first == null && tile.isEnabled()) first = tile;
        }
        JLabel closingCaption = WorkOrderPanelSupport.createCaption("Kaydı kapatır");
        // Açık durum sayısı tek olduğunda başlık boş kalan hücreye düşmesin: her zaman yeni satırdan başlar.
        choices.add(closingCaption, "newline, span 2, gaptop 8");
        for (ServiceStatus st : CLOSING_ORDER) {
            choices.add(statusTile(st, st == current, group, () -> {
                selected[0] = st;
                onPick(st, modal, dateCaption, lockNote);
            }));
        }

        JPanel body = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0 4, hidemode 3", "[grow, fill]", ""));
        body.setOpaque(false);
        body.add(choices, "wmin 0");
        body.add(lockNote, "wmin 0, gaptop 6");
        body.add(dateCaption, "gaptop 10");
        body.add(field, "growx, wmin 0");

        modal.extra(body)
                .focus(first)
                .primary("Durumu değiştir", () -> {
                    ServiceStatus target = selected[0];
                    if (target == null) return;
                    submit(parent, "Servis durumu güncellenemedi",
                            () -> service().updateStatus(workOrderId, target, field.getValue()),
                            () -> onChanged.accept(target));
                });
        modal.updatePrimary(null, false);
        modal.show(parent);
    }

    /** Seçime göre tarih etiketi, kilit notu ve birincil düğmenin adı güncellenir. */
    private static void onPick(ServiceStatus st, MessageModal modal, JLabel dateCaption, LockNote lockNote) {
        dateCaption.setText(switch (st) {
            case DELIVERED -> "Teslim tarihi";
            case RETURN -> "İade tarihi";
            default -> "Geçiş tarihi";
        });
        lockNote.showFor(st);
        modal.updatePrimary(switch (st) {
            case DELIVERED -> "Teslim et";
            case RETURN -> "İade et";
            default -> "Durumu değiştir";
        }, true);
    }

    /**
     * Bir durum karosu: rozet renginde ikon ve ad, ince çizgili yuvarlak kutu. Seçilince rozetin
     * zemini ve yazı rengiyle dolar. Şu anki durum seçilemez ve "şu an" diye işaretlenir.
     */
    private static JToggleButton statusTile(ServiceStatus st, boolean isCurrent, ButtonGroup group, Runnable onSelect) {
        Ikon icon = new Ikon(st.getIconPath(), 16);
        icon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(
                c -> BadgePalette.foreground(st.getBadgeColor())));
        JToggleButton tile = new JToggleButton(isCurrent ? st.getDisplayName() + "  ·  şu an" : st.getDisplayName(), icon);
        tile.setHorizontalAlignment(SwingConstants.LEADING);
        tile.setIconTextGap(8);
        tile.setFocusPainted(false);
        String base = "arc: 10; margin: 8,10,8,10; focusWidth: 0; innerFocusWidth: 1; borderWidth: 1;"
                + " borderColor: $Component.borderColor; background: $Table.background;"
                + " hoverBackground: $Servicio.rowHoverBackground;"
                + " selectedBackground: " + BadgePalette.backgroundHex(st.getBadgeColor()) + ";"
                + " selectedForeground: " + BadgePalette.foregroundHex(st.getBadgeColor()) + ";"
                + " disabledText: $Label.disabledForeground";
        tile.putClientProperty(FlatClientProperties.STYLE, base);
        if (isCurrent) {
            tile.setEnabled(false);
            tile.setToolTipText("Kaydın şu anki durumu");
        }
        tile.addItemListener(e -> tile.putClientProperty(FlatClientProperties.STYLE,
                tile.isSelected() ? base + "; font: bold" : base));
        tile.addActionListener(e -> onSelect.run());
        group.add(tile);
        return tile;
    }

    /** Teslim/iade seçilince beliren uyarı satırı: kilit ikonu ve kilidin getirdiği kısıtlar. */
    private static final class LockNote extends JPanel {
        private final JTextArea text = new JTextArea();

        LockNote() {
            super(new MigLayout("insets 0, gap 8, fillx", "[][grow, fill]", "[top]"));
            setOpaque(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setEditable(false);
            text.setFocusable(false);
            text.setOpaque(false);
            text.setBorder(BorderFactory.createEmptyBorder());
            text.putClientProperty(FlatClientProperties.STYLE,
                    "background: null; margin: 0,0,0,0; font: -1; foreground: $Servicio.warningColor");
            add(new JLabel(new Ikon("icons/lock.svg", 14, "Servicio.warningColor")), "gaptop 1");
            add(text, "wmin 0");
            setVisible(false);
        }

        void showFor(ServiceStatus st) {
            setVisible(st.isClosed());
            if (!st.isClosed()) return;
            text.setText("Kayıt kilitlenir: parça, işçilik, not ve arıza tespiti değiştirilemez. Ödeme alınabilir."
                    + (st == ServiceStatus.RETURN ? " Servisteki parçalar stoğa geri döner." : ""));
            revalidate();
        }
    }

    /** Bir geçişin tarihini düzeltir; yeni tarih komşu geçişlerin arasında kalmalıdır. */
    public static void editDate(Component parent, WorkOrderStatusHistory row, Runnable onChanged) {
        DateTimeField field = new DateTimeField();
        field.setValue(row.getChangedAt());

        String label = row.getStatus() == ServiceStatus.ACCEPTED ? "Teslim alma tarihi" : row.getStatus().getDisplayName() + " tarihi";
        MessageModal.of(MessageModal.Tone.INPUT, label + "ni düzelt",
                        "Şu an: " + row.getChangedAt().format(DateFormats.dateTime())
                                + ". Tarih önceki ve sonraki durumların arasında kalmalı.")
                .extra(labeled(label, field))
                .focus(field.focusTarget())
                .primary("Tarihi kaydet", () -> submit(parent, "Durum tarihi güncellenemedi",
                        () -> service().updateStatusDate(row.getId(), field.getValue()), onChanged))
                .show(parent);
    }

    /**
     * Teslimi ya da iadeyi geri alır; kayıt bir önceki durumuna döner ve kilidi açılır.
     *
     * @param onReopened dönülen durumla çağrılır
     */
    public static void reopen(Component parent, Long workOrderId, ServiceStatus closedStatus,
                              Consumer<ServiceStatus> onReopened) {
        boolean returned = closedStatus == ServiceStatus.RETURN;
        MessageModal.of(MessageModal.Tone.WARNING,
                        returned ? "İade geri alınsın mı?" : "Teslim geri alınsın mı?",
                        (returned ? "İade tarihi silinir" : "Teslim tarihi silinir")
                                + ", kayıt bir önceki durumuna döner ve yeniden düzenlenebilir."
                                + (returned ? " Servisteki parçalar yeniden stoktan düşülür." : ""))
                .primary(returned ? "İadeyi geri al" : "Teslimi geri al", () -> service().reopen(workOrderId)
                        .thenAccept(previous -> SwingUtilities.invokeLater(() -> onReopened.accept(previous)))
                        .exceptionally(ex -> ErrorHandler.handle(parent, "Kayıt yeniden açılamadı", ex)))
                .show(parent);
    }

    /**
     * Service çağrısını çalıştırır. Tarih kuralı ihlalleri (gelecek tarih, boş tarih) çağrı anında
     * fırlatılır, sıra ihlalleri future'dan gelir; ikisi de aynı hata bildirimine düşer.
     */
    private static void submit(Component parent, String logContext,
                               java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> call, Runnable onDone) {
        try {
            call.get().thenRun(() -> SwingUtilities.invokeLater(onDone))
                    .exceptionally(ex -> ErrorHandler.handle(parent, logContext, ex));
        } catch (RuntimeException ex) {
            ErrorHandler.handle(parent, logContext, ex);
        }
    }

    private static JPanel labeled(String caption, JComponent field) {
        JPanel p = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0 4", "[grow, fill]", ""));
        p.setOpaque(false);
        p.add(WorkOrderPanelSupport.createCaption(caption));
        p.add(field, "growx, wmin 0");
        return p;
    }

    private static WorkOrderService service() {
        return ServiceManager.getWorkOrderService();
    }
}
