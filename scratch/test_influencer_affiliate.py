import urllib.request
import json
import random

BASE_URL = "http://localhost:4000"

def get_headers(token=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h

def api_request(url, method="GET", payload=None, token=None):
    req_url = f"{BASE_URL}{url}"
    data = json.dumps(payload).encode('utf-8') if payload else None
    req = urllib.request.Request(req_url, data=data, headers=get_headers(token), method=method)
    try:
        with urllib.request.urlopen(req) as res:
            status = res.status
            if status in (200, 201):
                status = 200
            return status, json.loads(res.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, body
    except Exception as e:
        return 0, str(e)

def run_test():
    print("=== Testing Influencer Affiliate & Coupon Management System ===")
    coupon_code = f"CREATOR{random.randint(1000, 9999)}"

    # 1. Register a new user for creator application
    email = f"creator_{random.randint(1000, 9999)}@gmail.com"
    register_payload = {
        "full_name": "Drift King Creator",
        "email": email,
        "password": "Password@123",
        "whatsapp": f"{random.randint(1000000000, 9999999999)}",
        "referred_by": ""
    }
    
    status, res = api_request("/api/auth/register", "POST", register_payload)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Creator registration failed: {res}")
        return False
    print("SUCCESS: Creator registered successfully.")

    # 2. Login as creator
    login_payload = {"email": email, "password": "Password@123"}
    status, res = api_request("/api/auth/login", "POST", login_payload)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Creator login failed: {res}")
        return False
    creator_token = res["data"]["access_token"]
    print("SUCCESS: Creator logged in successfully.")

    # 3. Creator applies through Creator Hub
    apply_payload = {
        "fullName": "Drift King Creator",
        "email": email,
        "phone": "+919999999999",
        "socialLinks": "Instagram: @driftking, Youtube: /dk",
        "followerCount": 50000,
        "nicheDescription": "Car tuning and drift showcases",
        "paymentDetails": "UPI: dk@okaxis"
    }
    status, res = api_request("/api/influencer/apply", "POST", apply_payload, creator_token)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Influencer apply failed: {res}")
        return False
    app_id = res["data"]["id"]
    print(f"SUCCESS: Influencer application submitted. Application ID: {app_id}")

    # 4. Check application status (should be PENDING)
    status, res = api_request("/api/influencer/application/status", "GET", token=creator_token)
    if status != 200 or res["data"]["status"] != "PENDING":
        print(f"FAIL: Application status check failed. Expected PENDING, got: {res}")
        return False
    print("SUCCESS: Application status is PENDING.")

    # 5. Login as admin
    admin_login = {"email": "admin@enicilion.com", "password": "admin123"}
    status, res = api_request("/api/auth/login", "POST", admin_login)
    if status != 200 or not res.get("success"):
        print("FAIL: Admin login failed.")
        return False
    admin_token = res["data"]["access_token"]
    print("SUCCESS: Admin logged in.")

    # 6. Admin approves onboarding, assigns coupon dynamic code and 15% discount
    review_payload = {
        "status": "APPROVED",
        "notes": f"Excellent profile, approved with {coupon_code} coupon.",
        "commissionType": "percentage",
        "commissionValue": 15.00,
        "couponCode": coupon_code,
        "discountPercentage": 15
    }
    status, res = api_request(f"/api/admin/influencers/applications/{app_id}/status", "POST", review_payload, admin_token)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Admin review application failed: {res}")
        return False
    print("SUCCESS: Admin approved influencer application.")

    # 7. Creator checks dashboard (should load stats, commission model and active coupon)
    status, res = api_request("/api/influencer/dashboard", "GET", token=creator_token)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Creator dashboard retrieval failed: {res}")
        return False
    dash = res["data"]
    profile_id = dash["profileId"]
    print(f"SUCCESS: Creator dashboard accessed. Profile ID: {profile_id}")
    print(f"Active Coupon: {dash['coupons'][0]['code']}, Discount %: {dash['coupons'][0]['discountPercentage']}")
    print(f"Commission Rules: {dash['commissionType']} - {dash['commissionValue']}")
    
    if dash['coupons'][0]['code'] != coupon_code or dash['coupons'][0]['discountPercentage'] != 15:
        print("FAIL: Coupon rules mismatch on creator dashboard.")
        return False

    # 8. Customer performs checkout using CREATOR15 coupon
    checkout_payload = {
        "items": [
            {
                "eventId": "e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1",
                "tierId": "11111111-1111-1111-1111-111111111111", # VIP Access (INR 1500)
                "quantity": 2
            }
        ],
        "couponCode": coupon_code,
        "premiumSpiritsAcknowledged": True,
        "clientTotal": 2648, # (1500 * 2) - 15% discount (450) + 49*2 service fee (98) = 2648
        "idempotencyKey": f"idemp_{random.randint(10000000, 99999999)}",
        "guest_name": "Test Customer",
        "guest_email": "customer@gmail.com",
        "guest_phone": "1234567890"
    }

    # First, validate the coupon using generic validation API
    validate_payload = {
        "code": coupon_code,
        "subtotal": 3000
    }
    status, res = api_request("/api/coupons/validate", "POST", validate_payload)
    if status != 200 or not res.get("success") or res.get("data") is None or res["data"].get("discountAmount") != 450.0:
        print(f"FAIL: Generic validation endpoint failed. Got response: {res}")
        return False
    print("SUCCESS: Coupon validated using public validate endpoint with correct 15% discount amount.")

    status, res = api_request("/api/tickets/checkout", "POST", checkout_payload)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Customer checkout failed: {res}")
        return False
    payment_id = res["data"]["payment_id"]
    print(f"SUCCESS: Customer checkout generated payment intent: {payment_id}")

    # 9. Verify payment simulating successful signature verification
    pay_verify_payload = {
        "payment_id": payment_id,
        "razorpay_order_id": "order_dummy",
        "razorpay_payment_id": "pay_dummy",
        "razorpay_signature": "calculated_signature_matches_automatically_in_sandbox"
    }
    status, res = api_request("/api/payments/razorpay/verify", "POST", pay_verify_payload)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Payment verification failed: {res}")
        return False
    print("SUCCESS: Payment verified successfully. Tickets issued.")

    # 10. Creator checks dashboard (earnings and sales should be updated)
    status, res = api_request("/api/influencer/dashboard", "GET", token=creator_token)
    if status != 200 or not res.get("success"):
        print("FAIL: Creator dashboard refresh failed.")
        return False
    
    dash = res["data"]
    stats = dash["stats"]
    print(f"Creator updated stats: Tickets sold: {stats['totalTicketsSold']}, Revenue: {stats['totalRevenueGenerated']}, Approved Earnings: {stats['approvedEarnings']}, Pending: {stats['pendingEarnings']}")
    
    # Expected: 2 tickets sold. Ticket paid price = 1500 - 225 discount = 1275 per ticket.
    # Commission = 15% of 1275 = 191.25 per ticket. Total = 382.50.
    if stats["totalTicketsSold"] != 2 or float(stats["approvedEarnings"]) != 382.50:
        print(f"FAIL: Commission mapping calculation mismatch. Got: {stats}")
        return False
    print("SUCCESS: Real-time affiliate sales and commission earnings verified successfully.")

    # 11. Admin marks payout as completed for this creator profile
    status, res = api_request(f"/api/admin/influencers/payouts?profileId={profile_id}", "POST", token=admin_token)
    if status != 200 or not res.get("success"):
        print(f"FAIL: Admin payout processing failed: {res}")
        return False
    payout_id = res["data"]["id"]
    print(f"SUCCESS: Admin completed payout record: {payout_id}")

    # 12. Creator checks dashboard (earnings moved to Paid Earnings, payout history loaded)
    status, res = api_request("/api/influencer/dashboard", "GET", token=creator_token)
    if status != 200 or not res.get("success"):
        print("FAIL: Creator dashboard fetch failed post payout.")
        return False
    
    dash = res["data"]
    stats = dash["stats"]
    payouts = dash["payouts"]
    
    print(f"Creator earnings post payout: Approved: {stats['approvedEarnings']}, Paid: {stats['paidEarnings']}")
    if float(stats["approvedEarnings"]) != 0.0 or float(stats["paidEarnings"]) != 382.50 or len(payouts) != 1:
        print(f"FAIL: Payout state mapping failed. Stats: {stats}, Payouts: {payouts}")
        return False
    print("SUCCESS: Real-time Creator Dashboard and Payout History Logs verified successfully.")

    # 13. Admin checks Analytics reports and logs
    status, res = api_request("/api/admin/influencers/analytics", "GET", token=admin_token)
    if status != 200 or not res.get("success"):
        print("FAIL: Admin analytics query failed.")
        return False
    top = res["data"]["topInfluencers"]
    daily = res["data"]["dateWiseSales"]
    print(f"Analytics reports: Top influencers count: {len(top)}, Daily sales count: {len(daily)}")
    
    status, res = api_request("/api/admin/influencers/audit-logs", "GET", token=admin_token)
    if status != 200 or not res.get("success"):
        print("FAIL: Admin audit log retrieval failed.")
        return False
    logs = res["data"]
    print(f"Audit log stream length: {len(logs)}")
    print(f"Latest log action: {logs[0]['action']} - {logs[0]['details']}")

    print("\nALL INFLUENCER AFFILIATE SYSTEM INTEGRATION TESTS PASSED SUCCESSFULLY!")
    return True

if __name__ == "__main__":
    run_test()
