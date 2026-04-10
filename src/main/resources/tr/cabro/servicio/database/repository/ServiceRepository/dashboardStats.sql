WITH ServiceStats AS (
    -- Adım 1: Servis tablosundaki istatistikleri ve işçilik ücretlerini hesapla
    SELECT
        COUNT(id) AS totalRecords,
        SUM(CASE WHEN service_status NOT IN ('DELIVERED', 'RETURN') THEN 1 ELSE 0 END) AS activeRecords,
        SUM(CASE WHEN service_status IN ('READY', 'DELIVERED') THEN 1 ELSE 0 END) AS completedRecords,
        COALESCE(SUM(labor_cost), 0.0) AS totalLaborCost
    FROM services
),
     PartStats AS (
         -- Adım 2: Yedek parça tablosundaki toplam satış ciro bedelini hesapla
         -- (Satış Fiyatı * Miktar)
         SELECT
             COALESCE(SUM(sale_price * amount), 0.0) AS totalPartsRevenue
         FROM added_part
     )
-- Adım 3: İki bağımsız hesabı tek bir satırda birleştir (Cartesian patlamasını engeller)
SELECT
    s.totalRecords,
    s.activeRecords,
    s.completedRecords,
    (s.totalLaborCost + p.totalPartsRevenue) AS totalRevenue
FROM ServiceStats s, PartStats p;