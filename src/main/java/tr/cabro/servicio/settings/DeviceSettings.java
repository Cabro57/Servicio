package tr.cabro.servicio.settings;

import eu.okaeri.configs.annotation.Exclude;
import tr.cabro.servicio.model.Process;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.text.Collator;
import java.util.*;

@Getter
@Setter @Deprecated
public class DeviceSettings extends OkaeriConfig {

    private List<DeviceType> types = new ArrayList<>();

    private Map<String, List<String>> brands = new HashMap<>();

    private Map<String, List<Process>> processes = new HashMap<>();

    // Türkçe karakterleri doğru sıralamak için Collator (static final olduğu için Config dosyasına kaydedilmez)
    @Exclude
    private static final Collator TR_COLLATOR = Collator.getInstance(new Locale("tr", "TR"));

    // 1. Types listesini alfabetik olarak döndürüyoruz (Lombok'un metodunu ezer)
    public List<DeviceType> getTypes() {
        if (types != null) {
            types.sort(TR_COLLATOR);
        }
        return types;
    }

    // 2. Brands listesini alfabetik olarak döndürüyoruz
    public List<String> getBrands(String typeName) {
        List<String> brandList = brands.get(typeName);
        if (brandList != null) {
            brandList.sort(TR_COLLATOR);
            return brandList;
        }
        return Collections.emptyList();
    }

    // 3. Processes listesini (işlem ismine göre) alfabetik olarak döndürüyoruz
    public List<Process> getProcesses(String typeName) {
        List<Process> list = processes.get(typeName);
        if (list != null) {
            list.sort((p1, p2) -> TR_COLLATOR.compare(p1.getName(), p2.getName()));
            return list;
        }
        return Collections.emptyList();
    }

//    public boolean addDeviceType(String typeName) {
//        if (typeName == null || typeName.trim().isEmpty()) return false;
//        if (!types.contains(typeName)) {
//            types.add(typeName);
//            return true;
//        }
//        return false;
//    }

    public boolean removeDeviceType(String typeName) {
        boolean removed = types.remove(typeName);
        if (removed) {
            brands.remove(typeName);
            processes.remove(typeName);
        }
        return removed;
    }

//    public boolean addBrand(String typeName, String brandName) {
//        if (brandName == null || brandName.trim().isEmpty()) return false;
//        if (!types.contains(typeName)) addDeviceType(typeName);
//
//        List<String> brandList = brands.computeIfAbsent(typeName, k -> new ArrayList<>());
//        if (!brandList.contains(brandName)) {
//            brandList.add(brandName);
//            return true;
//        }
//        return false;
//    }

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

//    public boolean addProcess(String typeName, String name, String comment, BigDecimal price) {
//        if (!types.contains(typeName)) addDeviceType(typeName);
//
//        List<tr.cabro.servicio.model.Process> list = processes.computeIfAbsent(typeName, k -> new ArrayList<>());
//        boolean exists = list.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name));
//        if (!exists) {
//            list.add(new Process(name, comment, price));
//            return true;
//        }
//        return false;
//    }

//    public boolean addProcess(String typeName, Process process) {
//        return addProcess(typeName, process.getName(), process.getComment(), process.getPrice());
//    }

    public boolean updateProcess(String typeName, String oldName, Process newProcess) {
        List<Process> list = processes.get(typeName);
        if (list == null) return false;

        for (int i = 0; i < list.size(); i++) {
            Process p = list.get(i);
            if (p.getName().equalsIgnoreCase(oldName)) {
                // Aynı isimle başka process var mı kontrol et
                boolean exists = list.stream()
                        .anyMatch(proc -> !proc.getName().equalsIgnoreCase(oldName)
                                && proc.getName().equalsIgnoreCase(newProcess.getName()));
                if (exists) {
                    return false; // aynı isimli başka kayıt varsa güncellenmez
                }

                // Güncelle
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