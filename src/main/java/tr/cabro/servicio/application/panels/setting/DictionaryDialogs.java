package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.service.exception.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Ayarlar &gt; Sözlükler sayfalarının pencereleri. Hepsi {@link SettingsDialog} kabuğunu kullanır.
 * <p>
 * Kayıt işlemleri {@code CompletableFuture} döndürür: iş bitince pencere kapanır ve {@code onDone}
 * çalışır; doğrulama hatası (aynı ad, boş ad) pencereyi kapatmaz, ilgili alanın altında gösterilir.
 */
final class DictionaryDialogs {

    private DictionaryDialogs() {}

    // ================================================================== ad

    /**
     * Tek alanlı ad penceresi (yeni tür, ad değiştir). {@code save} verilen adla işi yapar.
     * {@code conflict} aynı adda başka bir kayıt varsa onu döndürür: pencere o zaman kaydetmek
     * yerine birleştirmeyi önerir ({@code onMerge}).
     */
    static <T> void name(Component parent, String title, String lead, String label, String initial,
                         Function<String, T> conflict, java.util.function.Consumer<T> onMerge,
                         Function<String, CompletableFuture<?>> save, Runnable onDone) {
        SettingsDialog d = new SettingsDialog(title, 420);
        if (lead != null) d.lead(lead);
        JTextField field = new JTextField(initial != null ? initial : "");
        JLabel error = FormKit.errorLabel();
        d.body().add(FormKit.cell(label, field, error), "span 2");

        d.primary(initial == null ? "Ekle" : "Kaydet", false, () -> {
            String name = field.getText().trim();
            if (name.isEmpty()) {
                FormKit.fail(field, error, "Ad boş olamaz.");
                return;
            }
            if (initial != null && name.equals(initial.trim())) {
                d.close();
                return;
            }
            // Aynı adda başka kayıt varsa ikinci bir kopya açmak yerine birleştirme penceresine geçilir.
            T other = conflict != null ? conflict.apply(name) : null;
            if (other != null && onMerge != null) {
                d.close();
                onMerge.accept(other);
                return;
            }
            run(d, save.apply(name), field, error, onDone);
        });
        d.show(parent, field);
    }

    // ================================================================== taşı / birleştir / sil

    /** Silinecek kaydın etkisi: bir satır ("212 cihaz kaydı") ve ikonu. */
    record Impact(String icon, String text) {}

    /**
     * Sil ya da birleştir penceresi.
     * <ul>
     *   <li>Kayıt kullanılmıyorsa ({@code impacts} boş): kısa onay, tehlike rengiyle "Sil".</li>
     *   <li>Kullanılıyorsa: etki satırları + "Nereye taşınsın?" listesi, "Taşı ve sil".
     *       {@code noneLabel} verilirse ("Kategorisiz bırak") taşımadan silmek de seçilebilir.</li>
     *   <li>{@code mergeInto} verilirse birleştirme kipidir: hedef seçili gelir, düğme "Birleştir".</li>
     * </ul>
     * {@code action} hedefle (ya da null) işi yapar.
     */
    static <T> void delete(Component parent, String noun, T item, List<Impact> impacts, List<T> targets,
                           String noneLabel, T mergeInto, Function<T, CompletableFuture<?>> action, Runnable onDone) {
        boolean merge = mergeInto != null;
        boolean used = !impacts.isEmpty();
        String name = String.valueOf(item);
        SettingsDialog d = new SettingsDialog(merge ? "Birleştir" : "\"" + name + "\" silinsin mi?", 460);

        JComboBox<Object> target = new JComboBox<>();
        if (!used && !merge) {
            d.lead("Bu " + noun + " hiçbir kayıtta kullanılmıyor. Silinince seçim listelerinden kalkar.");
        } else {
            d.lead(merge
                    ? "\"" + name + "\" kayıtları seçtiğiniz " + noun + " altına taşınır ve \"" + name + "\" silinir."
                    : "\"" + name + "\" kullanımda. Silmeden önce kayıtlarının nereye gideceğini seçin.");
            JPanel facts = new JPanel(new MigLayout("wrap, insets 0, gap 0 8", "[grow, fill]"));
            facts.setOpaque(false);
            for (Impact impact : impacts) facts.add(SettingsDialog.impact(impact.icon(), impact.text()));
            if (!impacts.isEmpty()) d.body().add(facts, "span 2, gapbottom 4");

            if (noneLabel != null) target.addItem(noneLabel);
            for (T t : targets) if (!t.equals(item)) target.addItem(t);
            if (merge) target.setSelectedItem(mergeInto);
            d.body().add(FormKit.cell("Nereye taşınsın?", target, null), "span 2");
        }

        String verb = merge ? "Birleştir" : used ? "Taşı ve sil" : "Sil";
        d.primary(verb, true, () -> {
            Object chosen = used || merge ? target.getSelectedItem() : null;
            @SuppressWarnings("unchecked")
            T t = chosen == null || chosen == noneLabel ? null : (T) chosen;
            if ((used || merge) && noneLabel == null && t == null) {
                d.status("Taşınacak bir " + noun + " seçin.", "icons/circle-alert.svg", "Servicio.dangerColor");
                return;
            }
            run(d, action.apply(t), null, null, onDone);
        });
        d.status(used && !merge ? "Bu işlem geri alınamaz." : null, null, null);
        if ((used || merge) && target.getItemCount() == 0) {
            d.setPrimaryEnabled(false);
            d.status("Taşınacak başka " + noun + " yok; önce bir tane ekleyin.", "icons/circle-alert.svg", "Servicio.warningColor");
        }
        d.show(parent, used || merge ? target : null);
    }

