package com.v_disk.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orders")
public class Order {
    private String id;
    private String userId;
    private List<String> vinylIds;
    private int qt;
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

   public List<String> getVinylIds() {
       return vinylIds;
   }

   public int getQt() {
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

   public void setVinylIds(List<String> vinylIds) {
       this.vinylIds = vinylIds;
   }

   public void setQt(int qt) {
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
