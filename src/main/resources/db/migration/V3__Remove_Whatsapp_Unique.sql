-- V3__Remove_Whatsapp_Unique.sql
-- Removes unique constraint on whatsapp column as it can be shared or reused across accounts/checkouts

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_whatsapp_key;
