package com.v_disk.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v_disk.dto.checkout.CheckoutRequestDTO;
import com.v_disk.dto.order.OrderResponseDTO;
import com.v_disk.model.Order;
import com.v_disk.model.OrderItem;
import com.v_disk.repository.OrderRepository;
import com.v_disk.repository.VinylRepository;
import com.v_disk.service.CartService;
import com.v_disk.service.CheckoutService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;

//no longer being used

@RestController
@RequestMapping("/api/checkout")
@Validated
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final VinylRepository vinylRepository;

    public CheckoutController(CheckoutService checkoutService, CartService cartService, OrderRepository orderRepository, VinylRepository vinylRepository) {
        this.checkoutService = checkoutService;
        this.cartService = cartService;
        this.orderRepository = orderRepository;
        this.vinylRepository = vinylRepository;
    }

    /**
     * Simplified checkout endpoint that processes a cart directly to an order
     * 
     * @param request Contains userId and paymentId
     * @return Created order details
     */
    @PostMapping
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> checkout(@Valid @RequestBody CheckoutRequestDTO request) {
        String userId = request.userId();
        String paymentId = request.paymentId();
        
        // Check if we can create the payment marker
        boolean createdMarker = checkoutService.tryCreateMarker(paymentId);
        if (!createdMarker) {
            // Payment already being processed
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment already being processed");
        }
        
        try {
            // Get cart items for user
            Map<String, Integer> cartItems = cartService.listItems(userId);
            if (cartItems.isEmpty()) {
                checkoutService.clear(paymentId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
            }
            // Build order items from the cart (vinylId -> quantity) and copy vinyl snapshot
            List<OrderItem> items = new ArrayList<>();
            int totalQt = 0;
            for (Map.Entry<String, Integer> entry : cartItems.entrySet()) {
                String vinylId = entry.getKey();
                Integer qty = entry.getValue() != null ? entry.getValue() : 1;
                OrderItem it = new OrderItem();
                it.setVinylId(vinylId);
                it.setQuantity(qty);
                // copy vinyl details if available
                vinylRepository.findById(vinylId).ifPresent(v -> {
                    it.setTitle(v.getTitle());
                    it.setArtist(v.getArtist());
                    it.setPrice(v.getPrice());
                    it.setCoverPath(v.getCoverPath());
                });
                items.add(it);
                totalQt += qty;
            }

            // Create the order
            Order order = new Order();
            order.setUserId(userId);
            order.setItems(items);
            order.setQt(totalQt);
            order.setPaymentId(paymentId);
            order.setOrderStatus("PENDING");
            order.setIsPaymentConfirmed(false);
            order.setCreatedAt(Instant.now());
            order.setCreatedAt(Instant.now());
            
            Order savedOrder = orderRepository.save(order);
            
            // Clear the cart after successful order creation
            cartService.clearCart(userId);
            
            OrderResponseDTO response = new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getItems(),
                savedOrder.getQt(),
                savedOrder.getPaymentId(),
                savedOrder.getOrderStatus(),
                savedOrder.getIsPaymentConfirmed(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("created", response));
        } catch (Exception e) {
            // Clean up checkout marker on error
            checkoutService.clear(paymentId);
            throw e;
        }
    }
}
