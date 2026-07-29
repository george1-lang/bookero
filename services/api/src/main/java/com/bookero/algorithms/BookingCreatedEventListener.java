package com.bookero.algorithms;

import com.bookero.common.BookingCreatedEvent;
import com.bookero.common.BookeroProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

  @Async("repriceExecutor")
  @TransactionalEventListener
  public void onBookingCreated(BookingCreatedEvent event) {
    if (!Boolean.TRUE.equals(properties.repriceAfterBooking())) {
      return;
    }

    try {
      algorithmRunService.execute(properties.repriceAfterBookingKey(), List.of(event.flightId()));
    } catch (Exception e) {
      log.warn("post-booking reprice skipped for flight {}: {}", event.flightId(), e.getMessage());
    }
  }
}
