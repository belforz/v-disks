package com.v_disk.controller;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
import com.v_disk.model.Vinyl;
import com.v_disk.model.OrderItem;
import com.v_disk.repository.OrderRepository;
import com.v_disk.repository.VinylRepository;
import com.v_disk.service.CheckoutService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderRepository repo;
    private final VinylRepository vinylRepo;
    private final CheckoutService checkoutService;
    private final JavaMailSender mailSender;
    private final com.v_disk.repository.UserRepository userRepo;

    public OrderController(OrderRepository repo, VinylRepository vinylRepo, CheckoutService checkoutService, JavaMailSender mailSender, com.v_disk.repository.UserRepository userRepo) {
        this.repo = repo;
        this.vinylRepo = vinylRepo;
        this.checkoutService = checkoutService;
        this.mailSender = mailSender;
        this.userRepo = userRepo;
    }

    
    @GetMapping
    public ResponseEntity<ResponseJSON<List<OrderResponseDTO>>> list() {
        List<OrderResponseDTO> all = repo.findAll()
            .stream()
            .map(o -> new OrderResponseDTO(o.getId(), o.getUserId(), o.getItems(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt()))
            .collect(Collectors.toList());
    return ResponseEntity.ok(new ResponseJSON<>("success", all));
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> get(@PathVariable String id) {
        Order o = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    return ResponseEntity.ok(new ResponseJSON<>("success",  new OrderResponseDTO(o.getId(), o.getUserId(), o.getItems(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt())));
    }

    
    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> create(@RequestBody @Valid OrderCreateDTO dto) {
        Order o = new Order();
        o.setUserId(dto.userId());
        if (dto.items() != null) {
            o.setItems(dto.items());
        }
        if (dto.qt() != null) {
            o.setQt(dto.qt());
        } else if (o.getItems() != null) {
            int total = o.getItems().stream().mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 1).sum();
            o.setQt(total);
        } else {
            o.setQt(0);
        }
       
        if (dto.paymentId() != null) o.setPaymentId(dto.paymentId());
        if (dto.isPaymentConfirmed() != null) o.setIsPaymentConfirmed(dto.isPaymentConfirmed());
        if (dto.orderStatus() != null) o.setOrderStatus(dto.orderStatus());
        o.setCreatedAt(Instant.now());
    
        if (o.getItems() != null) {
            for (OrderItem it : o.getItems()) {
                if ((it.getTitle() == null || it.getTitle().isBlank()) && it.getVinylId() != null) {
                    vinylRepo.findById(it.getVinylId()).ifPresent(v -> {
                        it.setTitle(v.getTitle());
                        it.setArtist(v.getArtist());
                        it.setPrice(v.getPrice());
                        it.setCoverPath(v.getCoverPath());
                    });
                }
            }
        }

        Order saved = repo.save(o);
        OrderResponseDTO resp = new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getItems(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt());

       
        try {
            userRepo.findById(saved.getUserId()).ifPresent(u -> {
                if (u.getEmail() != null && !u.getEmail().isBlank()) {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setTo(u.getEmail());
                    msg.setSubject("Order confirmation " + saved.getId());
                    String itemsText = "";
                    if (saved.getItems() != null) {
                        itemsText = saved.getItems().stream()
                            .map(it -> (it.getTitle() != null ? it.getTitle() : it.getVinylId()) + " (x" + it.getQuantity() + ")")
                            .collect(Collectors.joining("\n"));
                    }
                    msg.setText("Your order has been receveid. Request Order: " + saved.getId() + "\nItems:\n" + itemsText);
                    msg.setFrom("no-reply@v-disk.local");
                    mailSender.send(msg);
                }
            });
        } catch (Exception e) {
            log.warn("Error sending your order {}: {}", saved.getId(), e.getMessage());
        }

    return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("created", resp));
    }
    
    
    @PatchMapping("/{id}")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> update(@PathVariable String id, @RequestBody @Valid OrderUpdateDTO dto) {
        Order o = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (dto.userId() != null) o.setUserId(dto.userId());
    if (dto.items() != null) o.setItems(dto.items());
    if (dto.paymentId() != null) o.setPaymentId(dto.paymentId());
    if (dto.orderStatus() != null) o.setOrderStatus(dto.orderStatus());
    if (dto.isPaymentConfirmed() != null) o.setIsPaymentConfirmed(dto.isPaymentConfirmed());
    if (dto.qt() != null && dto.qt() > 0) o.setQt(dto.qt());
    o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
    return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getItems(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseJSON<String>> delete(@PathVariable String id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        repo.deleteById(id);
    return ResponseEntity.ok(new ResponseJSON<>("success", id));
    }

    
    @GetMapping("/by-customer/{userId}")
    public ResponseEntity<ResponseJSON<List<OrderResponseDTO>>> listByCustomer(@PathVariable String userId) {
        List<OrderResponseDTO> all = repo.findByUserId(userId).stream().map(o -> new OrderResponseDTO(o.getId(), o.getUserId(), o.getItems(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt())).collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseJSON<>("success", all));
    }

    
    @PostMapping("/payment/{paymentId}/approve")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> approvePayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));

        boolean createdMarker = checkoutService.tryCreateMarker(paymentId);
        if (!createdMarker) {
            return ResponseEntity.ok(new ResponseJSON<OrderResponseDTO>("already_processed", new OrderResponseDTO(o.getId(), o.getUserId(), o.getItems(), o.getQt(), o.getPaymentId(), o.getOrderStatus(), o.getIsPaymentConfirmed(), o.getCreatedAt(), o.getUpdatedAt())));
        }

        if (o.getItems() != null) {
            for (OrderItem orderedItem : o.getItems()) {
                String vid = orderedItem.getVinylId();
                Vinyl v = vinylRepo.findById(vid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found: " + vid));
                Integer needed = orderedItem.getQuantity() != null ? orderedItem.getQuantity() : 1;
                if (v.getStock() == null || v.getStock() < needed) {
                    checkoutService.clear(paymentId);
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Out of stock for vinyl: " + vid);
                }
            }

            for (OrderItem orderedItem : o.getItems()) {
                String vid = orderedItem.getVinylId();
                Integer needed = orderedItem.getQuantity() != null ? orderedItem.getQuantity() : 1;
                Vinyl v = vinylRepo.findById(vid).get();
                v.setStock(v.getStock() - needed);
                v.setUpdatedAt(Instant.now());
                vinylRepo.save(v);
            }
        }

        o.setOrderStatus("CONFIRMED");
        o.setIsPaymentConfirmed(true);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getItems(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    @PostMapping("/payment/{paymentId}/fail")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> failPayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));
        o.setOrderStatus("FAILED");
        o.setIsPaymentConfirmed(false);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getItems(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }

    @PostMapping("/payment/{paymentId}/cancel")
    public ResponseEntity<ResponseJSON<OrderResponseDTO>> cancelPayment(@PathVariable String paymentId) {
        Order o = repo.findByPaymentId(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for paymentId"));
        o.setOrderStatus("CANCELED");
        o.setIsPaymentConfirmed(false);
        o.setUpdatedAt(Instant.now());
        Order saved = repo.save(o);
        return ResponseEntity.ok(new ResponseJSON<>("success", new OrderResponseDTO(saved.getId(), saved.getUserId(), saved.getItems(), saved.getQt(), saved.getPaymentId(), saved.getOrderStatus(), saved.getIsPaymentConfirmed(), saved.getCreatedAt(), saved.getUpdatedAt())));
    }
}
