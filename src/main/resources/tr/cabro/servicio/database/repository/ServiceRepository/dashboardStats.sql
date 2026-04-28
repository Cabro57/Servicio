-- dashboardStats() — WorkOrderRepository
-- Dashboard için özet istatistikler
SELECT
    COUNT(s.id)                                                             AS totalRecords,
    COUNT(CASE WHEN s.service_status NOT IN ('DELIVERED', 'RETURN') THEN 1 END) AS activeRecords,
    COUNT(CASE WHEN s.service_status = 'DELIVERED' THEN 1 END)             AS completedRecords,
    COALESCE(SUM(sp.total_paid), 0.0)                                       AS totalRevenue
FROM workOrders s
LEFT JOIN (
    SELECT service_id, SUM(amount) AS total_paid
    FROM service_payments
    GROUP BY service_id
) sp ON sp.service_id = s.id;