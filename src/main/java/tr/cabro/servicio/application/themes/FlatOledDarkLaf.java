package tr.cabro.servicio.application.themes;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * OLED ekranlar için saf siyah (#000000) zeminli koyu tema.
 * <p>
 * {@link FlatDarkLaf}'ı temel alır; renk değişiklikleri
 * {@code /themes/FlatOledDarkLaf.properties} dosyasında tanımlıdır.
 * FlatLaf, {@code ApplicationBootstrap} içinde kayıtlı olan {@code themes}
 * özel varsayılan kaynağından sınıf adına göre bu dosyayı otomatik yükler
 * (önce FlatLaf.properties, sonra FlatDarkLaf.properties, en son bu dosya).
 * <p>
 * OLED panellerde saf siyah pikseller kapalı kalır: hem kontrast artar
 * hem de karanlıkta göz yorgunluğu ve güç tüketimi azalır.
 */
public class FlatOledDarkLaf extends FlatDarkLaf {

    public static final String NAME = "Servicio OLED Dark";

    public static boolean setup() {
        return setup(new FlatOledDarkLaf());
    }

    /** Temayı {@link UIManager#getInstalledLookAndFeels()} listesine ekler. */
    public static void installLafInfo() {
        installLafInfo(NAME, FlatOledDarkLaf.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "OLED ekranlar için saf siyah koyu tema";
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
