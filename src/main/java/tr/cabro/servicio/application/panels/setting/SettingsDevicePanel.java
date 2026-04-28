package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.handlers.BrandExportHandler;
import tr.cabro.servicio.application.handlers.TypeImportHandler;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

public class SettingsDevicePanel extends JPanel {

    private final DefaultListModel<DeviceType> typeModel;
    private final DefaultListModel<DeviceBrand> brandModel;

    private final DeviceDictionaryManager deviceDictService;

    public SettingsDevicePanel() {
        this.deviceDictService = ServiceManager.getDeviceDictionaryManager();

        typeModel = new DefaultListModel<>();
        brandModel = new DefaultListModel<>();

        init();
    }

    private void init() {
        initComponent();
        deviceDictService.getAllTypes()
                .thenAccept(deviceTypes -> deviceTypes.forEach(typeModel::addElement));

        type_field.addActionListener(e -> onTypeAdd());

        type_add_button.addActionListener(e -> onTypeAdd());
        type_del_button.addActionListener(e -> onTypeDel());

        // Tür seçildiğinde markaları yükle
        type_list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeviceType selectedType = type_list.getSelectedValue();
                loadBrands(selectedType);
            }
        });

        brand_field.addActionListener(e -> onBrandAdd());

        brand_add_button.addActionListener(e -> onBrandAdd());
        brand_del_button.addActionListener(e -> onBrandDel());

        // --- Drag & Drop ayarları ---
        brand_list.setDragEnabled(true);
        brand_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        brand_list.setTransferHandler(new BrandExportHandler());

        type_list.setDropMode(DropMode.ON);
        type_list.setTransferHandler(new TypeImportHandler(this));
    }

    private void onTypeAdd() {
        String typeName = type_field.getText().trim();

        deviceDictService.addType(typeName).thenAccept(id -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, "Cihaz türü eklendi: " + typeName);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, ex.getMessage());
            });

            return null;
        });

        type_field.setText("");
    }

    private void onTypeDel() {
        DeviceType selectedType = type_list.getSelectedValue();

        if (selectedType == null) {
            Toast.show(this, Toast.Type.WARNING, "Silinecek tür seçilmedi.");
            return;
        }

        deviceDictService.deleteType(selectedType.getId()).thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                typeModel.removeElement(selectedType);
                brandModel.clear();
                Toast.show(this, Toast.Type.SUCCESS, "Cihaz türü silindi: " + selectedType);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, ex.getMessage());
            });

            return null;
        });

    }

    private void onBrandAdd() {
        DeviceType selectedType = type_list.getSelectedValue();
        String brandName = brand_field.getText().trim();
        if (selectedType != null) {
            deviceDictService.addBrand(brandName).thenAccept(id -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Marka oluşturuldu: " + brandName);
                    loadBrands(selectedType);
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.WARNING, ex.getMessage());
                });

                return null;
            });
        }

        brand_field.setText("");
    }

    private void onBrandDel() {
        DeviceType selectedType = type_list.getSelectedValue();
        DeviceBrand selectedBrand = brand_list.getSelectedValue();
        if (selectedType != null && selectedBrand != null) {
            deviceDictService.unlinkBrandFromType(selectedType.getId(), selectedBrand.getId()).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.INFO, String.format("%s türüne ait %s markası silindi.", selectedType.getName(), selectedBrand.getName()));
                });
            });
        }
    }

    public void loadBrands(DeviceType deviceType) {
        if (deviceType == null) {
            return;
        }

        deviceDictService.getBrandsByTypeId(deviceType.getId()).thenAccept(deviceBrands -> {
            deviceBrands.stream()
                    .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

            SwingUtilities.invokeLater(() -> {
                brandModel.clear();
                deviceBrands.forEach(brandModel::addElement);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, ex.getMessage());
            });
            return null;
        });
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 5, gap 10", "[grow][grow]", "[fill, grow]"));

        // --- Cihaz Türleri Paneli ---
        JPanel device_type_panel = new JPanel(new MigLayout("insets 5, fill, wrap 2", "[grow][pref!]", "[][][grow][]"));
        device_type_panel.setBorder(BorderFactory.createTitledBorder("Cihaz Türleri"));

        type_field = new JTextField();
        type_add_button = new JButton("Ekle");
        type_list = new JList<>();
        type_list.setModel(typeModel);
        type_del_button = new JButton("Seçili Cihaz Türünü Sil");

        device_type_panel.add(new JLabel("Yeni Tür:"), "span 2");
        device_type_panel.add(type_field, "growx");
        device_type_panel.add(type_add_button, "wrap");
        device_type_panel.add(new JScrollPane(type_list), "span 2, grow");
        device_type_panel.add(type_del_button, "span 2, growx");

        // --- Markalar Paneli ---
        JPanel brand_panel = new JPanel(new MigLayout("insets 5, fill, wrap 2", "[grow][pref!]", "[][][grow][]"));
        brand_panel.setBorder(BorderFactory.createTitledBorder("Markalar"));

        brand_field = new JTextField();
        brand_add_button = new JButton("Ekle");
        brand_list = new JList<>();
        brand_list.setModel(brandModel);
        brand_del_button = new JButton("Seçili Markayı Sil");

        brand_panel.add(new JLabel("Yeni Marka:"), "span 2");
        brand_panel.add(brand_field, "growx");
        brand_panel.add(brand_add_button, "wrap");
        brand_panel.add(new JScrollPane(brand_list), "span 2, grow");
        brand_panel.add(brand_del_button, "span 2, growx");

        // Ana panele ekle
        add(device_type_panel, "grow, sg panels");
        add(brand_panel, "grow, sg panels");
    }

    JTextField type_field;
    JButton type_add_button;
    public JList<DeviceType> type_list;
    JButton type_del_button;
    JTextField brand_field;
    JButton brand_add_button;
    JList<DeviceBrand> brand_list;
    JButton brand_del_button;

}
