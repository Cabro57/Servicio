WITH part_summary AS (
    SELECT
        service_id,
        SUM(sale_price * amount) AS part_income,
        SUM(purchase_price * amount) AS part_expense
    FROM added_part
    GROUP BY service_id
),
     monthly_data AS (
         SELECT
             strftime('%Y-%m', s.created_at) AS month,
    COUNT(DISTINCT s.id) AS service_count,
    SUM(s.labor_cost) AS service_income,
    SUM(ps.part_income) AS part_income,
    SUM(ps.part_expense) AS part_expense
FROM services s
    LEFT JOIN part_summary ps ON ps.service_id = s.id
WHERE s.created_at IS NOT NULL
GROUP BY strftime('%Y-%m', s.created_at)
    ),
    calc AS (
SELECT
    md.month,
    md.service_count,
    ROUND(((md.service_count - COALESCE(prev.service_count, 0)) * 100.0 /
    NULLIF(prev.service_count, 0)), 2) AS service_change_rate,
    (md.service_income + md.part_income) AS total_revenue,
    ROUND((((md.service_income + md.part_income) -
    COALESCE(prev.service_income + prev.part_income, 0)) * 100.0 /
    NULLIF(prev.service_income + prev.part_income, 0)), 2) AS revenue_change_rate,
    md.part_expense AS total_expense,
    ROUND(((md.part_expense - COALESCE(prev.part_expense, 0)) * 100.0 /
    NULLIF(prev.part_expense, 0)), 2) AS expense_change_rate,
    ((md.service_income + md.part_income) - md.part_expense) AS total_profit,
    ROUND(((((md.service_income + md.part_income) - md.part_expense) -
    COALESCE((prev.service_income + prev.part_income) - prev.part_expense, 0)) * 100.0 /
    NULLIF((prev.service_income + prev.part_income) - prev.part_expense, 0)), 2) AS profit_change_rate
FROM monthly_data md
    LEFT JOIN monthly_data prev
ON prev.month = strftime('%Y-%m', date(md.month || '-01', '-1 month'))
    )
SELECT *
FROM calc
ORDER BY month;
