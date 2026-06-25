-- V12__Coupon_Usage_Relation.sql
-- Create coupon_usages relation and drop deprecated single-use columns from coupons table

DROP INDEX IF EXISTS idx_coupons_reserved_payment_id;
DROP INDEX IF EXISTS idx_coupons_used_payment_id;
DROP INDEX IF EXISTS idx_coupons_reserved_at;

ALTER TABLE coupons DROP COLUMN IF EXISTS reserved_payment_id;
ALTER TABLE coupons DROP COLUMN IF EXISTS reserved_at;
ALTER TABLE coupons DROP COLUMN IF EXISTS used_payment_id;
ALTER TABLE coupons DROP COLUMN IF EXISTS used_at;

CREATE TABLE coupon_usages (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    used_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_usages_coupon_id ON coupon_usages(coupon_id);
CREATE INDEX idx_coupon_usages_payment_id ON coupon_usages(payment_id);
