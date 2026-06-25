-- V15__Cascade_Delete_Events.sql
-- Drop and recreate foreign keys with ON DELETE CASCADE to allow clean event deletion

-- 1. spectator_tickets referencing events
ALTER TABLE spectator_tickets DROP CONSTRAINT IF EXISTS spectator_tickets_event_id_fkey;
ALTER TABLE spectator_tickets ADD CONSTRAINT spectator_tickets_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;

-- 2. spectator_tickets referencing ticket_tiers
ALTER TABLE spectator_tickets DROP CONSTRAINT IF EXISTS spectator_tickets_tier_id_fkey;
ALTER TABLE spectator_tickets ADD CONSTRAINT spectator_tickets_tier_id_fkey FOREIGN KEY (tier_id) REFERENCES ticket_tiers(id) ON DELETE CASCADE;

-- 3. checkin_events referencing spectator_tickets
ALTER TABLE checkin_events DROP CONSTRAINT IF EXISTS checkin_events_ticket_id_fkey;
ALTER TABLE checkin_events ADD CONSTRAINT checkin_events_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES spectator_tickets(id) ON DELETE CASCADE;

-- 4. pos_sales referencing ticket_tiers
ALTER TABLE pos_sales DROP CONSTRAINT IF EXISTS pos_sales_tier_id_fkey;
ALTER TABLE pos_sales ADD CONSTRAINT pos_sales_tier_id_fkey FOREIGN KEY (tier_id) REFERENCES ticket_tiers(id) ON DELETE CASCADE;

-- 5. ticket_notes referencing spectator_tickets
ALTER TABLE ticket_notes DROP CONSTRAINT IF EXISTS ticket_notes_ticket_id_fkey;
ALTER TABLE ticket_notes ADD CONSTRAINT ticket_notes_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES spectator_tickets(id) ON DELETE CASCADE;

-- 6. ticket_transfers referencing spectator_tickets
ALTER TABLE ticket_transfers DROP CONSTRAINT IF EXISTS ticket_transfers_ticket_id_fkey;
ALTER TABLE ticket_transfers ADD CONSTRAINT ticket_transfers_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES spectator_tickets(id) ON DELETE CASCADE;

-- 7. influencer_earnings_ledger referencing spectator_tickets
ALTER TABLE influencer_earnings_ledger DROP CONSTRAINT IF EXISTS influencer_earnings_ledger_ticket_id_fkey;
ALTER TABLE influencer_earnings_ledger ADD CONSTRAINT influencer_earnings_ledger_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES spectator_tickets(id) ON DELETE CASCADE;
