-- V7__Add_User_Search_Index.sql
-- Performance index to support fast case-insensitive search on users name and email
CREATE INDEX IF NOT EXISTS idx_users_search_email_fullname ON users (email, full_name);
