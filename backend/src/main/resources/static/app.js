const joinForm = document.getElementById("join-form");
const refreshButton = document.getElementById("refresh-button");

joinForm.addEventListener("submit", joinQueue);
refreshButton.addEventListener("click", loadDashboard);

document.addEventListener("DOMContentLoaded", () => {
    loadDashboard();

    // Automatically refresh the dashboard every 10 seconds.
    setInterval(loadDashboard, 10000);
});

async function request(url, options = {}) {
    const response = await fetch(url, options);

    let data = null;

    try {
        data = await response.json();
    } catch (error) {
        // Some unsuccessful responses may not contain JSON.
    }

    if (!response.ok) {
        throw new Error(
            data?.message || `Request failed with status ${response.status}`
        );
    }

    return data;
}

async function loadDashboard() {
    try {
        await Promise.all([
            loadStats(),
            loadCounters(),
            loadActiveQueue(),
            loadQueueHistory(),
            loadCounterDistribution()
        ]);
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function loadStats() {
    const stats = await request("/api/dashboard/stats");

    document.getElementById("stat-active").textContent =
        stats.activeCustomers;

    document.getElementById("stat-waiting").textContent =
        stats.waitingCustomers;

    document.getElementById("stat-serving").textContent =
        stats.servingCustomers;

    document.getElementById("stat-completed").textContent =
        stats.completedCustomers;

    document.getElementById("stat-cancelled").textContent =
        stats.cancelledCustomers;

    document.getElementById("stat-counters").textContent =
        `${stats.openCounters} / ${stats.totalCounters}`;

    document.getElementById("stat-predicted").textContent =
        formatSeconds(
            Math.round(stats.averagePredictedServiceSeconds)
        );

    document.getElementById("stat-actual").textContent =
        formatSeconds(
            Math.round(stats.averageActualServiceSeconds)
        );

    document.getElementById("stat-mae").textContent =
        formatSeconds(
            Math.round(stats.meanAbsoluteErrorSeconds)
        );

    renderStatusDistribution(stats);
}

function renderStatusDistribution(stats) {
    const values = {
        waiting: stats.waitingCustomers,
        serving: stats.servingCustomers,
        completed: stats.completedCustomers,
        cancelled: stats.cancelledCustomers
    };

    // The largest value receives a full-width bar.
    const maxValue = Math.max(
        ...Object.values(values),
        1
    );

    Object.entries(values).forEach(([status, value]) => {
        const valueElement =
            document.getElementById(
                `chart-${status}-value`
            );

        const barElement =
            document.getElementById(
                `chart-${status}-bar`
            );

        valueElement.textContent = value;

        barElement.style.width =
            `${(value / maxValue) * 100}%`;
    });
}

async function loadCounters() {
    const counters = await request("/api/counters");
    const counterList = document.getElementById("counter-list");

    if (counters.length === 0) {
        counterList.innerHTML = "<p>No checkout counters found.</p>";
        return;
    }

    counterList.innerHTML = counters.map(counter => `
        <div class="counter-card">
            <div>
                <h3>${escapeHtml(counter.name)}</h3>
                <p>Counter ID: ${counter.id}</p>
            </div>

            <div class="counter-right">
    <span class="counter-status ${counter.status.toLowerCase()}">
        ${counter.status}
    </span>

    <button
        class="action-button"
        onclick="startNextCustomer(${counter.id})"
        ${counter.status !== "OPEN" ? "disabled" : ""}
    >
        Start Next
    </button>

    <button
        class="action-button ${
        counter.status === "OPEN"
            ? "close-counter-button"
            : "open-counter-button"
    }"
        onclick="toggleCounterStatus(
            ${counter.id},
            '${counter.status}'
        )"
    >
        ${counter.status === "OPEN" ? "Close" : "Open"}
    </button>
</div>
        </div>
    `).join("");
}

async function loadActiveQueue() {
    const entries = await request("/api/queue/active");
    const queueBody = document.getElementById("queue-body");
    const activeCount = document.getElementById("active-count");

    activeCount.textContent =
        `${entries.length} ${entries.length === 1 ? "customer" : "customers"}`;

    if (entries.length === 0) {
        queueBody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-state">
                    No customers are currently waiting or being served.
                </td>
            </tr>
        `;
        return;
    }

    queueBody.innerHTML = entries.map(entry => {
        const statusClass =
            entry.status === "SERVING"
                ? "status-serving"
                : "status-waiting";

        const action =
            entry.status === "SERVING"
                ? `
            <button
                class="action-button complete-button"
                onclick="completeCustomer(${entry.id})"
            >
                Complete
            </button>
          `
                : `
            <button
                class="action-button cancel-button"
                onclick="cancelCustomer(${entry.id})"
            >
                Cancel
            </button>
          `;

        return `
            <tr>
                <td><strong>${escapeHtml(entry.token)}</strong></td>
                <td>${escapeHtml(entry.customerName)}</td>
                <td>${entry.itemCount}</td>
                <td>${escapeHtml(entry.counterName)}</td>
                <td>${entry.paymentMethod}</td>

                <td>
                    <span class="status-badge ${statusClass}">
                        ${entry.status}
                    </span>
                </td>

                <td>${formatSeconds(entry.predictedServiceSeconds)}</td>
                <td>${action}</td>
            </tr>
        `;
    }).join("");
}

async function loadQueueHistory(selectedStatus = null) {

    const historyFilter =
        document.getElementById("history-filter");

    const currentStatus =
        selectedStatus !== null
            ? selectedStatus
            : historyFilter.value;

    const endpoint =
        currentStatus
            ? `/api/queue/history?status=${encodeURIComponent(currentStatus)}`
            : "/api/queue/history";

    const entries = await request(endpoint);

    const historyBody =
        document.getElementById("history-body");

    const historyCount =
        document.getElementById("history-count");

    historyCount.textContent =
        `${entries.length} ${entries.length === 1 ? "record" : "records"}`;

    if (entries.length === 0) {
        historyBody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-state">
                    No queue-history records found for this status.
                </td>
            </tr>
        `;
        return;
    }

    historyBody.innerHTML = entries.map(entry => {

        const statusClass =
            `status-${entry.status.toLowerCase()}`;

        const actualTime =
            entry.actualServiceSeconds !== null
                ? formatSeconds(entry.actualServiceSeconds)
                : "-";

        const lastEvent =
            entry.cancelledAt
            ?? entry.serviceCompletedAt
            ?? entry.serviceStartedAt
            ?? entry.arrivalTime;

        return `
            <tr>
                <td>
                    <strong>${escapeHtml(entry.token)}</strong>
                </td>

                <td>${escapeHtml(entry.customerName)}</td>

                <td>${entry.itemCount}</td>

                <td>${escapeHtml(entry.counterName)}</td>

                <td>
                    <span class="status-badge ${statusClass}">
                        ${entry.status}
                    </span>
                </td>

                <td>
                    ${formatSeconds(entry.predictedServiceSeconds)}
                </td>

                <td>${actualTime}</td>

                <td>${formatHistoryDateTime(lastEvent)}</td>
            </tr>
        `;
    }).join("");
}

async function loadCounterDistribution() {
    const entries = await request("/api/queue/history");

    const container =
        document.getElementById("counter-distribution");

    const counterCounts = entries.reduce((counts, entry) => {
        const counterName =
            entry.counterName || "Unassigned";

        counts[counterName] =
            (counts[counterName] || 0) + 1;

        return counts;
    }, {});

    const counterRows = Object.entries(counterCounts)
        .sort(([firstName], [secondName]) =>
            firstName.localeCompare(secondName)
        );

    if (counterRows.length === 0) {
        container.innerHTML = `
            <p class="empty-state">
                No counter allocation data available.
            </p>
        `;
        return;
    }

    const maxCount = Math.max(
        ...counterRows.map(([, count]) => count),
        1
    );

    container.innerHTML = counterRows
        .map(([counterName, count]) => `
            <div class="chart-row">

                <div class="chart-label">
                    <span>${escapeHtml(counterName)}</span>
                    <strong>${count}</strong>
                </div>

                <div class="chart-track">
                    <div
                        class="chart-bar chart-counter"
                        style="width: ${(count / maxCount) * 100}%"
                    ></div>
                </div>

            </div>
        `)
        .join("");
}

function formatHistoryDateTime(value) {

    if (!value) {
        return "-";
    }

    return value
        .replace("T", " ")
        .split(".")[0];
}

async function joinQueue(event) {
    event.preventDefault();

    const submitButton =
        joinForm.querySelector("button[type='submit']");

    const requestBody = {
        customerName:
            document.getElementById("customerName").value.trim(),

        itemCount:
            Number(document.getElementById("itemCount").value),

        paymentMethod:
        document.getElementById("paymentMethod").value,

        priorityType:
        document.getElementById("priorityType").value
    };

    submitButton.disabled = true;
    submitButton.textContent = "Finding best counter...";

    try {
        const result = await request("/api/queue/join", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(requestBody)
        });

        document.getElementById("result-token").textContent =
            result.token;

        document.getElementById("result-counter").textContent =
            result.counterName;

        document.getElementById("result-wait").textContent =
            formatSeconds(result.estimatedWaitSeconds);

        document
            .getElementById("assignment-result")
            .classList.remove("hidden");

        joinForm.reset();

        showMessage(
            `${result.customerName} was assigned to ${result.counterName}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Find Best Counter";
    }
}

async function startNextCustomer(counterId) {
    try {
        const result = await request(
            `/api/queue/counters/${counterId}/next`,
            {
                method: "POST"
            }
        );

        showMessage(
            `${result.token} is now being served at ${result.counterName}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function toggleCounterStatus(
    counterId,
    currentStatus
) {
    const requestedStatus =
        currentStatus === "OPEN"
            ? "CLOSED"
            : "OPEN";

    try {
        const result = await request(
            `/api/counters/${counterId}/status`,
            {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    status: requestedStatus
                })
            }
        );

        showMessage(
            `${result.name} is now ${result.status}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function completeCustomer(entryId) {
    try {
        const result = await request(
            `/api/queue/${entryId}/complete`,
            {
                method: "POST"
            }
        );

        showMessage(
            `${result.token} checkout completed in ` +
            `${formatSeconds(result.actualServiceSeconds)}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function cancelCustomer(entryId) {

    const confirmed = window.confirm(
        "Are you sure you want to cancel this waiting customer?"
    );

    if (!confirmed) {
        return;
    }

    try {
        const result = await request(
            `/api/queue/${entryId}/cancel`,
            {
                method: "POST"
            }
        );

        showMessage(
            `${result.token} was cancelled and removed from the active queue.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function formatSeconds(seconds) {
    if (seconds === null || seconds === undefined) {
        return "--";
    }

    if (seconds < 60) {
        return `${seconds} sec`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    return remainingSeconds === 0
        ? `${minutes} min`
        : `${minutes} min ${remainingSeconds} sec`;
}

function showMessage(message, type) {
    const messageBox = document.getElementById("message-box");

    messageBox.textContent = message;
    messageBox.className = `message-box message-${type}`;

    window.setTimeout(() => {
        messageBox.classList.add("hidden");
    }, 5000);
}

function escapeHtml(value) {
    const characters = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#039;"
    };

    return String(value ?? "").replace(
        /[&<>"']/g,
        character => characters[character]
    );
}