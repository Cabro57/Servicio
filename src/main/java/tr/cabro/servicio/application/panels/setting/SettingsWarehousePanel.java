package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.MessageModal;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.StockLevel;
import tr.cabro.servicio.model.Warehouse;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.StockService;
import tr.cabro.servicio.service.WarehouseService;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Ayarlar &gt; Depolar (ana-detay): stoğun durduğu yerler. Solda depolar (kalem/adet sayısıyla),
 * sağda seçili deponun içeriği — "bu parça nerede?" sorusunun cevabı.
 * <p>
 * Kurallar servis katmanında (WarehouseService): tek varsayılan depo, stoklu depo pasife alınamaz,
 * geçmişi olan depo silinemez. Satırdaki eylemler yalnızca geçerli olduklarında görünür.
 */
public class SettingsWarehousePanel extends JPanel implements SettingsModal.HeaderActions {

    private final WarehouseService warehouses = ServiceManager.getWarehouseService();
    private final StockService stock = ServiceManager.getStockService();

    private final DictionaryList<Warehouse> warehouseList;
    private final DictionaryList<StockLevel> contentList;
    private final JLabel warehouseCount = DetailKit.small("");
    private final JLabel contentTitle = DetailKit.title("İçerik");
    private final JLabel contentMeta = DetailKit.small("");
    private final JTextField search = new JTextField();
    private final JButton addButton = SettingsKit.headerButton("Yeni depo", "icons/plus.svg");

    private List<Warehouse> all = List.of();
    private Long selectedId;
    private int contentToken;

    public SettingsWarehousePanel() {
        warehouseList = new DictionaryList<>(Warehouse::getName)
                .subtitle(SettingsWarehousePanel::subtitle)
                .trailing(w -> w.getItemCount() > 0 ? w.getTotalQuantity() + " adet" : "boş", w -> w.getItemCount() == 0)
                .onSelect(this::showContents)
                .action("icons/pencil.svg", "Düzenle", false, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), null, this::edit)
                .action("icons/star.svg", "Varsayılan yap", false, null, w -> !w.isDefaultWarehouse() && w.isActive(), this::makeDefault)
                .action("icons/archive.svg", "Pasife al", false, null, w -> w.isActive() && !w.isDefaultWarehouse(), w -> setActive(w, false))
                .action("icons/archive-restore.svg", "Yeniden etkinleştir", false, null, w -> !w.isActive(), w -> setActive(w, true))
                .action("icons/trash-2.svg", "Sil", true, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                        w -> !w.isDefaultWarehouse() && w.getMovementCount() == 0, this::delete)
                .bindActionKeys();
        warehouseList.setEmptyText("Depo yok", "Stoğun durduğu yerleri \"Yeni depo\" ile ekleyin.");

        contentList = new DictionaryList<>(StockLevel::getItemName)
                .subtitle(l -> ("PART".equals(l.getItemKind()) ? "Parça" : "Ürün") + "  ·  " + l.getBarcode())
                .trailing(l -> l.getQuantity() + " adet", l -> false);
        contentList.setEmptyText("Bu depo boş", "Kart sayfasındaki Stok Hareketi ile giriş ya da transfer yapıldığında burada görünür.");

