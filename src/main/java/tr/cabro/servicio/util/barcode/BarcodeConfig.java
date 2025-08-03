package tr.cabro.servicio.util.barcode;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BarcodeConfig extends OkaeriConfig {
    // Getters & Setters
    private String prefix = "SV";         // Önek (örn: "PRD")
    private int numberLength = 5;      // Sayı uzunluğu (örn: 5 => 00001)
    private boolean useDate = false;       // Tarih eklenecek mi
    private String separator = "";      // Ayırıcı (örn: "-", "_", "")



}
