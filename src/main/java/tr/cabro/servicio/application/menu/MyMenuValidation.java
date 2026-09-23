package tr.cabro.servicio.application.menu;

import lombok.Setter;
import raven.modal.Drawer;
import raven.modal.drawer.item.Item;
import raven.modal.drawer.item.MenuItem;
import raven.modal.drawer.menu.MenuValidation;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;

/**
 * Menüdeki üst seviye öğelerin görünürlüğünü belirler. raven'ın {@code menuValidation(int[])}
 * kancası yalnızca dizideki ORİJİNAL pozisyonu verir, {@link Item} nesnesini vermez — bu yüzden
 * pozisyonu {@link MyDrawerBuilder#createSimpleMenuOption()}'ın kaydettiği canlı menü dizisinden
 * geriye dönük olarak isme çevirip öyle karar veriyoruz. Böylece menüye yeni bir öğe eklenip
 * pozisyonlar kayınca (ör. "Ürünler") görünürlük kararı YANLIŞ öğeye uygulanmaz — eskiden burada
 * {@code {2,0}}/{@code {2,1}}/{@code {1,2}} gibi raven demo'sundan kalma, Servicio menüsünde
 * karşılığı olmayan ölü index kontrolleri vardı.
 * <p>
 * Dizi, {@link MyDrawerBuilder}'ın singleton'ı üzerinden DEĞİL doğrudan {@link #setMenuItems}
 * ile alınır: ilk menü kurulumu {@code MyDrawerBuilder}'ın private constructor'ı içinden,
 * {@code instance} alanı henüz atanmadan önce tetiklenir — {@code getInstance()} o anda
 * çağrılırsa sonsuz özyinelemeye girer.
 */
public class MyMenuValidation extends MenuValidation {

    @Setter
    private static MenuItem[] menuItems;

    public static void setUser(User user) {
        MyMenuValidation.user = user;
    }

    public static User user;

    @Override
    public boolean menuValidation(int[] index) {
        return validation(index);
    }

    public static boolean validation(Class<? extends Form> itemClass) {
        int[] index = Drawer.getMenuIndexClass(itemClass);
        if (index == null) {
            return false;
        }
        return validation(index);
    }

    public static boolean validation(int[] index) {
        if (user == null) {
            return false;
        }
        // Sadece üst seviye (tek elemanlı) öğeler için görünürlük özelleştirilir.
        if (index.length != 1) {
            return true;
        }

        String itemName = resolveItemName(index[0]);
        if (ServiceManager.getAppSettingService() == null) {
            return true;
        }
        if ("Parçalar".equals(itemName)) {
            return ServiceManager.getAppSettingService().isMenuShowParts();
        }
        if ("Ürünler".equals(itemName)) {
            return ServiceManager.getAppSettingService().isMenuShowProducts();
        }
        return true;
    }

    /**
     * raven'ın verdiği indeks dizideki pozisyon DEĞİL, grup başlıkları ({@link Item.Label}) ve
     * ayraçlar atlanarak sayılan menü sırasıdır. Menüye başlık eklenince pozisyonla eşlemek
     * görünürlük kararını yanlış öğeye uyguluyordu; burada n'inci {@link Item} bulunur.
     */
    private static String resolveItemName(int menuIndex) {
        if (menuItems == null || menuIndex < 0) {
            return null;
        }
        int seen = -1;
        for (MenuItem item : menuItems) {
            if (!item.isMenu()) continue;
            if (++seen == menuIndex) {
                return ((Item) item).getName();
            }
        }
        return null;
    }
}