        initComponent();
        addButton.addActionListener(e -> edit(null));
        reload();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(addButton);
    }

    // ------------------------------------------------------------------ yerleşim

    private void initComponent() {
        setLayout(new MigLayout("insets 4 24 20 24, gap 16", "[300:340:380, fill][grow, fill]", "[grow, fill]"));
        setOpaque(false);

        JPanel left = new JPanel(new MigLayout("insets 14 8 8 8, fill, wrap", "[grow, fill]", "[]8[grow, fill]"));
        left.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel leftHeader = new JPanel(new MigLayout("insets 0 8 0 8, fillx, gap 8", "[][grow]", "[baseline]"));
        leftHeader.setOpaque(false);
        leftHeader.add(DetailKit.title("Depolar"));
        leftHeader.add(warehouseCount);
        left.add(leftHeader);
        left.add(warehouseList, "grow, hmin 0");
        add(left, "grow, hmin 260");

        JPanel right = new JPanel(new MigLayout("insets 14 8 8 8, fill, wrap", "[grow, fill]", "[]10[grow, fill]"));
        right.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel rightHeader = new JPanel(new MigLayout("insets 0 8 0 8, fillx, gap 8", "[grow, fill][200:240:280, fill]", "[]1[]"));
        rightHeader.setOpaque(false);
        rightHeader.add(contentTitle, "wmin 0");
        rightHeader.add(search, "spany 2, aligny center, wrap");
        rightHeader.add(contentMeta, "wmin 0");
        right.add(rightHeader);
        right.add(contentList, "grow, hmin 0");
        add(right, "grow, hmin 260, wmin 0");

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Depoda ara");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new Ikon("icons/search.svg", 14, "Label.disabledForeground"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 3,8,3,8");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { contentList.filter(search.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { contentList.filter(search.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    /** "Varsayılan · 31 kalem · arka oda" — durum, kalem sayısı ve konum notu. */
    private static String subtitle(Warehouse w) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (w.isDefaultWarehouse()) parts.add("Varsayılan");
        else if (!w.isActive()) parts.add("Pasif");
        if (w.getItemCount() > 0) parts.add(w.getItemCount() + " kalem");
        if (w.getDescription() != null && !w.getDescription().isBlank()) parts.add(w.getDescription().trim());
        return parts.isEmpty() ? null : String.join("  ·  ", parts);
    }

    // ------------------------------------------------------------------ veri

    private void reload() {
        warehouses.getAllWithStats().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            all = list;
            long active = list.stream().filter(Warehouse::isActive).count();
            warehouseCount.setText(active == list.size() ? list.size() + " depo" : active + " aktif, " + (list.size() - active) + " pasif");
            warehouseList.setItems(list);
            Warehouse keep = list.stream().filter(w -> Objects.equals(w.getId(), selectedId)).findFirst()
                    .orElse(list.isEmpty() ? null : list.get(0));
            if (keep != null) warehouseList.select(keep);
            showContents(keep);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Depolar yüklenemedi", ex));
    }

    private void showContents(Warehouse w) {
        int token = ++contentToken;
        selectedId = w != null ? w.getId() : null;
        contentTitle.setText(w != null ? w.getName() : "İçerik");
        contentMeta.setText(w == null ? "" : w.getItemCount() == 0 ? "Stok yok"
                : w.getItemCount() + " kalem  ·  " + w.getTotalQuantity() + " adet  ·  alış değeri " + Format.formatPrice(w.getStockValue()));
        if (w == null) {
            contentList.setItems(List.of());
            return;
        }
        stock.getWarehouseContents(w.getId()).thenAccept(items -> SwingUtilities.invokeLater(() -> {
            if (token != contentToken) return;
            contentList.setItems(items);
            contentList.filter(search.getText());
        })).exceptionally(ex -> ErrorHandler.handle(this, "Depo içeriği yüklenemedi", ex));
    }

    private void done() {
        reload();
        SettingsKit.saved(this);
    }

    // ------------------------------------------------------------------ eylemler

    /** Yeni depo ({@code w == null}) ya da ad/açıklama düzenleme. */
    private void edit(Warehouse w) {
        SettingsDialog d = new SettingsDialog(w == null ? "Yeni depo" : "Depoyu düzenle", 440);
        d.lead("Stoğun durduğu yer: depo, vitrin, raf, servis aracı… Açıklamaya yerini yazın.");
        JTextField name = new JTextField(w != null ? w.getName() : "");
        JTextField description = new JTextField(w != null && w.getDescription() != null ? w.getDescription() : "");
        description.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. arka oda, 2. raf");
        JLabel nameError = FormKit.errorLabel();
        d.body().add(FormKit.cell("Ad", name, nameError), "span 2");
        d.body().add(FormKit.cell("Açıklama", description, null), "span 2");
        d.primary(w == null ? "Ekle" : "Kaydet", false, () -> {
            if (name.getText().trim().isEmpty()) {
                FormKit.fail(name, nameError, "Depo adını yazın.");
                return;
            }
            Warehouse target = new Warehouse();
            if (w != null) {
                target.setId(w.getId());
                target.setSortOrder(w.getSortOrder());
            }
            target.setName(name.getText());
            target.setDescription(description.getText());
            run(d, warehouses.save(target).thenAccept(saved -> selectedId = saved.getId()), name, nameError);
        });
        d.show(this, name);
    }

    private void makeDefault(Warehouse w) {
        warehouses.setDefault(w.getId())
                .thenRun(() -> SwingUtilities.invokeLater(this::done))
                .exceptionally(ex -> fail(ex, "Varsayılan depo değiştirilemedi"));
    }

    private void setActive(Warehouse w, boolean active) {
        warehouses.setActive(w.getId(), active)
                .thenRun(() -> SwingUtilities.invokeLater(this::done))
                .exceptionally(ex -> fail(ex, "Depo durumu değiştirilemedi"));
    }

    private void delete(Warehouse w) {
        MessageModal.of(MessageModal.Tone.DANGER, "Depo silinsin mi?",
                        "\"" + w.getName() + "\" hiç kullanılmadı; silinince listeden kalkar.")
                .primary("Sil", () -> warehouses.delete(w.getId())
                        .thenRun(() -> SwingUtilities.invokeLater(() -> {
                            selectedId = null;
                            done();
                        }))
                        .exceptionally(ex -> fail(ex, "Depo silinemedi")))
                .show(this);
    }

    /** Kural ihlali (stoklu depoyu pasife alma vb.) bilgi penceresiyle anlatılır, diğerleri hata akışına gider. */
    private Void fail(Throwable ex, String context) {
        Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        SwingUtilities.invokeLater(() -> {
            if (root instanceof ValidationException) {
                MessageModal.of(MessageModal.Tone.WARNING, "Yapılamadı", root.getMessage()).single().show(this);
            } else {
                ErrorHandler.handle(this, context, root);
            }
        });
        return null;
    }

    private void run(SettingsDialog d, CompletableFuture<?> future, JComponent field, JLabel error) {
        d.busy();
        future.whenComplete((ok, ex) -> SwingUtilities.invokeLater(() -> {
            if (ex == null) {
                d.close();
                done();
                return;
            }
            d.idle();
            Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof ValidationException) FormKit.fail(field, error, root.getMessage());
            else ErrorHandler.handle(d, "Depo kaydedilemedi", root);
        }));
    }
}
