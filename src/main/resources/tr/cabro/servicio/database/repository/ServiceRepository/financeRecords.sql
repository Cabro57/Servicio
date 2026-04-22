-- financeRecords() — ServiceRepository
-- Aylık gelir/gider/kâr raporu (service_items + service_payments tabloları)
WITH item_summary AS (
    SELECT
        si.service_id,
        SUM(CASE WHEN si.item_type = 'PART'  THEN si.unit_price  * si.quantity ELSE 0 END) AS part_income,
        SUM(CASE WHEN si.item_type = 'PART'  THEN si.purchase_price * si.quantity ELSE 0 END) AS part_expense,
        SUM(CASE WHEN si.item_type = 'LABOR' THEN si.unit_price  * si.quantity ELSE 0 END) AS labor_income
    FROM service_items si
    GROUP BY si.service_id
),
monthly_data AS (
    SELECT
        strftime('%Y-%m', s.created_at) AS month,
        COUNT(DISTINCT s.id)                                                    AS service_count,
        COALESCE(SUM(is2.part_income), 0)                                       AS part_income,
        COALESCE(SUM(is2.part_expense), 0)                                      AS part_expense,
        COALESCE(SUM(is2.labor_income), 0)                                      AS labor_income
    FROM services s
    LEFT JOIN item_summary is2 ON is2.service_id = s.id
    WHERE s.created_at IS NOT NULL
    GROUP BY strftime('%Y-%m', s.created_at)
),
calc AS (
    SELECT
        md.month,
        md.service_count,
        ROUND(((md.service_count - COALESCE(prev.service_count, 0)) * 100.0 /
               NULLIF(prev.service_count, 0)), 2)                               AS service_change_rate,
        (md.part_income + md.labor_income)                                      AS total_revenue,
        ROUND((((md.part_income + md.labor_income) -
                COALESCE(prev.part_income + prev.labor_income, 0)) * 100.0 /
                NULLIF(prev.part_income + prev.labor_income, 0)), 2)            AS revenue_change_rate,
        md.part_expense                                                         AS total_expense,
        ROUND(((md.part_expense - COALESCE(prev.part_expense, 0)) * 100.0 /
               NULLIF(prev.part_expense, 0)), 2)                                AS expense_change_rate,
        ((md.part_income + md.labor_income) - md.part_expense)                 AS total_profit,
        ROUND(((((md.part_income + md.labor_income) - md.part_expense) -
                COALESCE((prev.part_income + prev.labor_income) - prev.part_expense, 0)) * 100.0 /
                NULLIF((prev.part_income + prev.labor_income) - prev.part_expense, 0)), 2) AS profit_change_rate
    FROM monthly_data md
    LEFT JOIN monthly_data prev
        ON prev.month = strftime('%Y-%m', date(md.month || '-01', '-1 month'))
)
SELECT *
FROM calc
ORDER BY month;
