package com.vikrant.careSync.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class RazorpayService {
    
    private final RazorpayClient razorpayClient;
    private final String keySecret;
    private final String merchantUpiId;
    
    public RazorpayService(
            @Value("${app.payment.razorpay.key-id}") String keyId,
            @Value("${app.payment.razorpay.key-secret}") String keySecret,
            @Value("${app.payment.merchant.upi-id}") String merchantUpiId) throws RazorpayException {
        
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.keySecret = keySecret;
        this.merchantUpiId = merchantUpiId;
        
        log.info("Razorpay service initialized successfully");
    }
    
    /**
     * Create Razorpay order (Mock implementation for testing)
     */
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        // Mock implementation - generate a fake order ID for testing
        String mockOrderId = "order_mock_" + System.currentTimeMillis();
        log.info("Mock Razorpay order created. Order ID: {}, Receipt: {}, Amount: {}", mockOrderId, receipt, amount);
        return mockOrderId;
        
        /* Uncomment this block when you have real Razorpay credentials
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Convert to paise
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", 1); // Auto capture
            
            Order order = razorpayClient.orders.create(orderRequest);
            
            log.info("Razorpay order created successfully. Order ID: {}, Receipt: {}", order.get("id"), receipt);
            return order.get("id");
            
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for receipt: {}", receipt, e);
            throw new RuntimeException("Failed to create payment order: " + e.getMessage());
        }
        */
    }
    
    /**
     * Get UPI payment URL
     */
    public String getUpiPaymentUrl(String orderId, String upiId) {
        // Generate UPI payment URL with the merchant UPI ID
        return String.format("upi://pay?pa=%s&pn=CareSync&cu=INR&tn=Payment%%20for%%20Order%%20%s", 
                merchantUpiId, orderId);
    }
    
    /**
     * Get UPI payment URL with amount
     */
    public String getUpiPaymentUrlWithAmount(String orderId, String upiId, BigDecimal amount) {
        // Generate UPI payment URL with amount and merchant UPI ID
        return String.format("upi://pay?pa=%s&pn=CareSync&am=%.2f&cu=INR&tn=Payment%%20for%%20Order%%20%s", 
                merchantUpiId, amount, orderId);
    }
    
    /**
     * Get card payment URL
     */
    public String getCardPaymentUrl(String orderId) {
        // Return Razorpay checkout URL or order ID for frontend integration
        return "https://checkout.razorpay.com/v1/checkout.js?order_id=" + orderId;
    }
    
    /**
     * Generate QR code data
     */
    public String generateQrCode(String orderId) {
        // Generate UPI QR code data with merchant UPI ID
        return String.format("upi://pay?pa=%s&pn=CareSync&cu=INR&tn=Payment%%20for%%20Order%%20%s", 
                merchantUpiId, orderId);
    }
    
    /**
     * Generate QR code data with amount
     */
    public String generateQrCodeWithAmount(String orderId, BigDecimal amount) {
        // Generate UPI QR code data with amount and merchant UPI ID
        return String.format("upi://pay?pa=%s&pn=CareSync&am=%.2f&cu=INR&tn=Payment%%20for%%20Order%%20%s", 
                merchantUpiId, amount, orderId);
    }
    
    /**
     * Verify payment signature
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            
            return Utils.verifyPaymentSignature(attributes, keySecret);
            
        } catch (RazorpayException e) {
            log.error("Failed to verify payment signature. Order: {}, Payment: {}", orderId, paymentId, e);
            return false;
        }
    }
    
    /**
     * Get payment details from Razorpay
     */
    public Map<String, Object> getPaymentDetails(String paymentId) {
        try {
            com.razorpay.Payment payment = razorpayClient.payments.fetch(paymentId);
            return payment.toJson().toMap();
            
        } catch (RazorpayException e) {
            log.error("Failed to fetch payment details for payment ID: {}", paymentId, e);
            throw new RuntimeException("Failed to fetch payment details: " + e.getMessage());
        }
    }
    
    /**
     * Refund payment
     */
    public String refundPayment(String paymentId, BigDecimal amount, String reason) {
        try {
            JSONObject refundRequest = new JSONObject();
            if (amount != null) {
                refundRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Convert to paise
            }
            if (reason != null) {
                refundRequest.put("notes", new JSONObject().put("reason", reason));
            }
            
            com.razorpay.Payment payment = razorpayClient.payments.fetch(paymentId);
            com.razorpay.Refund refund = razorpayClient.payments.refund(paymentId, refundRequest);
            
            log.info("Refund initiated successfully. Refund ID: {}, Payment ID: {}", refund.get("id"), paymentId);
            return refund.get("id");
            
        } catch (RazorpayException e) {
            log.error("Failed to initiate refund for payment ID: {}", paymentId, e);
            throw new RuntimeException("Failed to initiate refund: " + e.getMessage());
        }
    }
    
    /**
     * Get order details
     */
    public Map<String, Object> getOrderDetails(String orderId) {
        try {
            Order order = razorpayClient.orders.fetch(orderId);
            return order.toJson().toMap();
            
        } catch (RazorpayException e) {
            log.error("Failed to fetch order details for order ID: {}", orderId, e);
            throw new RuntimeException("Failed to fetch order details: " + e.getMessage());
        }
    }
    
    /**
     * Capture payment (if not auto-captured)
     */
    public void capturePayment(String paymentId, BigDecimal amount) {
        try {
            JSONObject captureRequest = new JSONObject();
            captureRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Convert to paise
            
            com.razorpay.Payment payment = razorpayClient.payments.fetch(paymentId);
            razorpayClient.payments.capture(paymentId, captureRequest);
            
            log.info("Payment captured successfully. Payment ID: {}", paymentId);
            
        } catch (RazorpayException e) {
            log.error("Failed to capture payment ID: {}", paymentId, e);
            throw new RuntimeException("Failed to capture payment: " + e.getMessage());
        }
    }
    
    /**
     * Generate webhook signature for verification
     */
    public String generateWebhookSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate webhook signature", e);
            throw new RuntimeException("Failed to generate webhook signature: " + e.getMessage());
        }
    }
    
    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            String expectedSignature = generateWebhookSignature(payload, secret);
            return expectedSignature.equals(signature);
            
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }
}