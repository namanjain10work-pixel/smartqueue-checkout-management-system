package com.smartqueue.service;

import com.smartqueue.model.*;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests: no Spring context, database, or Python server is started. */
class QueueRulesTest {
    private CheckoutCounterRepository counters;
    private QueueEntryRepository entries;
    private CounterAllocator allocator;
    private FairQueueSelector selector;
    private QueueService service;

    @BeforeEach
    void setUp() {
        counters = mock(CheckoutCounterRepository.class);
        entries = mock(QueueEntryRepository.class);
        allocator = new CounterAllocator(counters, entries);
        selector = new FairQueueSelector();
        service = new QueueService(entries, mock(CheckoutTimePredictor.class),
                allocator, selector);
    }

    @Test
    void rejectsAllocationWhenNoCountersAreOpen() {
        when(counters.findByStatus(CounterStatus.OPEN)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, allocator::allocateCounter);
        verifyNoInteractions(entries);
    }

    @Test
    void selectsLowestWorkloadRatherThanFewestCustomers() {
        CheckoutCounter first = counter(1L);
        CheckoutCounter second = counter(2L);
        when(counters.findByStatus(CounterStatus.OPEN))
                .thenReturn(List.of(first, second));
        load(first, List.of(entry(first, PriorityType.REGULAR, 400)));
        load(second, List.of(entry(second, PriorityType.REGULAR, 60),
                entry(second, PriorityType.REGULAR, 70)));

        var result = allocator.allocateCounter();
        assertSame(second, result.counter());
        assertEquals(130L, result.estimatedWaitSeconds());
    }

    @Test
    void equalWorkloadsChooseLowerCounterIdRegardlessOfRepositoryOrder() {
        CheckoutCounter first = counter(1L);
        CheckoutCounter second = counter(2L);
        when(counters.findByStatus(CounterStatus.OPEN))
                .thenReturn(List.of(second, first));
        load(first, List.of());
        load(second, List.of());

        var result = allocator.allocateCounter();
        assertSame(first, result.counter());
        assertEquals(0L, result.estimatedWaitSeconds());
    }

    @Test
    void overdueServingCustomerCannotProduceNegativeWorkload() {
        CheckoutCounter first = counter(1L);
        QueueEntry overdue = entry(first, PriorityType.REGULAR, 60);
        overdue.startService();
        ReflectionTestUtils.setField(overdue, "serviceStartedAt",
                LocalDateTime.now().minusMinutes(5));
        when(counters.findByStatus(CounterStatus.OPEN)).thenReturn(List.of(first));
        load(first, List.of(overdue, entry(first, PriorityType.REGULAR, 90)));

        assertEquals(90L, allocator.allocateCounter().estimatedWaitSeconds());
    }

    @Test
    void servingWorkloadSubtractsElapsedTime() {
        CheckoutCounter first = counter(1L);
        QueueEntry serving = entry(first, PriorityType.REGULAR, 600);
        serving.startService();
        ReflectionTestUtils.setField(serving, "serviceStartedAt",
                LocalDateTime.now().minusSeconds(120));
        when(counters.findByStatus(CounterStatus.OPEN)).thenReturn(List.of(first));
        load(first, List.of(serving));

        long remaining = allocator.allocateCounter().estimatedWaitSeconds();
        // Allows scheduling delay without requiring an exact wall-clock result.
        assertTrue(remaining >= 450 && remaining <= 480,
                "Expected about 480 seconds remaining, got " + remaining);
    }

    @Test
    void rejectsSelectionFromEmptyQueue() {
        assertThrows(IllegalStateException.class,
                () -> selector.selectNext(List.of()));
    }

    @Test
    void regularCustomersAreSelectedInArrivalOrder() {
        CheckoutCounter counter = counter(1L);
        QueueEntry oldest = entry(counter, PriorityType.REGULAR, 100);
        QueueEntry newer = entry(counter, PriorityType.REGULAR, 100);
        assertSame(oldest, selector.selectNext(List.of(oldest, newer)));
    }

    @Test
    void assistanceCustomerCanJumpTwoPositions() {
        CheckoutCounter counter = counter(1L);
        QueueEntry first = entry(counter, PriorityType.REGULAR, 100);
        QueueEntry second = entry(counter, PriorityType.REGULAR, 100);
        QueueEntry assistance = entry(counter, PriorityType.ASSISTANCE, 100);
        assertSame(assistance,
                selector.selectNext(List.of(first, second, assistance)));
    }

    @Test
    void assistanceCustomerCannotJumpThreePositions() {
        CheckoutCounter counter = counter(1L);
        QueueEntry first = entry(counter, PriorityType.REGULAR, 100);
        assertSame(first, selector.selectNext(List.of(first,
                entry(counter, PriorityType.REGULAR, 100),
                entry(counter, PriorityType.REGULAR, 100),
                entry(counter, PriorityType.ASSISTANCE, 100))));
    }

