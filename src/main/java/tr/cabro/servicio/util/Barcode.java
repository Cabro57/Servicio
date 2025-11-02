package tr.cabro.servicio.util;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
@Setter @Getter
public class Barcode {

    private static List<String> barcodes = new ArrayList<>();

    public static synchronized String generate() {
        String code;
        String companyPrefix = Servicio.getSettings().getBarcodePrefix();
        do {
            String base = companyPrefix + generateRandomBase(5); // 6 + 5 = 11 hane
            int checkDigit = calculateCheckDigit(base);
            code = base + checkDigit;
        } while (barcodes.contains(code)); // benzersiz tut
        barcodes.add(code);
        return code;
    }

    private static String generateRandomBase(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private static int calculateCheckDigit(String base) {
        int sumOdd = 0, sumEven = 0;
        for (int i = 0; i < base.length(); i++) {
            int digit = Character.getNumericValue(base.charAt(i));
            if ((i % 2) == 0) sumOdd += digit;
            else sumEven += digit;
        }
        int total = (sumOdd * 3) + sumEven;
        int mod = total % 10;
        return (mod == 0) ? 0 : 10 - mod;
    }
}
