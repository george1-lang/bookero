package com.bookero.common;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Every Bookero entity carries an application-assigned identifier, so Spring Data
 * would otherwise treat each one as detached and route {@code save()} through
 * {@code merge()} — which under Hibernate 7 fails outright for a row that does not
 * exist yet, and costs a wasted SELECT for one that does. Tracking persistence
 * explicitly makes {@code save()} a plain insert for new rows.
 */
@MappedSuperclass
public abstract class AssignedIdEntity<ID> implements Persistable<ID> {

  @Transient
  private boolean persisted;

  @Override
  public boolean isNew() {
    return !persisted;
  }

  @PostPersist
  @PostLoad
  void markPersisted() {
    this.persisted = true;
  }
}
