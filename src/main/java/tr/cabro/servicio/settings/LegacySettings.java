package tr.cabro.servicio.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter @Setter
public class LegacySettings extends Settings {

    private List<String> device_types = new ArrayList<>();

    private Map<String, List<String>> device_brands = new HashMap<>();

    private Map<String, Map<String, Double>> device_process = new HashMap<>();

}
