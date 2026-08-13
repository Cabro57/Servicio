package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.handlers.BrandExportHandler;
import tr.cabro.servicio.application.handlers.TypeImportHandler;
import tr.cabro.servicio.application.renderer.list.BrandListCellRenderer;
import tr.cabro.servicio.application.renderer.list.TypeListCellRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
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

        typeField.addActionListener(e -> onTypeAdd());

        typeAddButton.addActionListener(e -> onTypeAdd());

        // Tür seçildiğinde markaları yükle
        typeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeviceType selectedType = typeList.getSelectedValue();
                loadBrands(selectedType);
            }
        });

        brandField.addActionListener(e -> onBrandAdd());
        brandAddButton.addActionListener(e -> onBrandAdd());

        // --- Drag & Drop ayarları ---
        brandList.setDragEnabled(true);
        brandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        brandList.setTransferHandler(new BrandExportHandler());

        typeList.setDropMode(DropMode.ON);
        typeList.setTransferHandler(new TypeImportHandler(this));
    }

    private void onTypeAdd() {
        String typeName = typeField.getText().trim();

        deviceDictService.addType(typeName).thenAccept(id -> {
            SwingUtilities.invokeLater(() -> {
                typeModel.addElement(new DeviceType((long) id, typeName, 0));
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.deviceType.added", typeName));
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türü eklenemedi", ex));

        typeField.setText("");
    }

    private void onTypeDel(DeviceType selectedType) {

        if (selectedType == null) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.deviceType.selectToDelete"));
            return;
        }

        deviceDictService.deleteType(selectedType.getId()).thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                typeModel.removeElement(selectedType);
                brandModel.clear();
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.deviceType.deleted", selectedType));
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türü silinemedi", ex));

    }

    private void onBrandAdd() {
        DeviceType selectedType = typeList.getSelectedValue();
        String brandName = brandField.getText().trim();
        if (selectedType != null) {
            deviceDictService.addBrandToType(selectedType.getId(), brandName).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.brand.created", brandName));
                    loadBrands(selectedType);
                });
            }).exceptionally(ex -> ErrorHandler.handle(this, "Marka eklenemedi", ex));
        } else {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.deviceType.selectFirst"));
        }

        brandField.setText("");
    }

    private void onBrandDel(DeviceBrand selectedBrand) {
        DeviceType selectedType = typeList.getSelectedValue();
        if (selectedType != null && selectedBrand != null) {
            deviceDictService.unlinkBrandFromType(selectedType.getId(), selectedBrand.getId()).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    brandModel.removeElement(selectedBrand);
                    Toast.show(this, Toast.Type.INFO, Messages.get("toast.brand.unlinked", selectedType.getName(), selectedBrand.getName()));
                });
            }).exceptionally(ex -> ErrorHandler.handle(this, "Marka bağlantısı kaldırılamadı", ex));
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
                brandTitle.setText(deviceType.getName() + " Markaları");
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Markalar yüklenemedi", ex));
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 5, gap 10", "[][grow]", "[fill, grow]"));


        // Ana panele ekle
        add(getDeviceTypePanel(), "");
        add(getBrandPanel(), "grow");
    }

    private JPanel getDeviceTypePanel() {
        JPanel deviceTypePanel = new JPanel(new MigLayout("insets 5, fill, wrap 2", "[grow][pref!]", "[]1[]15[][grow][]"));
        deviceTypePanel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        JLabel title = new JLabel("Cihaz Türleri");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h2.font");

        JLabel subtitle = new JLabel("Arıza kaydında seçilecek ana kategoriler.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        typeField = new JTextField();
        typeField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni Tür...");

        typeAddButton = new JButton(new Ikon("icons/plus.svg", typeField.getFont().getSize()));

        typeList = new JList<>();
        typeList.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        typeList.setCellRenderer(new TypeListCellRenderer(typeList, this::onTypeDel));
        typeList.setModel(typeModel);

        deviceTypePanel.add(title, "cell 0 0");
        deviceTypePanel.add(subtitle, "cell 0 1, wrap");

        deviceTypePanel.add(typeField, "growx");
        deviceTypePanel.add(typeAddButton, "wrap");
        deviceTypePanel.add(typeList, "span 2, grow");

        return deviceTypePanel;
    }

    private JPanel getBrandPanel() {
        JPanel brandPanel = new JPanel(new MigLayout("insets 5, fill, wrap 2", "[grow][pref!]", "[]1[]1[]15[][grow][]"));
        brandPanel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        brandTitle = new JLabel("");
        brandTitle.putClientProperty(FlatClientProperties.STYLE, "font: $h2.font");

        JLabel subtitle = new JLabel("Arıza kaydında seçilecek ana kategoriler.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        brandField = new JTextField();
        brandField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni Marka ekle...");

        brandAddButton = new JButton(new Ikon("icons/plus.svg", typeField.getFont().getSize()));

        brandList = new JList<>();
        brandList.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        brandList.setCellRenderer(new BrandListCellRenderer(brandList, this::onBrandDel));
        brandList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        brandList.setModel(brandModel);

        brandPanel.add(brandTitle, "cell 0 0");
        brandPanel.add(subtitle, "cell 0 1, wrap");

        brandPanel.add(new JSeparator(JSeparator.VERTICAL), "growx, wrap");

        brandPanel.add(brandField, "growx");
        brandPanel.add(brandAddButton, "wrap");
        brandPanel.add(brandList, "span 2, grow");

        return brandPanel;
    }

    private JLabel brandTitle;
    JTextField typeField;
    JButton typeAddButton;
    public JList<DeviceType> typeList;
    JTextField brandField;
    JButton brandAddButton;
    public JList<DeviceBrand> brandList;

}
