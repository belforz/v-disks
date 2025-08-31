package com.v_disk.controller;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.service.CheckoutService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/checkout")
@Validated
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    public static class CheckoutRequestDTO {
        @NotBlank(message = "paymentId is required")
        private String paymentId;
        private Map<String, String> payload;

        public String getPaymentId() {
            return paymentId;
        }

        public void setPaymentId(String paymentId) {
            this.paymentId = paymentId;
        }

        public Map<String, String> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, String> payload) {
            this.payload = payload;
        }
    }

    @PostMapping
    public ResponseEntity<ResponseJSON<String>> save(@RequestParam String paymentId, @Valid @RequestBody CheckoutRequestDTO body) {
        Map<String, String> payload = body.getPayload();
        if (payload == null) {
            payload = Collections.emptyMap();
        }
        checkoutService.save(paymentId, payload);
        return ResponseEntity.ok(new ResponseJSON<>("success", paymentId));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseJSON<Map<String, String>>> get(@PathVariable String paymentId) {
        Map<String, String> data = checkoutService.get(paymentId);
        return ResponseEntity.ok(new ResponseJSON<>("success", data));
    }

    @DeleteMapping("/{paymentId}")
    public ResponseEntity<ResponseJSON<String>> clear(@PathVariable String paymentId) {
        checkoutService.clear(paymentId);
        return ResponseEntity.ok(new ResponseJSON<>("success", paymentId));
    }
}
