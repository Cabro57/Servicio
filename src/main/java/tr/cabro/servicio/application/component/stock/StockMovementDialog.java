package tr.cabro.servicio.application.component.stock;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.panels.setting.SettingsDialog;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.model.StockLevel;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.StockService;
import tr.cabro.servicio.service.exception.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Parça/ürün kartındaki "Stok Hareketi" penceresi: giriş, çıkış, sayım ve depolar arası transfer.
 * <p>
 * Stoğu değiştirmenin tek arayüz yolu budur (düzenleme formu stoğa dokunmaz). Her işlem bir depo
 * seçer; alan altında o depodaki adet, alt satırda işlemin sonucu ("Ana Depo 9 → 12") canlı
 * görünür. Depoda olmayan miktar çıkarılamaz/transfer edilemez — birincil düğme kilitlenir.
 */
public final class StockMovementDialog {

    public enum Mode { IN, OUT, COUNT, TRANSFER }

    /** Depo seçeneği: bakiyesiyle birlikte ("Ana Depo — 9 adet"). */
    private record Option(Long id, String name, int quantity, boolean isDefault, boolean active) {
        @Override public String toString() {
            return name + (isDefault ? " (varsayılan)" : "") + " — " + quantity + " adet";
        }
    }

    private final StockService stockService = ServiceManager.getStockService();
    private final StockItemKind kind;
    private final Long itemId;
    private final Runnable onDone;
    private final List<Option> options;
    private final Component parent;

    private final SettingsDialog dialog = new SettingsDialog("Stok hareketi", 500);
    private final ViewTabs modes = new ViewTabs();
    private final JComboBox<Option> fromCombo = new JComboBox<>();
    private final JComboBox<Option> toCombo = new JComboBox<>();
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
    private final JTextField costField = new JTextField();
    private final JComboBox<ReferenceType> reasonCombo = new JComboBox<>(new ReferenceType[]{ReferenceType.LOSS, ReferenceType.ADJUSTMENT});
    private final JTextField noteField = new JTextField();

    private final JLabel fromLabel = FormKit.fieldLabel("Depo");
    private final JLabel qtyLabel = FormKit.fieldLabel("Adet");
    private final JLabel noteLabel = FormKit.fieldLabel("Açıklama");
    private final JLabel fromHint = FormKit.note(" ");
    private final JLabel noteError = FormKit.errorLabel();
    private JPanel fromCell, toCell, qtyCell, costCell, reasonCell;
    private Mode mode;

    private StockMovementDialog(Component parent, StockItemKind kind, Long itemId, List<StockLevel> levels, Runnable onDone) {
        this.parent = parent;
        this.kind = kind;
        this.itemId = itemId;
        this.onDone = onDone;
        this.options = levels.stream()
                .map(l -> new Option(l.getWarehouseId(), l.getWarehouseName(), l.getQuantity(), l.isDefaultWarehouse(), l.isActiveWarehouse()))
                .toList();
    }

    /**
     * Pencereyi açar. Depo bakiyeleri önce okunur; pencere güncel rakamlarla açılır.
     *
     * @param defaultCost girişte önerilen birim maliyet (kartın alış fiyatı), null olabilir
     */
    public static void open(Component parent, StockItemKind kind, Long itemId, String itemName,
                            BigDecimal defaultCost, Mode initial, Runnable onDone) {
        ServiceManager.getStockService().getLevels(kind, itemId).thenAccept(levels -> SwingUtilities.invokeLater(() -> {
            StockMovementDialog d = new StockMovementDialog(parent, kind, itemId, levels, onDone);
            d.build(itemName, defaultCost, levels.stream().mapToInt(StockLevel::getQuantity).sum());
            d.setMode(initial != null ? initial : Mode.IN);
            d.dialog.show(parent, (JComponent) ((JSpinner.DefaultEditor) d.qtySpinner.getEditor()).getTextField());
        })).exceptionally(ex -> ErrorHandler.handle(parent, "Depo bakiyeleri okunamadı", ex));
    }

    // ------------------------------------------------------------------ yapı

