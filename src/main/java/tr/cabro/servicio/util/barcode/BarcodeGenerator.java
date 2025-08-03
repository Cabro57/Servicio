package tr.cabro.servicio.util.barcode;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BarcodeGenerator {
    private final BarcodeConfig config;
    private long lastNumber = 0;

    public BarcodeGenerator(BarcodeConfig config) {
        this.config = config;
    }

    public synchronized String generate() {
        lastNumber = lastNumber + 1;

        StringBuilder sb = new StringBuilder();
        if (config.getPrefix() != null && !config.getPrefix().isEmpty()) {
            sb.append(config.getPrefix()).append(config.getSeparator());
        }
        if (config.isUseDate()) {
            String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
            sb.append(date).append(config.getSeparator());
        }
        sb.append(String.format("%0" + config.getNumberLength() + "d", lastNumber));

        return sb.toString();
    }
}
