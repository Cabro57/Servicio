package tr.cabro.servicio.settings;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.BackupMode;

import java.util.*;

@Getter
@Setter
public class Settings extends OkaeriConfig {

    public Settings() {
        payment_type.add("Nakit");
        payment_type.add("Banka/Kredi Kartı");
        payment_type.add("Banka Havale/EFT");
        payment_type.add("Veresiye");
    }

    private String version = "2.0.0 BETA-3";
    private Template template = new Template();
    private String path = "";
    private boolean full_size = false;
    private boolean FirstRun = true;

    private BackupSettings backup = new BackupSettings();

    @Getter @Setter
    public static class BackupSettings extends OkaeriConfig {

        private String path = Servicio.getInstance().getDataFolder().getAbsolutePath() + "\\backups";
        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;


    }

    private List<String> device_types = new ArrayList<>();
    private Map<String, List<String>> device_brands = new HashMap<>();
    private Map<String, Map<String, Double>> device_process = new HashMap<>();

    private List<String> payment_type = new ArrayList<>();

    @Getter @Setter
    public static class Template extends OkaeriConfig {
        private String selected_theme = "Light";
        private Map<String, String> themes = new HashMap<>();

        private Template() {
            themes.put("Light", "com.formdev.flatlaf.FlatLightLaf");
            themes.put("Dark", "com.formdev.flatlaf.FlatDarkLaf");
            themes.put("IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
            themes.put("Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
            themes.put("macOS Light v3", "com.formdev.flatlaf.themes.FlatMacLightLaf");
            themes.put("macOS Dark v3", "com.formdev.flatlaf.themes.FlatMacDarkLaf");
        }
    }

    // === Device Type ===
    public boolean addDeviceType(String typeName) {
        if (!device_types.contains(typeName)) {
            device_types.add(typeName);
            device_brands.put(typeName, new ArrayList<>());
            device_process.put(typeName, new HashMap<>());
            return true;
        }
        return false; // Zaten vardı
    }

    public boolean removeDeviceType(String typeName) {
        boolean removed = device_types.remove(typeName);
        if (removed) {
            device_brands.remove(typeName);
            device_process.remove(typeName);
        }
        return removed;
    }

    // === Brand ===
    public List<String> getBrands(String typeName) {
        return device_brands.getOrDefault(typeName, Collections.emptyList());
    }

    public boolean addBrand(String typeName, String brandName) {
        if (!device_types.contains(typeName)) {
            addDeviceType(typeName);
        }
        List<String> brands = device_brands.get(typeName);
        if (!brands.contains(brandName)) {
            brands.add(brandName);
            return true;
        }
        return false;
    }

    public boolean removeBrand(String typeName, String brandName) {
        List<String> brands = device_brands.get(typeName);
        if (brands != null && brands.removeIf(b -> b.equalsIgnoreCase(brandName))) {
            if (brands.isEmpty()) {
                device_brands.remove(typeName);
            }
            return true;
        }
        return false;
    }

    // === Process ===
    public Map<String, Double> getProcess(String typeName) {
        return device_process.getOrDefault(typeName, Collections.emptyMap());
    }

    public boolean addProcess(String typeName, String processName, Double price) {
        if (!device_types.contains(typeName)) {
            addDeviceType(typeName);
        }
        Map<String, Double> processes = device_process.get(typeName);
        boolean isNew = !processes.containsKey(processName);
        processes.put(processName, price);
        return isNew;
    }

    public boolean removeProcess(String typeName, String processName) {
        Map<String, Double> processes = device_process.get(typeName);
        if (processes != null) {
            Double removed = processes.remove(processName);
            if (processes.isEmpty()) {
                device_process.remove(typeName);
            }
            return removed != null;
        }
        return false;
    }
}
