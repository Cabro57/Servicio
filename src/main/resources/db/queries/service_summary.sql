WITH service_data AS (
    SELECT
        COUNT(*) AS total_services,
        SUM(labor_cost) AS total_labor_income
    FROM services
),
month_service_data AS (
    SELECT
        COUNT(*) AS month_services,
        SUM(labor_cost) AS month_labor_income
    FROM services
    WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')
),
part_data AS (
    SELECT
        SUM(purchase_price * amount) AS total_cost,
        SUM(sale_price * amount) AS total_income
    FROM added_part
),
month_part_data AS (
    SELECT
        SUM(purchase_price * amount) AS month_cost,
        SUM(sale_price * amount) AS month_income
    FROM added_part
    WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')
)
SELECT
    -- Servis sayıları
    sd.total_services AS toplam_servis,
    msd.month_services AS bu_ay_servis,
    ROUND((msd.month_services * 100.0 / sd.total_services), 2) AS bu_ay_servis_orani,

    -- Gelir
    (sd.total_labor_income + pd.total_income) AS toplam_gelir,
    (msd.month_labor_income + mpd.month_income) AS bu_ay_gelir,
    ROUND(((msd.month_labor_income + mpd.month_income) * 100.0 /
           (sd.total_labor_income + pd.total_income)), 2) AS bu_ay_gelir_orani,

    -- Gider
    pd.total_cost AS toplam_gider,
    mpd.month_cost AS bu_ay_gider,
    ROUND((mpd.month_cost * 100.0 / pd.total_cost), 2) AS bu_ay_gider_orani,

    -- Kâr
    ((sd.total_labor_income + pd.total_income) - pd.total_cost) AS toplam_kar,
    ((msd.month_labor_income + mpd.month_income) - mpd.month_cost) AS bu_ay_kar,
    ROUND((((msd.month_labor_income + mpd.month_income) - mpd.month_cost) * 100.0 /
           ((sd.total_labor_income + pd.total_income) - pd.total_cost)), 2) AS bu_ay_kar_orani

FROM service_data sd
CROSS JOIN month_service_data msd
CROSS JOIN part_data pd
CROSS JOIN month_part_data mpd;
