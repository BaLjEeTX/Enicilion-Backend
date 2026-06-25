import os
from fpdf import FPDF

class BusinessLogicPDF(FPDF):
    def header(self):
        if self.page_no() > 1:
            self.set_font('helvetica', 'B', 8)
            self.set_text_color(100, 100, 100)
            self.cell(90, 10, 'ENICILION PLATFORM - BUSINESS LOGIC GUIDE', 0, 0, 'L')
            self.cell(90, 10, 'MotorScape 2026', 0, 1, 'R')
            self.set_draw_color(200, 200, 200)
            self.set_line_width(0.2)
            self.line(15, 17, 195, 17)
            self.ln(3)

    def footer(self):
        if self.page_no() > 1:
            self.set_y(-15)
            self.set_font('helvetica', 'I', 8)
            self.set_text_color(120, 120, 120)
            self.cell(0, 10, f'Page {self.page_no()} of {{nb}}', 0, 0, 'C')

def add_section_header(pdf, title):
    pdf.set_font('helvetica', 'B', 12)
    pdf.set_text_color(26, 54, 93) # Deep Slate Blue
    pdf.ln(4)
    pdf.cell(0, 8, title, 0, 1, 'L')
    pdf.set_draw_color(26, 54, 93)
    pdf.set_line_width(0.4)
    pdf.line(15, pdf.get_y(), 195, pdf.get_y())
    pdf.ln(3)

def add_subsection_header(pdf, title):
    pdf.set_font('helvetica', 'B', 10)
    pdf.set_text_color(160, 64, 0) # Muted Orange/Accent
    pdf.ln(2)
    pdf.cell(0, 6, title, 0, 1, 'L')
    pdf.ln(1)

def add_body_paragraph(pdf, text):
    pdf.set_font('helvetica', '', 9.5)
    pdf.set_text_color(40, 40, 40) # Charcoal
    pdf.multi_cell(0, 5, text)
    pdf.ln(2)

def add_bullet_point(pdf, title, description):
    pdf.set_font('helvetica', 'B', 9.5)
    pdf.set_text_color(40, 40, 40)
    pdf.write(5, "-  ")
    pdf.set_font('helvetica', 'B', 9.5)
    pdf.write(5, title + ": ")
    pdf.set_font('helvetica', '', 9.5)
    pdf.write(5, description + "\n")
    pdf.ln(1.5)

