import urllib.request
import urllib.error
import time
import json
import uuid

BASE_URL = "http://localhost:4000/api"

def make_request(url, method="GET", payload=None):
    req = urllib.request.Request(url, method=method)
    if payload:
        data = json.dumps(payload).encode("utf-8")
        req.add_header("Content-Type", "application/json")
        req.data = data
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read()

def run_test():
    print("=== Testing Wallet Pass Endpoints ===")
    
    email = f"wallet_tester_{int(time.time())}@example.com"
    
    print("1. Creating User Profile & Checking Out")
    checkout_payload = {
        "items": [
            {
                "eventId": "e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1",
                "tierId": "11111111-1111-1111-1111-111111111111",
                "quantity": 1
            }
        ],
        "premiumSpiritsAcknowledged": True,
        "clientTotal": 1549,
        "idempotencyKey": f"idemp_{int(time.time())}",
        "guest_name": "Wallet Tester",
        "guest_email": email,
        "guest_phone": "9876543210"
    }
    status, headers, body = make_request(f"{BASE_URL}/tickets/checkout", "POST", checkout_payload)
    if status != 201:
        print(f"Checkout failed: {body.decode()}")
        return
    
    data = json.loads(body.decode())
    order_id = data["data"]["payment_id"]
    print(f"SUCCESS: Checkout successful. Payment ID: {order_id}")
    
    print("2. Simulating Razorpay Payment Verification")
    verify_payload = {
        "payment_id": order_id,
        "razorpay_order_id": "order_dummy",
        "razorpay_payment_id": f"pay_{int(time.time())}",
        "razorpay_signature": "calculated_signature_matches_automatically_in_sandbox"
    }
    
    status, headers, body = make_request(f"{BASE_URL}/payments/razorpay/verify", "POST", verify_payload)
    if status != 200:
        print(f"Payment verification failed: {body.decode()}")
        return
    
    tickets = json.loads(body.decode())["data"]["tickets"]
    if not tickets:
        print("No tickets found in verification response.")
        return
        
    ticket_code = tickets[0]["code"]
    print(f"SUCCESS: Payment verified. Ticket issued: {ticket_code}")
    
    print("\n3. Testing Apple Wallet (.pkpass) Endpoint")
    status, headers, body = make_request(f"{BASE_URL}/tickets/{ticket_code}/apple-wallet", "GET")
    if status == 200 and headers.get("Content-Type") == "application/vnd.apple.pkpass":
        print(f"SUCCESS: Received Apple Wallet PKPass binary stream. Length: {len(body)} bytes")
    else:
        print(f"FAILED: Apple Wallet endpoint. Status: {status}, Content-Type: {headers.get('Content-Type')}")
        
    print("\n4. Testing Google Wallet (JWT Link) Endpoint")
    status, headers, body = make_request(f"{BASE_URL}/tickets/{ticket_code}/google-wallet", "GET")
    if status == 200:
        jwt_data = json.loads(body.decode())
        save_url = jwt_data["data"]["saveUrl"]
        print(f"SUCCESS: Received Google Wallet Save Link.")
        print(f"URL: {save_url}")
        
        if "https://pay.google.com/gp/v/save/" in save_url:
            print("SUCCESS: Google Wallet JWT integration format looks correct.")
        else:
            print("FAILED: Save URL format incorrect.")
    else:
        print(f"FAILED: Google Wallet endpoint. Status: {status}")
        
    print("\nALL TESTS COMPLETED.")

if __name__ == "__main__":
    run_test()
