CREATE TABLE staff_permissions (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    feature VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_staff_permissions_email_feature ON staff_permissions(email, feature);
