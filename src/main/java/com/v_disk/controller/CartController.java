package com.v_disk.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.service.CartService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ResponseJSON<Map<String, Integer>>> getCart(@PathVariable @NotBlank String userId) {
        Map<String, Integer> items = cartService.listItems(userId);
        return ResponseEntity.ok(new ResponseJSON<>("success", items));
    }

    @PostMapping("/{userId}/item/{vinylId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJSON<String>> addOrUpdateItem(@PathVariable String userId, @PathVariable String vinylId, @RequestBody Map<String, Integer> body) {
        Integer qty = body.getOrDefault("quantity", 1);
        cartService.putItem(userId, vinylId, qty);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("created", "item_added_or_updated"));
    }

    @DeleteMapping("/{userId}/item/{vinylId}")
    public ResponseEntity<ResponseJSON<String>> removeItem(@PathVariable String userId, @PathVariable String vinylId) {
        cartService.removeItem(userId, vinylId);
        return ResponseEntity.ok(new ResponseJSON<>("success", "item_removed"));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ResponseJSON<String>> setCart(@PathVariable String userId, @RequestBody Map<String, Integer> items) {
        cartService.setCart(userId, items);
        return ResponseEntity.ok(new ResponseJSON<>("success", "cart_set"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ResponseJSON<String>> clearCart(@PathVariable String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(new ResponseJSON<>("success", "cart_cleared"));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<ResponseJSON<String>> createCart(@PathVariable String userId, @RequestBody Map<String, Integer> items) {
        cartService.createCart(userId, items);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("created", "cart_created"));
    }
}
