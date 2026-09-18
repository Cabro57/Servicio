package tr.cabro.servicio.application.component.table;

import raven.swingpack.JPagination;
import raven.swingpack.pagination.event.PaginationModelEvent;

/**
 * Projedeki tüm sayfalama bileşenleri için {@link JPagination} uyarlaması. Kütüphanenin
 * iki davranışını düzeltir; ikisi de her çağrı yerinde ayrı ayrı yamanmak yerine burada
 * bir kez çözülür.
 * <p>
 * <b>1. Saydam zemin.</b> {@code JPagination} bir {@code JPanel} ve varsayılan olarak
 * opak. Üzerine oturduğu tablo kartı {@code lighten($Panel.background, 3%)} ile
 * boyandığı için, sayfalama düz {@code $Panel.background} basıyor ve kartın üstünde
 * farklı tonda bir dikdörtgen olarak görünüyordu. {@code setBackground(null)} bunu
 * çözmez: {@code Component.getBackground()} alan {@code null} olduğunda ebeveynin
 * rengine düşer, boyama yine yapılır. Çözüm opaklığı kapatmak.
 * <p>
 * <b>2. Sayfa sayısı büyüyünce yeniden yerleşim.</b> {@code JPagination}'ın tercih
 * edilen boyutu model'deki sayfa adedine bağlıdır, ama model değişince yalnızca
 * {@code repaint()} çağrılır, {@code revalidate()} çağrılmaz. Liste formları bileşeni
 * {@code new AppPagination(5, 1, 1)} ile (tek sayfa genişliğinde) kurup veriyi asenkron
 * bekliyor; veri gelip {@code setPageRange(1, N)} çağrıldığında model büyüyor fakat
 * bileşenin sınırları eski kalıyor ve fazladan sayfa butonları kırpılıp görünmez
 * oluyordu — ekran "tam yüklenmemiş" gibi duruyordu. Model her değiştiğinde
 * {@code revalidate()} çağırmak bunu giderir.
 */
public class AppPagination extends JPagination {

    public AppPagination(int maxItem, int selectedPage, int pageSize) {
        super(maxItem, selectedPage, pageSize);
        // Zemini kartın kendisi boyasın; bkz. sınıf javadoc'u (1).
        setOpaque(false);
    }

    @Override
    public void paginationModelChanged(PaginationModelEvent event) {
        super.paginationModelChanged(event);
        // Sayfa adedi değiştiyse tercih edilen genişlik de değişti; bkz. javadoc (2).
        revalidate();
    }
}
