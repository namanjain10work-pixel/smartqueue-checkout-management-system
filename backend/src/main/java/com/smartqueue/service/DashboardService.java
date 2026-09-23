package com.smartqueue.service;
import com.smartqueue.dto.DashboardStatsResponse;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {
    private final QueueEntryRepository queueEntryRepository;
    private final CheckoutCounterRepository checkoutCounterRepository;

    public DashboardService(
            QueueEntryRepository queueEntryRepository,
            CheckoutCounterRepository checkoutCounterRepository
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.checkoutCounterRepository = checkoutCounterRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {

        long waitingCustomers =
                queueEntryRepository.countByStatus(QueueStatus.WAITING);

        long servingCustomers =
                queueEntryRepository.countByStatus(QueueStatus.SERVING);

        long completedCustomers =
                queueEntryRepository.countByStatus(QueueStatus.COMPLETED);

        long activeCustomers =
                waitingCustomers + servingCustomers;

        long totalCounters =
                checkoutCounterRepository.count();

        long openCounters =
                checkoutCounterRepository
                        .findByStatus(CounterStatus.OPEN)
                        .size();

        List<QueueEntry> allEntries =
                queueEntryRepository.findAll();

        double averagePredictedServiceSeconds =
                allEntries.stream()
                        .mapToInt(QueueEntry::getPredictedServiceSeconds)
                        .average()
                        .orElse(0.0);

        List<QueueEntry> completedEntries =
                allEntries.stream()
                        .filter(entry ->
                                entry.getStatus() == QueueStatus.COMPLETED
                                        && entry.getActualServiceSeconds() != null
                        )
                        .toList();

        double averageActualServiceSeconds =
                completedEntries.stream()
                        .mapToInt(QueueEntry::getActualServiceSeconds)
                        .average()
                        .orElse(0.0);

        double meanAbsoluteErrorSeconds =
                completedEntries.stream()
                        .mapToDouble(entry ->
                                Math.abs(
                                        entry.getActualServiceSeconds()
                                                - entry.getPredictedServiceSeconds()
                                )
                        )
                        .average()
                        .orElse(0.0);

        return new DashboardStatsResponse(
                waitingCustomers,
                servingCustomers,
                completedCustomers,
                activeCustomers,
                openCounters,
                totalCounters,
                roundToOneDecimal(averagePredictedServiceSeconds),
                roundToOneDecimal(averageActualServiceSeconds),
                roundToOneDecimal(meanAbsoluteErrorSeconds)
        );
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
