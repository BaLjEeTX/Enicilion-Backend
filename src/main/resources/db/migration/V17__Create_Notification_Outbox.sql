-- V17__Create_Notification_Outbox.sql

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    ticket_code VARCHAR(64) NOT NULL,
    override_email VARCHAR(255),
    override_phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    error_message TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_outbox_status ON notification_outbox(status);
CREATE INDEX idx_notification_outbox_scheduled_at ON notification_outbox(scheduled_at);
