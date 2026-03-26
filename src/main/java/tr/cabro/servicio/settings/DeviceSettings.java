package tr.cabro.servicio.settings;

import tr.cabro.servicio.model.Process;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class DeviceSettings extends OkaeriConfig {

    private List<String> types = new ArrayList<>();

    private Map<String, List<String>> brands = new HashMap<>();

    private Map<String, List<Process>> processes = new HashMap<>();

    public boolean addDeviceType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) return false;
        if (!types.contains(typeName)) {
            types.add(typeName);
            return true;
        }
        return false;
    }

    public boolean removeDeviceType(String typeName) {
        boolean removed = types.remove(typeName);
        if (removed) {
            brands.remove(typeName);
            processes.remove(typeName);
        }
        return removed;
    }

    public List<String> getBrands(String typeName) {
        return brands.getOrDefault(typeName, Collections.emptyList());
    }

    public boolean addBrand(String typeName, String brandName) {
        if (brandName == null || brandName.trim().isEmpty()) return false;
        if (!types.contains(typeName)) addDeviceType(typeName);

        List<String> brandList = brands.computeIfAbsent(typeName, k -> new ArrayList<>());
        if (!brandList.contains(brandName)) {
            brandList.add(brandName);
            return true;
        }
        return false;
    }

    public boolean removeBrand(String typeName, String brandName) {
        List<String> brandList = brands.get(typeName);
        if (brandList != null && brandList.removeIf(b -> b.equalsIgnoreCase(brandName))) {
            if (brandList.isEmpty()) {
                brands.remove(typeName);
            }
            return true;
        }
        return false;
    }

    public List<Process> getProcesses(String typeName) {
        return processes.getOrDefault(typeName, Collections.emptyList());
    }

    public boolean addProcess(String typeName, String name, String comment, double price) {
        if (!types.contains(typeName)) addDeviceType(typeName);

        List<tr.cabro.servicio.model.Process> list = processes.computeIfAbsent(typeName, k -> new ArrayList<>());
        boolean exists = list.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (!exists) {
            list.add(new Process(name, comment, price));
            return true;
        }
        return false;
    }

    public boolean addProcess(String typeName, Process process) {
        return addProcess(typeName, process.getName(), process.getComment(), process.getPrice());
    }

    public boolean updateProcess(String typeName, String oldName, Process newProcess) {
        List<Process> list = processes.get(typeName);
        if (list == null) return false;

        for (int i = 0; i < list.size(); i++) {
            Process p = list.get(i);
            if (p.getName().equalsIgnoreCase(oldName)) {
                // aynı isimle başka process var mı kontrol et
                boolean exists = list.stream()
                        .anyMatch(proc -> !proc.getName().equalsIgnoreCase(oldName)
                                && proc.getName().equalsIgnoreCase(newProcess.getName()));
                if (exists) {
                    return false; // aynı isimli başka kayıt varsa güncellenmez
                }

                // güncelle
                list.set(i, newProcess);
                return true;
            }
        }
        return false;
    }

    public boolean removeProcess(String typeName, String processName) {
        List<Process> list = processes.get(typeName);
        if (list != null && list.removeIf(p -> p.getName().equalsIgnoreCase(processName))) {
            if (list.isEmpty()) {
                processes.remove(typeName);
            }
            return true;
        }
        return false;
    }
}