    private void build(String itemName, BigDecimal defaultCost, int total) {
        dialog.lead(itemName + "  ·  tüm depolarda " + total + " adet");

        modes.addView(Mode.IN.name(), "Giriş");
        modes.addView(Mode.OUT.name(), "Çıkış");
        modes.addView(Mode.COUNT.name(), "Sayım");
        modes.addView(Mode.TRANSFER.name(), "Transfer");
        modes.setOnChange(k -> setMode(Mode.valueOf(k)));
        JPanel modeRow = new JPanel(new MigLayout("insets 0, gap 0", "[]push", "[]"));
        modeRow.setOpaque(false);
        modeRow.add(modes);

        JPanel body = dialog.body();
        body.add(modeRow, "span 2, gapbottom 4");
        fromCell = FormKit.cell(fromLabel, fromCombo, fromHint);
        toCell = FormKit.cell(FormKit.fieldLabel("Hedef depo"), toCombo, null);
        qtyCell = FormKit.cell(qtyLabel, qtySpinner, null);
        costCell = FormKit.cell(FormKit.fieldLabel("Birim maliyet (TL)"), costField, FormKit.note("İsteğe bağlı; geçmişte görünür"));
        reasonCell = FormKit.cell(FormKit.fieldLabel("Sebep"), reasonCombo, null);
        body.add(fromCell);
        body.add(toCell);
        body.add(qtyCell);
        body.add(costCell);
        body.add(reasonCell);
        body.add(FormKit.cell(noteLabel, noteField, noteError), "span 2");

        if (defaultCost != null && defaultCost.signum() > 0) {
            costField.setText(defaultCost.stripTrailingZeros().toPlainString().replace('.', ','));
        }
        reasonCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof ReferenceType r) setText(r == ReferenceType.LOSS ? "Fire / kayıp / hasar" : "Düzeltme (açıklama zorunlu)");
                return this;
            }
        });

        fromCombo.addActionListener(e -> refresh());
        toCombo.addActionListener(e -> refresh());
        reasonCombo.addActionListener(e -> refresh());
        qtySpinner.addChangeListener(e -> refresh());
        FormKit.clearOnType(noteField, noteError);

        dialog.primary("Kaydet", false, this::save);
    }

    private void setMode(Mode mode) {
        this.mode = mode;
        if (!Objects.equals(modes.getSelected(), mode.name())) modes.select(mode.name(), false);

        boolean transfer = mode == Mode.TRANSFER;
        fromLabel.setText(transfer ? "Kaynak depo" : "Depo");
        qtyLabel.setText(mode == Mode.COUNT ? "Sayılan adet" : "Adet");
        noteLabel.setText(mode == Mode.OUT && reasonCombo.getSelectedItem() == ReferenceType.ADJUSTMENT ? "Açıklama (zorunlu)" : "Açıklama");
        noteField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, switch (mode) {
            case IN -> "Fatura / irsaliye no, tedarikçi…";
            case OUT -> "Ne oldu? (kırıldı, iade edildi, kayıp…)";
            case COUNT -> "Sayımı yapan, not…";
            case TRANSFER -> "Neden taşındı? (isteğe bağlı)";
        });
        toCell.setVisible(transfer);
        costCell.setVisible(mode == Mode.IN);
        reasonCell.setVisible(mode == Mode.OUT);

        // Giriş/çıkış/sayım yalnızca aktif depoya; transfer pasif depodaki stoğu da boşaltabilir.
        Option keepFrom = (Option) fromCombo.getSelectedItem();
        fill(fromCombo, options.stream().filter(o -> o.active() || (transfer && o.quantity() != 0)).toList(), keepFrom);
        if (transfer) {
            fill(toCombo, options.stream().filter(Option::active).toList(), (Option) toCombo.getSelectedItem());
            // Hedef kaynakla aynı açılmasın: ilk farklı aktif depo önerilir.
            Option from = (Option) fromCombo.getSelectedItem();
            Option to = (Option) toCombo.getSelectedItem();
            if (from != null && to != null && from.id().equals(to.id())) {
                for (int i = 0; i < toCombo.getItemCount(); i++) {
                    if (!toCombo.getItemAt(i).id().equals(from.id())) {
                        toCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }

        SpinnerNumberModel model = (SpinnerNumberModel) qtySpinner.getModel();
        model.setMinimum(mode == Mode.COUNT ? 0 : 1);
        if (mode == Mode.COUNT) {
            Option from = (Option) fromCombo.getSelectedItem();
            qtySpinner.setValue(from != null ? Math.max(0, from.quantity()) : 0);
        } else if ((Integer) qtySpinner.getValue() < 1) {
            qtySpinner.setValue(1);
        }

        dialog.setPrimaryText(switch (mode) {
            case IN -> "Girişi kaydet";
            case OUT -> "Çıkışı kaydet";
            case COUNT -> "Sayımı kaydet";
            case TRANSFER -> "Transfer et";
        });
        FormKit.clear(noteField, noteError);
        refresh();
        FormKit.revalidateUp(dialog);
    }

    /** Listeyi doldurur; önceki seçim hâlâ varsa korunur, yoksa varsayılan depo seçilir. */
    private static void fill(JComboBox<Option> combo, List<Option> items, Option keep) {
        combo.removeAllItems();
        Option select = null;
        for (Option o : items) {
            combo.addItem(o);
            if (keep != null && o.id().equals(keep.id())) select = o;
        }
        if (select == null) select = items.stream().filter(Option::isDefault).findFirst().orElse(items.isEmpty() ? null : items.get(0));
        combo.setSelectedItem(select);
    }

    // ------------------------------------------------------------------ canlı önizleme

    private void refresh() {
        if (mode == null) return;
        Option from = (Option) fromCombo.getSelectedItem();
        Option to = (Option) toCombo.getSelectedItem();
        int qty = (Integer) qtySpinner.getValue();
        int have = from != null ? from.quantity() : 0;
        fromHint.setText(from == null ? "Aktif depo yok" : "Bu depoda " + have + " adet");

        if (mode == Mode.OUT) {
            noteLabel.setText(reasonCombo.getSelectedItem() == ReferenceType.ADJUSTMENT ? "Açıklama (zorunlu)" : "Açıklama");
        }

        boolean ok = from != null;
        String text;
        String color = null;
        switch (mode) {
            case IN -> text = from == null ? "" : from.name() + ": " + have + " → " + (have + qty) + " adet";
            case OUT -> {
                ok &= qty <= have;
                text = from == null ? "" : qty > have
                        ? from.name() + " deposunda yalnızca " + have + " adet var"
                        : from.name() + ": " + have + " → " + (have - qty) + " adet";
                if (qty > have) color = "Servicio.dangerColor";
            }
            case COUNT -> {
                int diff = qty - have;
                text = from == null ? "" : diff == 0
                        ? "Sistemle aynı; hareket yazılmaz"
                        : "Sistem " + have + ", sayılan " + qty + " · fark " + (diff > 0 ? "+" : "−") + Math.abs(diff);
                if (diff != 0) color = "Servicio.warningColor";
                ok &= diff != 0;
            }
            case TRANSFER -> {
                boolean same = from != null && to != null && from.id().equals(to.id());
                ok &= to != null && !same && qty <= have;
                if (same) {
                    text = "Kaynak ve hedef aynı depo";
                    color = "Servicio.dangerColor";
                } else if (from != null && qty > have) {
                    text = from.name() + " deposunda yalnızca " + have + " adet var";
                    color = "Servicio.dangerColor";
                } else {
                    text = from == null || to == null ? "" : from.name() + " " + have + " → " + (have - qty)
                            + "   ·   " + to.name() + " " + to.quantity() + " → " + (to.quantity() + qty);
                }
            }
            default -> text = "";
        }
        dialog.status(text, color != null ? "icons/circle-alert.svg" : null, color);
        dialog.setPrimaryEnabled(ok);
    }

    // ------------------------------------------------------------------ kayıt

    private void save() {
        Option from = (Option) fromCombo.getSelectedItem();
        if (from == null) return;
        int qty = (Integer) qtySpinner.getValue();
        String note = noteField.getText().trim();

        CompletableFuture<?> future;
        String done;
        switch (mode) {
            case IN -> {
                BigDecimal cost;
                try {
                    cost = parseMoney(costField.getText());
                } catch (NumberFormatException ex) {
                    dialog.status("Birim maliyet sayı olmalı (ör. 125,50)", "icons/circle-alert.svg", "Servicio.dangerColor");
                    costField.requestFocusInWindow();
                    return;
                }
                future = stockService.receive(kind, itemId, from.id(), qty, cost, note);
                done = qty + " adet giriş " + from.name() + " deposuna yazıldı";
            }
            case OUT -> {
                ReferenceType reason = (ReferenceType) reasonCombo.getSelectedItem();
                if (reason == ReferenceType.ADJUSTMENT && note.isEmpty()) {
                    FormKit.fail(noteField, noteError, "Düzeltmenin sebebini yazın.");
                    noteField.requestFocusInWindow();
                    return;
                }
                future = stockService.issue(kind, itemId, from.id(), qty, reason, note);
                done = qty + " adet çıkış " + from.name() + " deposundan yazıldı";
            }
            case COUNT -> {
                future = stockService.count(kind, itemId, from.id(), qty, note);
                done = from.name() + " sayımı kaydedildi: " + qty + " adet";
            }
            case TRANSFER -> {
                Option to = (Option) toCombo.getSelectedItem();
                if (to == null) return;
                future = stockService.transfer(kind, itemId, from.id(), to.id(), qty, note);
                done = qty + " adet " + from.name() + " → " + to.name() + " taşındı";
            }
            default -> { return; }
        }

        dialog.busy();
        future.whenComplete((ok, ex) -> SwingUtilities.invokeLater(() -> {
            if (ex == null) {
                dialog.close();
                Toasts.show(parent, Toast.Type.SUCCESS, done);
                if (onDone != null) onDone.run();
                return;
            }
            dialog.idle();
            Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof ValidationException) {
                dialog.status(root.getMessage(), "icons/circle-alert.svg", "Servicio.dangerColor");
            } else {
                ErrorHandler.handle(dialog, "Stok hareketi kaydedilemedi", root);
            }
        }));
    }

    /** "1.250,50", "1250.5", "12" → BigDecimal; boş → null. */
    static BigDecimal parseMoney(String text) {
        String s = text == null ? "" : text.replace("₺", "").replace(" ", "").trim();
        if (s.isEmpty()) return null;
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        BigDecimal value = new BigDecimal(s);
        if (value.signum() < 0) throw new NumberFormatException("negatif");
        return value;
    }
}
