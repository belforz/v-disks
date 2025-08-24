package com.v_disk.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import com.v_disk.model.Order;

public interface OrderRepository extends MongoRepository<Order, String> {
	Optional<Order> findByPaymentId(String paymentId);
}
