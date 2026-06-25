-- V11__Remove_Seed_Events_And_Tickets.sql
-- Cleans up all existing events, ticket tiers, spectator tickets, and applications

DELETE FROM spectator_tickets;
DELETE FROM payments;
DELETE FROM ticket_tiers;
DELETE FROM event_summaries;
DELETE FROM event_applications;
DELETE FROM events;
