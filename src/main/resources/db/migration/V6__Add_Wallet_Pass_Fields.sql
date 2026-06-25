-- V6__Add_Wallet_Pass_Fields.sql
ALTER TABLE spectator_tickets
ADD COLUMN apple_pass_id VARCHAR(255),
ADD COLUMN google_wallet_object_id VARCHAR(255),
ADD COLUMN wallet_last_updated TIMESTAMP WITH TIME ZONE;

-- Create indexes on pass IDs since they might be used for webhook lookups later
CREATE INDEX idx_spectator_tickets_apple_pass ON spectator_tickets(apple_pass_id);
CREATE INDEX idx_spectator_tickets_google_pass ON spectator_tickets(google_wallet_object_id);
