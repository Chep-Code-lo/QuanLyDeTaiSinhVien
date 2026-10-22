package com.example.qldetai.feature.communication.email;

public record EmailDeliveryClaim(Long id, String token) {
    @Override
    public String toString() { return "EmailDeliveryClaim[id=" + id + ", token=REDACTED]"; }
}