def create_pdf(output_path):
    pdf = BusinessLogicPDF(orientation='P', unit='mm', format='A4')
    pdf.alias_nb_pages()
    pdf.set_margins(15, 20, 15)
    pdf.set_auto_page_break(auto=True, margin=20)
    
    # Page 1: Cover Page
    pdf.add_page()
    
    # Decorative header block (dark blue)
    pdf.set_fill_color(26, 54, 93) # Deep Navy
    pdf.rect(0, 0, 210, 80, 'F')
    
    # Gold accent line
    pdf.set_fill_color(201, 168, 76) # Gold Accent
    pdf.rect(0, 80, 210, 4, 'F')
    
    pdf.set_y(25)
    pdf.set_font('helvetica', 'B', 28)
    pdf.set_text_color(255, 255, 255)
    pdf.cell(0, 15, 'ENICILION', 0, 1, 'C')
    pdf.set_font('helvetica', 'B', 14)
    pdf.cell(0, 10, 'MotorScape 2026 Event Management Platform', 0, 1, 'C')
    
    pdf.set_y(95)
    pdf.set_text_color(40, 40, 40)
    pdf.set_font('helvetica', 'B', 22)
    pdf.cell(0, 12, 'Comprehensive Business Logic', 0, 1, 'C')
    pdf.cell(0, 12, '& Client Workflow Guide', 0, 1, 'C')
    
    pdf.ln(15)
    pdf.set_font('helvetica', '', 11)
    pdf.set_text_color(80, 80, 80)
    pdf.multi_cell(0, 6, 
        "This document provides a non-technical, user-friendly overview of the "
        "Enicilion platform's business logic. It outlines the core checkout rules, "
        "anti-fraud gate mechanisms, mobile wallet integrations, influencer affiliate marketing, "
        "and drifter showcase workflows configured for the MotorScape 2026 event.",
        align='C'
    )
    
    pdf.set_y(220)
    pdf.set_draw_color(200, 200, 200)
    pdf.line(30, 215, 180, 215)
    
    pdf.set_font('helvetica', 'B', 10)
    pdf.set_text_color(60, 60, 60)
    pdf.cell(0, 6, 'Prepared for: Enicilion Stakeholders & Project Sponsor', 0, 1, 'C')
    pdf.set_font('helvetica', '', 9)
    pdf.cell(0, 5, 'Status: Final Production Specifications', 0, 1, 'C')
    pdf.cell(0, 5, 'Date: June 2026 | Version 1.0.0', 0, 1, 'C')
    
    # Page 2: Table of Contents & Executive Summary
    pdf.add_page()
    
    add_section_header(pdf, "Table of Contents")
    pdf.ln(2)
    
    toc_items = [
        ("1. Executive Summary & Event Concept", 2),
        ("2. Spectator Ticketing & Booking Rules", 2),
        ("3. Driver Applications & Showcase Cars", 3),
        ("4. Mobile Pass Integration (Apple & Google Wallet)", 4),
        ("5. Secure Gate Admission & Fraud Prevention", 4),
        ("6. Influencer Affiliate Program & Dashboard", 5),
        ("7. Payment Processing & Mock Sandbox", 6),
        ("8. Platform Operational Architecture Summary", 6)
    ]
    for title, page in toc_items:
        pdf.set_font('helvetica', 'B', 10)
        pdf.set_text_color(50, 50, 50)
        pdf.cell(150, 8, title, 0, 0)
        pdf.set_font('helvetica', '', 10)
        pdf.cell(0, 8, f"Page {page}", 0, 1, 'R')
    pdf.ln(8)
    
    add_section_header(pdf, "1. Executive Summary & Event Concept")
    add_body_paragraph(pdf, 
        "Enicilion is a state-of-the-art, high-performance event management platform specifically "
        "customized for MotorScape 2026. MotorScape 2026 is a premium automotive festival that "
        "combines professional drifting, vehicle displays, and commercial spectator zones. "
        "To deliver a premium experience, the platform caters to two primary visitor segments:"
    )
    
    add_bullet_point(pdf, "Spectators", 
        "General event-goers who purchase tickets across multiple tiers (General Admission, VIP Access, etc.) "
        "to watch the drift showcases, access food courts, and experience the festival grounds.")
    add_bullet_point(pdf, "Drivers / Drifters", 
        "Automotive enthusiasts who apply to showcase their modified drift or street cars at the event. "
        "They undergo a curated approval process managed by event coordinators.")
        
    add_body_paragraph(pdf, 
        "The platform bridges these requirements by integrating ticket booking, automated car classification, "
        "mobile wallet issuing, real-time ticket scanning, and an affiliate marketing engine for social media influencers "
        "to drive ticket sales.")
        
    # Page 3: Spectator Ticketing & Checkout
    pdf.add_page()
    add_section_header(pdf, "2. Spectator Ticketing & Booking Rules")
    add_body_paragraph(pdf,
        "The spectator booking engine is designed to ensure a smooth, error-free checkout process while "
        "protecting event inventory from duplicate bookings or double-selling. Below are the key business rules "
        "governing ticket selection and purchasing:"
    )
    
    add_subsection_header(pdf, "2.1. Real-Time Inventory Protection (Double-Booking Block)")
    add_body_paragraph(pdf,
        "For popular events, hundreds of tickets might be purchased simultaneously. To prevent the platform from "
        "overselling space (e.g., selling 505 tickets when only 500 spots exist), the system uses an instant inventory locking "
        "mechanism. The exact millisecond a customer initiates checkout, the system reserves their tickets temporarily. "
        "If another customer is looking at the same ticket tier, they are prevented from buying that same ticket if it would "
        "exceed capacity, ensuring the venue capacity limits are strictly respected."
    )
    
    add_subsection_header(pdf, "2.2. Automated Booking Discounts & Coupons")
    add_body_paragraph(pdf,
        "To maximize sales and support bulk orders, the platform evaluates pricing rules dynamically:"
    )
    add_bullet_point(pdf, "Group Booking Discount", 
        "If a customer purchases five (5) or more tickets of the same tier in a single cart, the system automatically "
        "applies a 10% discount to that specific ticket line item. This rewards group bookings instantly without requiring coupon codes.")
    add_bullet_point(pdf, "Promotional Coupons", 
        "Customers can input promotional or affiliate coupon codes. These codes grant a customizable discount "
        "(e.g., 10% off for generic coupons, or 15% off for approved influencer affiliates). Coupon discounts are calculated "
        "only on coupon-eligible ticket types, excluding any items that already received the bulk group discount.")
        
    add_subsection_header(pdf, "2.3. Transparent Platform Convenience Fees")
    add_body_paragraph(pdf,
        "To cover platform operations and payment gateway costs, the system appends a standard, transparent service fee "
        "of INR 49.00 per access ticket. This is clearly detailed during checkout so the buyer sees a full breakdown of: "
        "Ticket Subtotal - Bulk Discount - Coupon Discount + Platform Service Fee."
    )
    
    # Page 4: Driver Applications & Showcase Cars
    pdf.add_page()
    add_section_header(pdf, "3. Driver Applications & Showcase Cars")
    add_body_paragraph(pdf,
        "Driver showcases are the core attraction of MotorScape 2026. The platform provides a portal where drifters "
        "and collectors apply to showcase their vehicles. The workflow is optimized to save time for both drivers "
        "and administrators:"
    )
    
    add_subsection_header(pdf, "3.1. Detailed Application & Vehicle Submission")
    add_body_paragraph(pdf,
        "Applicants submit a digital form specifying their vehicle details (Make, Model, Modifications, Engine Power) "
        "and upload high-resolution images of their cars. To ensure the website loads fast for visitors while preserving "
        "car detail, the platform automatically compresses high-res uploads (up to 10MB) into modern WebP images. "
        "This reduces photo file sizes by up to 80% without losing visual clarity."
    )
    
    add_subsection_header(pdf, "3.2. Intelligent Automotive Classification Engine")
    add_body_paragraph(pdf,
        "When an application is submitted, an intelligent vehicle classification system automatically tags the car based "
        "on its specifications:"
    )
    add_bullet_point(pdf, "JDM Class", 
        "Cars matching famous Japanese domestic models, such as Toyota Supra, Nissan Skyline/Silvia/GTR, Mazda RX-7, etc.")
    add_bullet_point(pdf, "Euro Class", 
        "Cars manufactured by European brands like BMW, Mercedes-Benz, Porsche, Audi, or Volkswagen.")
    add_bullet_point(pdf, "Modified Class", 
        "Vehicles featuring extensive aftermarket modifications (e.g. stage tuning, turbo swaps, widebody kits, roll cages).")
    add_bullet_point(pdf, "Stock Class", 
        "Standard factory specifications suitable for regular exhibition.")
        
    add_body_paragraph(pdf,
        "This auto-tagging allows administrators to instantly sort applications and plan physical parking zones at the "
        "venue without having to manually review and tag every vehicle details from scratch."
    )
    
    # Page 5: Mobile Pass Integration & Gate Verification
    pdf.add_page()
    add_section_header(pdf, "4. Mobile Pass Integration (Apple & Google Wallet)")
    add_body_paragraph(pdf,
        "To provide a premium and friction-free experience, Enicilion eliminates the need to print physical tickets. "
        "Upon successful purchase, spectators receive links to save their passes directly into their mobile devices:"
    )
    add_bullet_point(pdf, "Apple Wallet (.pkpass)", 
        "iPhone users can download a native digital pass that displays a stylized ticket with the event branding, "
        "owner name, category color badges (e.g., Red for VIP), and a scannable gate code.")
    add_bullet_point(pdf, "Google Wallet (JWT Save Link)", 
        "Android users are presented with a secure 'Save to Google Wallet' link. Clicking this instantly adds the "
        "ticket pass to their Google Wallet app, ensuring it is available offline at the gate.")
        
    pdf.ln(4)
    add_section_header(pdf, "5. Secure Gate Admission & Fraud Prevention")
    add_body_paragraph(pdf,
        "To prevent ticket cloning, gate congestion, and unauthorized entries, the platform implements a high-security "
        "validation workflow at the gate entrance:"
    )
    
    add_subsection_header(pdf, "5.1. Cryptographic Security Codes")
    add_body_paragraph(pdf,
        "Every issued ticket contains a unique, random code generated using cryptographic security libraries (e.g., MS26-XXXX-XXXX). "
        "These codes cannot be guessed or predicted by fraudsters, ensuring only legitimate buyers can generate valid passes."
    )
    
    add_subsection_header(pdf, "5.2. Double-Scan & Ticket Cloning Protection")
    add_body_paragraph(pdf,
        "When a ticket code is scanned at a gate terminal:"
    )
    add_bullet_point(pdf, "First Scan", 
        "The system verifies the ticket is paid, changes its status to 'Checked In', records the entry time, and allows the guest in.")
    add_bullet_point(pdf, "Duplicate Scan", 
        "If the same ticket code is scanned again, the gate terminal displays a warning: 'Already scanned!' along with the original guest's name.")
    add_bullet_point(pdf, "Fraud / Clone Block", 
        "If a ticket code is scanned three (3) or more times, the system flags it as an attempted fraud (e.g., a cloned or shared pass). "
        "The platform automatically cancels and blocks the ticket entirely, updates the real-time gate network, and rejects "
        "all future entry attempts for that code.")
        
    add_subsection_header(pdf, "5.3. Live Database Synchronization")
    add_body_paragraph(pdf,
        "The check-in systems sync gate scans instantly with the cloud database. If a spectator tries to scan a duplicate "
        "pass at Gate A and Gate B simultaneously, both gates will know instantly, ensuring zero security leaks."
    )
    
    # Page 6: Creator Affiliate Hub
    pdf.add_page()
    add_section_header(pdf, "6. Influencer Affiliate Program & Dashboard")
    add_body_paragraph(pdf,
        "A key marketing channel for MotorScape 2026 is our social media creator program. Influencers and creators can "
        "join the platform to promote tickets and earn commissions. The platform automates this process end-to-end:"
    )
    
    add_subsection_header(pdf, "6.1. Creator Onboarding & Approval")
    add_body_paragraph(pdf,
        "Influencers apply via the online Creator Hub, submitting their social handles, follower counts, and payment/UPI details. "
        "Event administrators review applications and approve them, instantly generating a custom coupon code (e.g. DRIFT15) "
        "and setting their commission parameters (e.g. 15% discount for their audience and 15% cash payout for the creator)."
    )
    
    add_subsection_header(pdf, "6.2. Interactive Creator Dashboard")
    add_body_paragraph(pdf,
        "Approved creators get access to a private, real-time statistics dashboard where they track their success:"
    )
    add_bullet_point(pdf, "Total Tickets Sold", "The total number of spectator tickets purchased using their custom coupon code.")
    add_bullet_point(pdf, "Total Revenue Generated", "The gross monetary sales driven by the creator's promotion.")
    add_bullet_point(pdf, "Approved Earnings", "Commission earned from completed purchases that are ready to be paid out.")
    add_bullet_point(pdf, "Pending Earnings", "Commissions from purchases that are still awaiting final processing.")
    
    add_subsection_header(pdf, "6.3. Real-Time Earnings Calculations")
    add_body_paragraph(pdf,
        "The system calculates commission based on the actual price paid by the customer, not the original price. "
        "For example, if a VIP ticket costs INR 1,500 and the influencer's coupon gives 15% off, the customer pays INR 1,275. "
        "The influencer's 15% commission is calculated on INR 1,275, yielding INR 191.25 per ticket. All calculations occur "
        "instantly at the moment of payment verification, updating the creator's dashboard live."
    )
    
    add_subsection_header(pdf, "6.4. Secure Administrative Settlements")
    add_body_paragraph(pdf,
        "Administrators can process payouts directly from the admin panel. Once a payout is marked as complete, "
        "the creator's dashboard moves those earnings from 'Approved' to 'Paid' status, and logs a detailed payout "
        "history record for transparent bookkeeping."
    )
    
    # Page 7: Payments, Sandbox & Summary
    pdf.add_page()
    add_section_header(pdf, "7. Payment Processing & Mock Sandbox")
    add_body_paragraph(pdf,
        "To facilitate secure payments, Enicilion integrates directly with Razorpay, a leading payment gateway. "
        "For testing, training, and client demonstrations, the platform is equipped with an intelligent payment sandbox:"
    )
    add_bullet_point(pdf, "Mock Sandbox Mode", 
        "Allows users, creators, and organizers to test the full checkout flow without entering real credit cards. "
        "The sandbox automatically validates payment signatures and simulates a successful transaction in under a second, "
        "instantly generating test tickets and mobile passes.")
    add_bullet_point(pdf, "Idempotency Protection", 
        "To prevent duplicate credit card charges if a client double-clicks the checkout button, the system uses unique transaction tokens. "
        "If a second checkout request with the same token arrives, the system safely ignores it and serves the existing payment session.")
        
    add_section_header(pdf, "8. Platform Operational Architecture Summary")
    add_body_paragraph(pdf,
        "The Enicilion platform runs on a robust, enterprise-grade architecture that ensures high speed, reliability, and security:"
    )
    
    pdf.ln(2)
    # Let's draw a nice box for the architecture components
    pdf.set_fill_color(240, 240, 240)
    pdf.set_draw_color(200, 200, 200)
    # Draw box at current Y position
    start_y = pdf.get_y()
    pdf.rect(15, start_y, 180, 48, 'FD')
    
    pdf.set_y(start_y + 3)
    pdf.set_font('helvetica', 'B', 10)
    pdf.set_text_color(26, 54, 93)
    pdf.cell(0, 5, '    KEY ARCHITECTURAL HIGHLIGHTS', 0, 1, 'L')
    pdf.ln(1)
    
    pdf.set_font('helvetica', '', 9.5)
    pdf.set_text_color(50, 50, 50)
    highlights = [
        "Cloud Database Locking: Prevents overselling popular event ticket categories during peak traffic.",
        "Instant Mobile Sync: Syncs gate admissions in real time to prevent duplicate entries.",
        "Automated Creator Ledger: Tracks dynamic coupon payouts, commissions, and payout records.",
        "Responsive Admin Panel: Gives administrators an overview of drift cars, ticket counts, and sales analytics."
    ]
    for highlight in highlights:
        pdf.cell(10, 5, '', 0, 0)
        pdf.cell(0, 5, "-  " + highlight, 0, 1, 'L')
    
    pdf.set_y(start_y + 54)
    pdf.set_font('helvetica', 'I', 10)
    pdf.set_text_color(100, 100, 100)
    pdf.cell(0, 10, '--- End of Document ---', 0, 1, 'C')
    
    pdf.output(output_path)
    print(f"PDF successfully generated at: {output_path}")

if __name__ == "__main__":
    output_dir = "/Users/baljeetsingh/Downloads"
    os.makedirs(output_dir, exist_ok=True)
    target_path = os.path.join(output_dir, "enicilion_business_logic.pdf")
    create_pdf(target_path)
