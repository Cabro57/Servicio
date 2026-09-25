package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Ayarlar &gt; Cihazlar: cihaz türleri ve her türün markaları (ana-detay).
 * <p>
 * Solda türler (marka/işçilik/cihaz sayılarıyla); seçili türün markaları sağda. Sağdaki alan hem
 * arar hem ekler: yazdıkça liste süzülür, Enter eşleşme yoksa markayı ekler (başka türde varsa aynı
 * marka bu türe bağlanır, kopya açılmaz). Marka kaydı tüm türlerde ortaktır; ad değiştirme ve
 * silme her türü etkiler, "bu türden çıkar" yalnızca seçim listesini.
 * <p>
 * Kullanımdaki tür ya da marka silinirken kayıtları başka birine taşınır (birleştirme).
 */
public class SettingsDevicePanel extends JPanel implements SettingsModal.HeaderActions {

    private final DeviceDictionaryManager dictionary = ServiceManager.getDeviceDictionaryManager();

    private final DictionaryList<DeviceType> typeList;
    private final DictionaryList<DeviceBrand> brandList;
    private final JLabel typeCount = DetailKit.small("");
    private final JLabel brandTitle = DetailKit.title("Markalar");
    private final JLabel brandMeta = DetailKit.small("");
    private final JTextField brandField = new JTextField();
    private final JLabel brandHint = DetailKit.small("");
    private final JButton addTypeButton = SettingsKit.headerButton("Yeni tür", "icons/plus.svg");

    private List<DeviceType> types = List.of();
    private List<DeviceBrand> brandsOfType = List.of();
    private List<DeviceBrand> allBrands = List.of();

