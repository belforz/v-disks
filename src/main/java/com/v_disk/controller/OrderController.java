package com.v_disk.controller;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v_disk.dto.order.OrderCreateDTO;
import com.v_disk.dto.order.OrderResponseDTO;
import com.v_disk.dto.order.OrderUpdateDTO;
import com.v_disk.model.Order;
import com.v_disk.repository.OrderRepository;
import com.v_disk.repository.VinylRepository;
import com.v_disk.model.Vinyl;
import com.v_disk.service.CheckoutService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository repo;
    private final VinylRepository vinylRepo;
    private final CheckoutService checkoutService;

    public OrderController(OrderRepository repo, VinylRepository vinylRepo, CheckoutService checkoutService) {
        this.repo = repo;
        this.vinylRepo = vinylRepo;
        this.checkoutService = checkoutService;
    }

    @GetMapping
    public ResponseEntity<ResponseJSON<List<OrderResponseDTO>>> list() {
        List<OrderResponseDTO> all = repo.findAll()
            .stream()
            .map(o -> new OrderResponseDTO(o.getId(), o.getUserId(), o.getVinylIds(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt()))
            .collect(Collectors.toList());
    return ResponseEntity.ok(new ResponseJSON<>("success", all));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> get(@PathVariable String id) {
        Order o = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    return ResponseEntity.ok(new ResponseJSON<>("success",  new OrderResponseDTO(o.getId(), o.getUserId(), o.getVinylIds(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> create(@RequestBody @Valid OrderCreateDTO dto) {
        Order o = new Order();
        o.setUserId(dto.userId());
        o.setVinylIds(dto.vinylIds());
        // Ensure qt is set: prefer DTO value, otherwise infer from vinylIds size, fallback to 0
        if (dto.qt() != null) {
            o.setQt(dto.qt());
        } else if (dto.vinylIds() != null) {
            o.setQt(dto.vinylIds().size());
        } else {
            o.setQt(0);
        }
        // Optional fields from DTO
        if (dto.paymentId() != null) o.setPaymentId(dto.paymentId());
        if (dto.isPaymentConfirmed() != null) o.setIsPaymentConfirmed(dto.isPaymentConfirmed());
        if (dto.orderStatus() != null) o.setOrderStatus(dto.orderStatus());
        o.setCreatedAt(Instant.now());
        Order saved = repo.save(o);
        OrderResponseDTO resp = new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getVinylIds(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("created", resp));
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> update(@PathVariable String id, @RequestBody @Valid OrderUpdateDTO dto) {
        Order o = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (dto.userId() != null) o.setUserId(dto.userId());
    if (dto.vinylIds() != null) o.setVinylIds(dto.vinylIds());
    if (dto.paymentId() != null) o.setPaymentId(dto.paymentId());
    if (dto.orderStatus() != null) o.setOrderStatus(dto.orderStatus());
    if (dto.isPaymentConfirmed() != null) o.setIsPaymentConfirmed(dto.isPaymentConfirmed());
    if (dto.qt() != null && dto.qt() > 0) o.setQt(dto.qt());
    o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
    return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getVinylIds(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseJSON<String>> delete(@PathVariable String id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        repo.deleteById(id);
    return ResponseEntity.ok(new ResponseJSON<>("success", id));
    }

    
    @PostMapping("/payment/{paymentId}/approve")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> approvePayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));

        
        boolean createdMarker = checkoutService.tryCreateMarker(paymentId);
        if (!createdMarker) {
            return ResponseEntity.ok(new ResponseJSON<>("already_processed", new OrderResponseDTO(o.getId(), o.getUserId(), o.getVinylIds(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt())));
        }

        for (String vid : o.getVinylIds()) {
            Vinyl v = vinylRepo.findById(vid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found: " + vid));
            if (v.getStock() == null || v.getStock() <= 0) {
                
                checkoutService.clear(paymentId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Out of stock for vinyl: " + vid);
            }
        }

        for (String vid : o.getVinylIds()) {
            Vinyl v = vinylRepo.findById(vid).get();
            v.setStock(v.getStock() - 1);
            v.setUpdatedAt(Instant.now());
            vinylRepo.save(v);
        }

        o.setOrderStatus("CONFIRMED");
        o.setIsPaymentConfirmed(true);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getVinylIds(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    @PostMapping("/payment/{paymentId}/fail")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> failPayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));
        o.setOrderStatus("FAILED");
        o.setIsPaymentConfirmed(false);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getVinylIds(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    @PostMapping("/payment/{paymentId}/cancel")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> cancelPayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));
        o.setOrderStatus("CANCELED");
        o.setIsPaymentConfirmed(false);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getVinylIds(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }
}
