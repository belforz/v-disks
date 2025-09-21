package com.v_disk.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orders")
public class Order {
    private String id;
    private String userId;
    private List<com.v_disk.model.OrderItem> items;
    private Integer qt;
    private String paymentId;
    private Boolean isPaymentConfirmed;
    private String orderStatus;
    private Instant createdAt;
    private Instant updatedAt;

   public String getId() {
       return id;
   }

   public String getUserId() {
       return userId;
   }

   public List<com.v_disk.model.OrderItem> getItems() {
       return items;
   }

   public Integer getQt() {
       return qt;
   }

   public String getPaymentId() {
       return paymentId;
   }

   public String getOrderStatus() {
       return orderStatus;
   }

   public Boolean getIsPaymentConfirmed() {
       return isPaymentConfirmed;
   }

   public Instant getCreatedAt() {
       return createdAt;
   }

   public Instant getUpdatedAt() {
       return updatedAt;
   }

   public void setId(String id) {
       this.id = id;
   }

   public void setUserId(String userId) {
       this.userId = userId;
   }

   public void setItems(List<com.v_disk.model.OrderItem> items) {
       this.items = items;
       if (items != null) {
           this.qt = items.stream().filter(Objects::nonNull).map(item -> Optional.ofNullable(item.getQuantity()).orElse(1)).reduce(0, Integer::sum);
       }
   }

   public void setQt(Integer qt) {
       this.qt = qt;
   }

   public void setPaymentId(String paymentId) {
       this.paymentId = paymentId;
   }

   public void setOrderStatus(String orderStatus) {
       this.orderStatus = orderStatus;
   }

   public void setIsPaymentConfirmed(Boolean isPaymentConfirmed) {
       this.isPaymentConfirmed = isPaymentConfirmed;
   }

   public void setCreatedAt(Instant createdAt) {
       this.createdAt = createdAt;
   }

   public void setUpdatedAt(Instant updatedAt) {
       this.updatedAt = updatedAt;
   }
}
