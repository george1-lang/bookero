package com.bookero.algorithms;

import com.bookero.common.BookingCreatedEvent;
import com.bookero.common.BookeroProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class BookingCreatedEventListener {

  private static final Logger log = LoggerFactory.getLogger(BookingCreatedEventListener.class);

  private final BookeroProperties properties;
  private final AlgorithmRunService algorithmRunService;

  public BookingCreatedEventListener(
      BookeroProperties properties,
      AlgorithmRunService algorithmRunService
  ) {
    this.properties = properties;
    this.algorithmRunService = algorithmRunService;
  }

  @TransactionalEventListener
  public void onBookingCreated(BookingCreatedEvent event) {
    if (!properties.repriceAfterBooking()) {
      return;
    }

    try {
      var algorithmKey = properties.repriceAfterBookingKey();
      algorithmRunService.execute(algorithmKey, List.of(event.flightId()));
    } catch (Exception e) {
      log.warn("Post-booking reprice failed for flight {}: {}", event.flightId(), e.getMessage());
      // Do not propagate; booking should not fail due to optional reprice
    }
  }
}
