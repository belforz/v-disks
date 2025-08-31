package com.v_disk.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.service.CheckoutService;
import com.v_disk.utils.ResponseJSON;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<ResponseJSON<String>> save(@RequestBody Map<String, Object> body) {
        // Expect at least a paymentId and optional payload
        Object pid = body.get("paymentId");
        if (pid == null) {
            return ResponseEntity.badRequest().body(new ResponseJSON<>("error", "missing paymentId"));
        }
        String paymentId = String.valueOf(pid);
        Object payloadObj = body.get("payload");
        Map<String, String> payload = Map.of();
        if (payloadObj instanceof Map) {
            payload = ((Map<?, ?>) payloadObj).entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue())));
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
