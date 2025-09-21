package com.v_disk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.v_disk.model.Order;

public interface OrderRepository extends MongoRepository<Order, String> {
	Optional<Order> findByPaymentId(String paymentId);
	List<Order> findByUserId(String userId);
}