    /** Basit, geri alınamaz onay (kullanım bilgisi gerektirmeyen kayıtlar: işçilik, şablon). */
    static void confirm(Component parent, String title, String lead, String verb,
                        java.util.function.Supplier<CompletableFuture<?>> action, Runnable onDone) {
        SettingsDialog d = new SettingsDialog(title, 420);
        d.lead(lead);
        d.primary(verb, true, () -> run(d, action.get(), null, null, onDone));
        d.show(parent, null);
    }

    // ================================================================== marka türleri

    /**
     * Markanın hangi türlerde seçilebildiği. İşaret kaldırılan türde cihazı olan marka için
     * uyarı gösterilir (cihaz kayıtları değişmez; marka yalnızca o türün listesinden çıkar).
     */
    static void brandTypes(Component parent, DeviceBrand brand, List<DeviceType> types, Set<Long> linked,
                           Function<Long, Integer> devicesInType,
                           Function<Set<Long>, CompletableFuture<?>> save, Runnable onDone) {
        SettingsDialog d = new SettingsDialog("\"" + brand.getName() + "\" hangi türlerde?", 440);
        d.lead("Marka işaretli türlerin arıza kaydında seçilebilir. İşareti kaldırmak cihaz kayıtlarını değiştirmez.");
        JPanel list = new JPanel(new MigLayout("wrap 2, insets 0, fillx, gap 12 6", "[grow, fill][right]"));
        list.setOpaque(false);
        Map<Long, JCheckBox> boxes = new LinkedHashMap<>();
        for (DeviceType type : types) {
            JCheckBox box = new JCheckBox(type.getName(), linked.contains(type.getId()));
            boxes.put(type.getId(), box);
            int count = devicesInType.apply(type.getId());
            JLabel meta = new JLabel(count > 0 ? count + " cihaz" : "");
            meta.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            list.add(box);
            list.add(meta);
            box.addActionListener(e -> {
                long orphaned = boxes.entrySet().stream()
                        .filter(en -> !en.getValue().isSelected() && devicesInType.apply(en.getKey()) > 0).count();
                d.status(orphaned > 0 ? "Cihazı olan türden kaldırılıyor; o cihazlar markasını korur." : null,
                        "icons/info.svg", "Servicio.warningColor");
            });
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        d.body().add(scroll, "span 2, hmax 300");

        d.primary("Kaydet", false, () -> {
            Set<Long> selected = new LinkedHashSet<>();
            boxes.forEach((id, box) -> { if (box.isSelected()) selected.add(id); });
            run(d, save.apply(selected), null, null, onDone);
        });
        d.show(parent, boxes.isEmpty() ? null : boxes.values().iterator().next());
    }

    // ================================================================== kategori

    /** Kategori ekle / düzenle: ad ve kapsam (Parça / Ürün / İkisi). */
    static void category(Component parent, PartCategory category, CategoryScope defaultScope,
                         BiFunction<String, CategoryScope, CompletableFuture<?>> save, Runnable onDone) {
        boolean editing = category != null;
        SettingsDialog d = new SettingsDialog(editing ? "Kategoriyi düzenle" : "Yeni kategori", 440);
        d.lead("Kapsam, kategorinin hangi ekranda seçilebileceğini belirler.");

        JTextField name = new JTextField(editing ? category.getName() : "");
        JLabel nameError = FormKit.errorLabel();
        d.body().add(FormKit.cell("Ad", name, nameError), "span 2");

        SegmentedButtons<CategoryScope> scope = new SegmentedButtons<>();
        scope.add(CategoryScope.PART, "Parça", "icons/circuit-board.svg");
        scope.add(CategoryScope.PRODUCT, "Ürün", "icons/shopping-bag.svg");
        scope.add(CategoryScope.BOTH, "İkisi de", null);
        scope.setSelected(editing && category.getScope() != null ? category.getScope()
                : defaultScope != null ? defaultScope : CategoryScope.BOTH);
        JLabel scopeNote = FormKit.note("");
        JPanel scopeCell = FormKit.cell("Kapsam", wrapLeft(scope), scopeNote);
        d.body().add(scopeCell, "span 2");

        Runnable explain = () -> {
            CategoryScope s = scope.getSelected();
            String text = switch (s) {
                case PART -> "Parça ve servis kalemi ekranlarında seçilir.";
                case PRODUCT -> "Ürün ve satış (POS) ekranlarında seçilir.";
                case BOTH -> "Parça ve ürün ekranlarının ikisinde de seçilir.";
            };
            if (editing) {
                int lost = s == CategoryScope.PART ? category.products() : s == CategoryScope.PRODUCT ? category.parts() : 0;
                if (lost > 0) {
                    text = lost + (s == CategoryScope.PART ? " ürün" : " parça")
                            + " bu kategoride kalır ama yeni kayıtlarda seçilemez.";
                }
            }
            scopeNote.setText(text);
        };
        scope.setOnChange(s -> explain.run());
        explain.run();

        d.primary(editing ? "Kaydet" : "Ekle", false, () -> {
            if (name.getText().trim().isEmpty()) {
                FormKit.fail(name, nameError, "Ad boş olamaz.");
                return;
            }
            run(d, save.apply(name.getText().trim(), scope.getSelected()), name, nameError, onDone);
        });
        d.show(parent, name);
    }

    // ================================================================== işçilik

    /** Hazır işçilik ekle / düzenle. {@code labor.id} null ise yeni kayıttır. */
    static void labor(Component parent, Labor labor, List<DeviceType> types,
                      Function<Labor, CompletableFuture<?>> save, Runnable onDone) {
        boolean editing = labor.getId() != null;
        SettingsDialog d = new SettingsDialog(editing ? "İşçiliği düzenle" : "Yeni işçilik", 500);
        d.lead("Servis kaydına kalem eklerken cihaz türüne göre listelenir; fiyat eklerken değiştirilebilir.");

        JTextField name = new JTextField(labor.getName() != null ? labor.getName() : "");
        name.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. Ekran değişimi");
        JLabel nameError = FormKit.errorLabel();
        d.body().add(FormKit.cell("Ad", name, nameError), "span 2");

        DeviceType general = new DeviceType(null, "Genel (tüm türler)", 0);
        JComboBox<DeviceType> type = new JComboBox<>();
        type.addItem(general);
        types.forEach(type::addItem);
        type.setSelectedItem(general);
        for (DeviceType t : types) if (t.getId().equals(labor.getDeviceTypeId())) type.setSelectedItem(t);
        d.body().add(FormKit.cell("Cihaz türü", type, null));

        CurrencyField price = new CurrencyField();
        price.setValue(labor.getDefaultPrice() != null ? labor.getDefaultPrice().doubleValue() : 0.0);
        price.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel priceError = FormKit.errorLabel();
        d.body().add(FormKit.cell("Varsayılan ücret", price, priceError));

        JTextArea description = FormKit.textArea(3);
        description.setText(labor.getDescription() != null ? labor.getDescription() : "");
        d.body().add(FormKit.cell("Açıklama", FormKit.areaScroll(description), FormKit.note("İsteğe bağlı; listede adın altında görünür.")), "span 2");

        d.primary(editing ? "Kaydet" : "Ekle", false, () -> {
            if (name.getText().trim().isEmpty()) {
                FormKit.fail(name, nameError, "Ad boş olamaz.");
                return;
            }
            try {
                price.commitEdit();
            } catch (ParseException ex) {
                FormKit.fail(price, priceError, "Geçerli bir tutar girin.");
                return;
            }
            if (price.getDoubleValue() < 0) {
                FormKit.fail(price, priceError, "Ücret negatif olamaz.");
                return;
            }
            Labor result = new Labor();
            result.setId(labor.getId());
            result.setName(name.getText().trim());
            result.setDescription(description.getText().trim());
            result.setCategory(labor.getCategory());
            result.setCreatedAt(labor.getCreatedAt());
            result.setDefaultPrice(BigDecimal.valueOf(price.getDoubleValue()));
            DeviceType selected = (DeviceType) type.getSelectedItem();
            result.setDeviceTypeId(selected != null ? selected.getId() : null);
            run(d, save.apply(result), name, nameError, onDone);
        });
        d.show(parent, name);
    }

    // ================================================================== ortak

    /**
     * İşi çalıştırır: sürerken birincil eylem kilitli; başarıda pencere kapanır ve {@code onDone};
     * doğrulama hatasında pencere açık kalır, mesaj alanın altında (alan yoksa alt satırda).
     */
    private static void run(SettingsDialog d, CompletableFuture<?> future, JComponent field, JLabel error, Runnable onDone) {
        d.busy();
        future.whenComplete((ok, ex) -> SwingUtilities.invokeLater(() -> {
            if (ex == null) {
                d.close();
                if (onDone != null) onDone.run();
                return;
            }
            d.idle();
            Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof ValidationException) {
                if (field != null && error != null) FormKit.fail(field, error, root.getMessage());
                else d.status(root.getMessage(), "icons/circle-alert.svg", "Servicio.dangerColor");
            } else {
                ErrorHandler.handle(d, "Sözlük kaydı yapılamadı", root);
            }
        }));
    }

    /** Parçalı düğmeyi hücre genişliğine yaymadan sola yaslar. */
    private static JComponent wrapLeft(JComponent c) {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 0", "[]push", "[]"));
        p.setOpaque(false);
        p.add(c);
        return p;
    }
}
