package com.healthcare.payment.repository;

import com.healthcare.payment.model.PaymentSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentSessionRepository extends MongoRepository<PaymentSession, String> {

    Optional<PaymentSession> findByStripeSessionId(String stripeSessionId);

    Optional<PaymentSession> findByConsultationId(String consultationId);

    List<PaymentSession> findByPatientIdOrderByCreatedAtDesc(String patientId);

    List<PaymentSession> findAllByOrderByCreatedAtDesc();
}