    @Test
    void longWaitingRegularCustomerIsProtectedFromPriorityJump() {
        CheckoutCounter counter = counter(1L);
        QueueEntry oldest = entry(counter, PriorityType.REGULAR, 100);
        ReflectionTestUtils.setField(oldest, "assignedAt",
                LocalDateTime.now().minusMinutes(11));
        assertSame(oldest, selector.selectNext(List.of(oldest,
                entry(counter, PriorityType.ASSISTANCE, 100))));
    }

    @Test
    void serviceStartsAndCompletesCustomerWithTimingRecorded() {
        CheckoutCounter counter = counter(1L);
        QueueEntry customer = entry(counter, PriorityType.REGULAR, 100);
        ReflectionTestUtils.setField(customer, "id", 10L);
        customer.setToken("SQ-0010");
        when(entries.findFirstByCounterIdAndStatus(1L, QueueStatus.SERVING))
                .thenReturn(Optional.empty());
        when(entries.findByCounterIdAndStatusOrderByAssignedAtAsc(
                1L, QueueStatus.WAITING)).thenReturn(List.of(customer));
        when(entries.save(any(QueueEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(entries.findById(10L)).thenReturn(Optional.of(customer));

        service.startNextCustomer(1L);
        assertEquals(QueueStatus.SERVING, customer.getStatus());
        assertNotNull(customer.getServiceStartedAt());
        service.completeCustomer(10L);
        assertEquals(QueueStatus.COMPLETED, customer.getStatus());
        assertNotNull(customer.getServiceCompletedAt());
        assertTrue(customer.getActualServiceSeconds() >= 1);
        verify(entries, times(2)).save(customer);
    }

    @Test
    void cannotStartAnotherCustomerAtBusyCounter() {
        QueueEntry serving = entry(counter(1L), PriorityType.REGULAR, 100);
        serving.startService();
        when(entries.findFirstByCounterIdAndStatus(1L, QueueStatus.SERVING))
                .thenReturn(Optional.of(serving));

        assertThrows(IllegalStateException.class,
                () -> service.startNextCustomer(1L));
        verify(entries, never()).save(any(QueueEntry.class));
    }

    @Test
    void cancellingWaitingCustomerRecordsCancellationAndBlocksService() {
        QueueEntry customer = entry(counter(1L), PriorityType.REGULAR, 100);
        ReflectionTestUtils.setField(customer, "id", 10L);
        customer.setToken("SQ-0010");
        when(entries.findById(10L)).thenReturn(Optional.of(customer));
        when(entries.save(customer)).thenReturn(customer);

        service.cancelCustomer(10L);
        assertEquals(QueueStatus.CANCELLED, customer.getStatus());
        assertNotNull(customer.getCancelledAt());
        assertThrows(IllegalStateException.class, customer::startService);
        assertThrows(IllegalStateException.class, customer::completeService);
        assertThrows(IllegalStateException.class, customer::cancel);
    }

    @Test
    void cannotCompleteWaitingCustomerOrCancelServingCustomer() {
        QueueEntry customer = entry(counter(1L), PriorityType.REGULAR, 100);
        when(entries.findById(10L)).thenReturn(Optional.of(customer));
        assertThrows(IllegalStateException.class,
                () -> service.completeCustomer(10L));
        assertEquals(QueueStatus.WAITING, customer.getStatus());

        customer.startService();
        assertThrows(IllegalStateException.class,
                () -> service.cancelCustomer(10L));
        assertEquals(QueueStatus.SERVING, customer.getStatus());
        verify(entries, never()).save(any(QueueEntry.class));
    }

    @Test
    void missingCustomerCannotBeCompletedOrCancelled() {
        when(entries.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.completeCustomer(999L));
        assertThrows(IllegalStateException.class,
                () -> service.cancelCustomer(999L));
        verify(entries, never()).save(any(QueueEntry.class));
    }

    private CheckoutCounter counter(long id) {
        CheckoutCounter counter = new CheckoutCounter("Counter " + id, CounterStatus.OPEN);
        ReflectionTestUtils.setField(counter, "id", id);
        return counter;
    }

    private QueueEntry entry(CheckoutCounter counter, PriorityType priority, int seconds) {
        return new QueueEntry("Test Customer", 15, PaymentMethod.UPI, priority,
                seconds, "RULE_BASELINE_V1", counter);
    }

    private void load(CheckoutCounter counter, List<QueueEntry> customers) {
        when(entries.findByCounterIdAndStatusInOrderByAssignedAtAsc(counter.getId(),
                List.of(QueueStatus.WAITING, QueueStatus.SERVING)))
                .thenReturn(customers);
    }
}
