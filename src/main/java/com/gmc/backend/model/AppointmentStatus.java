package com.gmc.backend.model;

public enum AppointmentStatus {
    PENDING,      // shown as "REQUESTED" in the UI
    APPROVED,     // admin approved (replaces CONFIRMED)
    RESCHEDULED,  // admin moved it to a new date/time
    CANCELLED,
    COMPLETED
}
