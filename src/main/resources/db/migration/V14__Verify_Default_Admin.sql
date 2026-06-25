-- V14__Verify_Default_Admin.sql
-- Set the default seeded admin user as verified and update password to 'WoodLand@786'
UPDATE users 
SET is_email_verified = TRUE,
    password_hash = '$2a$12$TJQciDBcKyDgkV.hkKRRieqG2alb2qrD0Mhr7ctHUl5/lSGudwYK6'
WHERE email = 'protocol@enicilion.com';
