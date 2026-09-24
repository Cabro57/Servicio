package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.ActionButtonEditor;
import tr.cabro.servicio.application.component.table.TableActionEvent;
import tr.cabro.servicio.application.panels.ProcessEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.LaborService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.util.Format;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Tamirler (hazır işçilik kalemleri): üstte cihaz türü sekmeleri (sayılı), altında işçilik satırları —
 * kalın ad, altında tür ve açıklama, sağda varsayılan fiyat; düzenle/sil satırın üstüne gelince
 * görünür, satıra tıklamak düzenler. Eski ekran açılır liste süzgeci + her satırda üç ikonlu tabloydu.
 */
public class SettingsRepairPanel extends JPanel implements SettingsModal.HeaderActions {

    private static final String ALL = "all";

    private final LaborService laborService;
    private final ViewTabs views = new ViewTabs();
    private DictionaryList<Labor> list;
    private List<Labor> all = List.of();
    private final java.util.List<String> typeKeys = new java.util.ArrayList<>();
    private JButton add_button;

    public SettingsRepairPanel() {
        laborService = ServiceManager.getLaborService();
        initComponent();

        views.addView(ALL, "Tümü");
        views.addView("general", "Genel");
        views.setOnChange(k -> applyView());
        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            for (DeviceType t : types) {
                views.addView("type:" + t.getId(), t.getName());
                typeKeys.add("type:" + t.getId());
            }
            views.revalidate();
            updateCounts();
        }));

        add_button.addActionListener(e -> setupEditModal("LABOR_ADD", false, new Labor()));
        refreshTable();
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 4 24 20 24, gapy 10", "[grow, fill]", "[][grow, fill]"));
        setOpaque(false);
        add_button = SettingsKit.headerButton("Yeni işçilik", "icons/plus.svg");

        list = new DictionaryList<Labor>(Labor::getName, SettingsRepairPanel::subtitle,
                l -> l.getDefaultPrice() != null ? Format.formatPrice(l.getDefaultPrice()) : "fiyat yok")
                .onSelect(l -> setupEditModal("LABOR_EDIT", true, l))
                .onEdit(l -> setupEditModal("LABOR_EDIT", true, l))
                .onDelete(this::delete);
        list.setEmptyText("Bu türde işçilik yok", "Sık yaptığınız işleri fiyatıyla ekleyin; servis kaydında tek tıkla seçilir.");

        add(views, "wmin 0, wrap");
        add(list, "grow, hmin 160");
    }

    private static String subtitle(Labor l) {
        String type = l.getDeviceTypeId() == null ? "Genel (tüm cihazlar)" : l.getDeviceTypeName();
        String desc = l.getDescription();
        return desc != null && !desc.isBlank() ? type + "  ·  " + desc : type;
    }

    private void refreshTable() {
        laborService.getAll().thenAccept(labors -> SwingUtilities.invokeLater(() -> {
            all = labors;
            updateCounts();
            applyView();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşçilik listesi yenilenemedi", ex));
    }

    private boolean matches(Labor l, String key) {
        if (key == null || ALL.equals(key)) return true;
        if ("general".equals(key)) return l.getDeviceTypeId() == null;
        return l.getDeviceTypeId() != null && key.equals("type:" + l.getDeviceTypeId());
    }

    private void updateCounts() {
        views.setCount(ALL, (long) all.size());
        views.setCount("general", all.stream().filter(l -> matches(l, "general")).count());
        for (String key : typeKeys) views.setCount(key, all.stream().filter(x -> matches(x, key)).count());
    }

    private void applyView() {
        String key = views.getSelected();
        list.setItems(all.stream().filter(l -> matches(l, key)).collect(java.util.stream.Collectors.toList()), null);
    }

    private void delete(Labor l) {
        DialogHelper.confirmDelete(this, "confirm.delete.labor", () ->
                        laborService.delete(l.getId()).thenAccept(response -> SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.labor.deleted"));
                            refreshTable();
                        })).exceptionally(ex -> ErrorHandler.handle(this, "İşçilik silinemedi", ex)),
                l.getName());
    }

    private void setupEditModal(String id, boolean updated, Labor labor) {
        ProcessEditPanel panel = new ProcessEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(
                panel, updated ? "İşçilik Düzenle" : "Yeni İşçilik", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.formOpen();
                        panel.formFill(labor);

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        Labor newLabor = panel.getLabor();

                        laborService.save(newLabor, updated)
                                .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                                    refreshTable();
                                    SettingsKit.saved(this);
                                })).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(controller::consume);
                                    return ErrorHandler.handle(this, "İşçilik kaydedilemedi", ex);
                                });
                    }
                })
        , id);
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(add_button);
    }
}
