-- Eski Türkçe metinleri, Java Enum sabitlerine (UPPER_CASE) dönüştürüyoruz.

-- 1. ServiceStatus Dönüşümü
UPDATE services SET service_status = 'UNDER_REPAIR' WHERE service_status = 'Tamirde';
UPDATE services SET service_status = 'READY' WHERE service_status = 'Hazır';
UPDATE services SET service_status = 'ANOTHER_SERVICE' WHERE service_status = 'Başka Serviste';
UPDATE services SET service_status = 'DELIVERED' WHERE service_status IN ('Teslim edildi', 'Teslim Edildi');
UPDATE services SET service_status = 'RETURN' WHERE service_status = 'İade';
UPDATE services SET service_status = 'WAITING_FOR_PART' WHERE service_status = 'Parça Bekliyor';

-- 2. PaymentType Dönüşümü
UPDATE services SET payment_type = 'CASH' WHERE payment_type = 'Nakit';
UPDATE services SET payment_type = 'CARD' WHERE payment_type IN ('Banka/Kredi Kartı', 'Kredi Kartı');
UPDATE services SET payment_type = 'TRANSFER' WHERE payment_type = 'Banka Havale/EFT';
UPDATE services SET payment_type = 'ON_ACCOUNT' WHERE payment_type = 'Veresiye';

-- 3. CustomerType Dönüşümü
UPDATE customers SET status = 'NORMAL' WHERE status = 'Normal';
UPDATE customers SET status = 'BE_CAREFUL' WHERE status = 'Dikkat Et';
UPDATE customers SET status = 'DOING_BUSINESS' WHERE status = 'İş Yapma';
UPDATE customers SET status = 'SMALL_BUSINESS' WHERE status = 'Esnaf';
UPDATE customers SET status = 'DEALER' WHERE status = 'Bayi';
UPDATE customers SET status = 'PROBLEM' WHERE status = 'Problemli';

UPDATE customers SET phone_number_1 = '+90' || phone_number_1 WHERE phone_number_1 LIKE '5%' AND LENGTH(phone_number_1) = 10;
UPDATE customers SET phone_number_2 = '+90' || phone_number_2 WHERE phone_number_2 LIKE '5%' AND LENGTH(phone_number_2) = 10;
UPDATE suppliers SET phone = '+90' || phone WHERE phone LIKE '5%' AND LENGTH(phone) = 10;