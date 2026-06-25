package com.enicilion.backend.payments.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.payments.dto.RazorpayVerificationRequest;
import com.enicilion.backend.payments.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayService razorpayService;

    @PostMapping("/razorpay/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyRazorpay(
            @Valid @RequestBody RazorpayVerificationRequest request) {
        
        UUID paymentUuid = UUID.fromString(request.getPaymentId());
        
        Map<String, Object> result = razorpayService.verifyAndProcessPayment(
                paymentUuid,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
        
        if ("failed".equals(result.get("status"))) {
            throw new com.enicilion.backend.common.exception.BadValidationException("Signature verification failed");
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
