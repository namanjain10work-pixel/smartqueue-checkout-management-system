package com.smartqueue.dto;

public record DashboardStatsResponse(
        long waitingCustomers,
        long servingCustomers,
        long completedCustomers,
        long cancelledCustomers,
        long activeCustomers,
        long openCounters,
        long totalCounters,
        double averagePredictedServiceSeconds,
        double averageActualServiceSeconds,
        double meanAbsoluteErrorSeconds
) {
}