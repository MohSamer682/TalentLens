package com.resumescreener.common.reschedule;

/** Only a pending request can be answered, and every answer is final. */
public enum RescheduleStatus {
    PENDING, ACCEPTED, DECLINED, WITHDRAWN;

    public RescheduleStatus moveTo(RescheduleStatus target) {
        if (this != PENDING || target == PENDING) {
            throw new IllegalStateException("A " + this + " request can't become " + target);
        }
        return target;
    }
}