    public SettingsDevicePanel() {
        typeList = new DictionaryList<>(DeviceType::getName)
                .subtitle(t -> countText(t.brands(), "marka") + "  ·  " + countText(t.labors(), "işçilik"))
                .trailing(t -> t.devices() > 0 ? t.devices() + " cihaz" : "cihaz yok", t -> t.devices() == 0)
                .onSelect(this::showType)
                .action("icons/pencil.svg", "Adını değiştir", false, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), null, this::renameType)
                .action("icons/trash-2.svg", "Sil", true, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), null, this::deleteType)
                .bindActionKeys();
        typeList.setEmptyText("Henüz tür yok", "Telefon, tablet, bilgisayar gibi türleri \"Yeni tür\" ile ekleyin.");

        brandList = new DictionaryList<>(DeviceBrand::getName)
                .subtitle(b -> b.getTypeNames() != null && !b.getTypeNames().isBlank() ? "Ayrıca: " + b.getTypeNames() : null)
                .trailing(b -> b.devices() > 0 ? b.devices() + " cihaz" : "cihaz yok", b -> b.devices() == 0)
                .onOpen(this::editBrandTypes)
                .action("icons/link-2.svg", "Hangi türlerde…", this::editBrandTypes)
                .action("icons/pencil.svg", "Adını değiştir", false, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), null, this::renameBrand)
                .action("icons/unlink-2.svg", "Bu türden çıkar", this::unlinkBrand)
                .action("icons/trash-2.svg", "Sil", true, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), null, this::deleteBrand)
                .bindActionKeys();

        initComponent();
        addTypeButton.addActionListener(e -> addType());
        reloadTypes();
        reloadAllBrands();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(addTypeButton);
    }

    // ------------------------------------------------------------------ yerleşim

    private void initComponent() {
        setLayout(new MigLayout("insets 4 24 20 24, gap 16", "[250:270:300, fill][grow, fill]", "[grow, fill]"));
        setOpaque(false);
        add(typeCard(), "grow, hmin 260");
        add(brandCard(), "grow, hmin 260, wmin 0");
    }

    private JPanel typeCard() {
        JPanel card = new JPanel(new MigLayout("insets 14 8 8 8, fill, wrap", "[grow, fill]", "[]8[grow, fill]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel header = new JPanel(new MigLayout("insets 0 8 0 8, fillx, gap 8", "[][grow]", "[baseline]"));
        header.setOpaque(false);
        header.add(DetailKit.title("Türler"));
        header.add(typeCount);
        card.add(header);
        card.add(typeList, "grow, hmin 0");
        return card;
    }

    private JPanel brandCard() {
        JPanel card = new JPanel(new MigLayout("insets 14 8 10 8, fill, wrap, hidemode 3", "[grow, fill]", "[]10[grow, fill]6[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JPanel header = new JPanel(new MigLayout("insets 0 8 0 8, fillx, gap 8, hidemode 3", "[grow, fill][200:260:300, fill]", "[]1[]"));
        header.setOpaque(false);
        header.add(brandTitle, "wmin 0");
        header.add(brandField, "spany 2, aligny center, wrap");
        header.add(brandMeta, "wmin 0");
        card.add(header);
        card.add(brandList, "grow, hmin 0");

        brandHint.setIconTextGap(6);
        card.add(brandHint, "gapleft 8, wmin 0");

        brandField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Marka ara ya da ekle");
        brandField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new Ikon("icons/search.svg", 14, "Label.disabledForeground"));
        brandField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        brandField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 3,8,3,8");
        brandField.setToolTipText("Yazdıkça süzer; Enter listede olmayan markayı bu türe ekler");
        brandField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onBrandQuery(); }
            @Override public void removeUpdate(DocumentEvent e) { onBrandQuery(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        brandField.addActionListener(e -> addBrandFromField());
        // ↓ aramadan listeye geçer; Esc önce aramayı temizler.
        brandField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "toList");
        brandField.getActionMap().put("toList", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { brandList.requestFocusInWindow(); }
        });
        brandField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        brandField.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { brandField.setText(""); }
            @Override public boolean accept(Object sender) { return !brandField.getText().isEmpty(); }
        });
        return card;
    }

    // ------------------------------------------------------------------ veri

    private void reloadTypes() {
        dictionary.getAllTypes().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            types = list;
            typeCount.setText(list.isEmpty() ? "" : String.valueOf(list.size()));
            typeList.setItems(list);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türleri yüklenemedi", ex));
    }

    private void reloadAllBrands() {
        dictionary.getAllBrands().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            allBrands = list;
            onBrandQuery();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Markalar yüklenemedi", ex));
    }

    /** Bir değişiklikten sonra: sayılar ve listeler yeniden; seçili tür korunur, markaları da yenilenir. */
    private void reloadAll() {
        reloadTypes();
        reloadAllBrands();
        SettingsKit.saved(this);
    }

    private void showType(DeviceType type) {
        boolean has = type != null;
        brandField.setEnabled(has);
        if (!has) {
            brandTitle.setText("Markalar");
            brandMeta.setText("Soldan bir tür seçin.");
            brandsOfType = List.of();
            brandList.setEmptyText("Tür seçilmedi", "Markalar türe göre listelenir.");
            brandList.setItems(List.of());
            onBrandQuery();
            return;
        }
        brandTitle.setText(type.getName() + " markaları");
        brandMeta.setText(countText(type.brands(), "marka") + "  ·  " + countText(type.devices(), "cihaz kaydı"));
        brandList.setEmptyText(type.getName() + " için marka yok",
                "Yukarıdaki alana marka adını yazıp Enter'a basın. Başka türde varsa aynı marka bağlanır.");
        dictionary.getBrandsByTypeId(type.getId()).thenAccept(list -> SwingUtilities.invokeLater(() -> {
            if (!type.equals(typeList.getSelected())) return;
            brandsOfType = list;
            brandList.setItems(list);
            onBrandQuery();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Markalar yüklenemedi", ex));
    }

    // ------------------------------------------------------------------ ara ya da ekle

    /** Alan her değiştiğinde: listeyi süzer ve Enter'ın ne yapacağını alt satırda söyler. */
    private void onBrandQuery() {
        String query = brandField.getText().trim();
        brandList.filter(query);
        DeviceType type = typeList.getSelected();
        if (query.isEmpty() || type == null) {
            brandHint.setIcon(null);
            brandHint.setText("Marka kaydı tüm türlerde ortaktır: adı değişince her türde değişir.");
            return;
        }
        brandHint.setIcon(new Ikon("icons/plus.svg", 13, "Label.disabledForeground"));
        if (find(brandsOfType, query).isPresent()) {
            brandHint.setIcon(null);
            brandHint.setText("\"" + find(brandsOfType, query).get().getName() + "\" bu türde zaten var.");
        } else if (find(allBrands, query).isPresent()) {
            DeviceBrand existing = find(allBrands, query).get();
            brandHint.setText("Enter: \"" + existing.getName() + "\" markasını " + type.getName() + " türüne bağla"
                    + (existing.getTypeNames() != null ? " (" + existing.getTypeNames() + " türünde kayıtlı)" : ""));
        } else {
            brandHint.setText("Enter: \"" + query + "\" markasını " + type.getName() + " türüne ekle");
        }
    }

    private void addBrandFromField() {
        DeviceType type = typeList.getSelected();
        String name = brandField.getText().trim();
        if (type == null || name.isEmpty()) return;
        Optional<DeviceBrand> inType = find(brandsOfType, name);
        if (inType.isPresent()) {
            brandList.select(inType.get());
            brandList.requestFocusInWindow();
            return;
        }
        dictionary.addBrandToType(type.getId(), name).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            brandField.setText("");
            Toasts.show(this, Toast.Type.SUCCESS, "\"" + name + "\" " + type.getName() + " türüne eklendi.");
            reloadAll();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Marka eklenemedi", ex));
    }

    private static Optional<DeviceBrand> find(List<DeviceBrand> list, String name) {
        String key = DictionaryList.normalize(name);
        return list.stream().filter(b -> DictionaryList.normalize(b.getName()).equals(key)).findFirst();
    }

    // ------------------------------------------------------------------ tür eylemleri

    private void addType() {
        DictionaryDialogs.<DeviceType>name(this, "Yeni tür",
                "Arıza kaydında ilk seçilen cihaz türü. Markalar ve hazır işçilikler türe göre listelenir.",
                "Tür adı", null, null, null,
                name -> dictionary.addType(name),
                this::reloadAll);
    }

    private void renameType(DeviceType type) {
        DictionaryDialogs.name(this, "Türün adını değiştir", null, "Tür adı", type.getName(),
                name -> types.stream().filter(t -> !t.equals(type) && DictionaryList.normalize(t.getName())
                        .equals(DictionaryList.normalize(name))).findFirst().orElse(null),
                other -> mergeType(type, other),
                name -> dictionary.renameType(type.getId(), name),
                this::reloadAll);
    }

    private void deleteType(DeviceType type) {
        DictionaryDialogs.delete(this, "tür", type, typeImpacts(type), types, null, null,
                target -> dictionary.deleteType(type.getId(), target != null ? target.getId() : null),
                this::reloadAll);
    }

    private void mergeType(DeviceType type, DeviceType into) {
        DictionaryDialogs.delete(this, "tür", type, typeImpacts(type), types, null, into,
                target -> dictionary.deleteType(type.getId(), target.getId()),
                this::reloadAll);
    }

    private static List<DictionaryDialogs.Impact> typeImpacts(DeviceType t) {
        List<DictionaryDialogs.Impact> impacts = new ArrayList<>();
        if (t.devices() > 0) impacts.add(new DictionaryDialogs.Impact("icons/tablet-smartphone.svg", t.devices() + " cihaz kaydı"));
        if (t.labors() > 0) impacts.add(new DictionaryDialogs.Impact("icons/wrench.svg", t.labors() + " hazır işçilik"));
        if (t.brands() > 0) impacts.add(new DictionaryDialogs.Impact("icons/tag.svg", t.brands() + " marka bağlantısı"));
        return impacts;
    }

    // ------------------------------------------------------------------ marka eylemleri

    private void editBrandTypes(DeviceBrand brand) {
        dictionary.getTypeIdsOfBrand(brand.getId()).thenAccept(ids -> SwingUtilities.invokeLater(() ->
                DictionaryDialogs.brandTypes(this, brand, types, new HashSet<>(ids),
                        typeId -> {
                            DeviceType selected = typeList.getSelected();
                            // Sayı yalnızca seçili tür için biliniyor; diğer türlerde uyarı gerekmez (0).
                            return selected != null && selected.getId().equals(typeId) ? brand.devices() : 0;
                        },
                        selected -> dictionary.setBrandTypes(brand.getId(), selected),
                        this::reloadAll)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Marka türleri yüklenemedi", ex));
    }

    private void renameBrand(DeviceBrand brand) {
        DictionaryDialogs.name(this, "Markanın adını değiştir",
                "Marka kaydı ortaktır; yeni ad bu markanın bağlı olduğu her türde görünür.",
                "Marka adı", brand.getName(),
                name -> allBrands.stream().filter(b -> !b.equals(brand) && DictionaryList.normalize(b.getName())
                        .equals(DictionaryList.normalize(name))).findFirst().orElse(null),
                other -> mergeBrand(brand, other),
                name -> dictionary.renameBrand(brand.getId(), name),
                this::reloadAll);
    }

    private void unlinkBrand(DeviceBrand brand) {
        DeviceType type = typeList.getSelected();
        if (type == null) return;
        dictionary.unlinkBrandFromType(type.getId(), brand.getId()).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            Toasts.show(this, Toast.Type.INFO, "\"" + brand.getName() + "\" " + type.getName() + " listesinden çıkarıldı"
                    + (brand.devices() > 0 ? "; " + brand.devices() + " cihaz markasını korur." : "."));
            reloadAll();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Marka türden çıkarılamadı", ex));
    }

    private void deleteBrand(DeviceBrand brand) {
        DeviceBrand global = allBrands.stream().filter(b -> b.equals(brand)).findFirst().orElse(brand);
        DictionaryDialogs.delete(this, "marka", global, brandImpacts(global), allBrands, null, null,
                target -> dictionary.deleteBrand(brand.getId(), target != null ? target.getId() : null),
                this::reloadAll);
    }

    private void mergeBrand(DeviceBrand brand, DeviceBrand into) {
        DeviceBrand global = allBrands.stream().filter(b -> b.equals(brand)).findFirst().orElse(brand);
        DictionaryDialogs.delete(this, "marka", global, brandImpacts(global), allBrands, null, into,
                target -> dictionary.deleteBrand(brand.getId(), target.getId()),
                this::reloadAll);
    }

    private static List<DictionaryDialogs.Impact> brandImpacts(DeviceBrand b) {
        List<DictionaryDialogs.Impact> impacts = new ArrayList<>();
        if (b.devices() > 0) impacts.add(new DictionaryDialogs.Impact("icons/tablet-smartphone.svg", b.devices() + " cihaz kaydı (tüm türler)"));
        if (b.getTypeNames() != null && !b.getTypeNames().isBlank()) {
            impacts.add(new DictionaryDialogs.Impact("icons/link-2.svg", "Bağlı türler: " + b.getTypeNames()));
        }
        return impacts;
    }

    private static String countText(int count, String noun) {
        return count == 0 ? noun + " yok" : count + " " + noun;
    }
}
