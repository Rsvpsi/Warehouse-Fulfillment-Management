const resources = {
    products: {
        url: "addProduct",
        table: "productTable",
        searchInput: "productSearch",
        columns: ["id", "name", "sku", "price", "quantity"],
        editParameters: { name: "productName" },
        emptyMessage: "No products found."
    },
    orders: {
        url: "api/orders",
        table: "orderTable",
        searchInput: "orderSearch",
        columns: ["id", "customer", "amount", "status"],
        emptyMessage: "No orders found."
    },
    inventory: {
        url: "api/inventory",
        table: "inventoryTable",
        searchInput: "inventorySearch",
        columns: ["id", "product", "quantity", "location"],
        emptyMessage: "No inventory found."
    },
    customers: {
        url: "api/customers",
        table: "customerTable",
        searchInput: "customerSearch",
        columns: ["id", "name", "email", "phone", "address"],
        emptyMessage: "No customers found."
    },
    warehouses: {
        url: "api/warehouses",
        table: "warehouseTable",
        searchInput: "warehouseSearch",
        columns: ["id", "name", "location", "capacity"],
        emptyMessage: "No warehouses found."
    },
    dispatches: {
        url: "api/dispatches",
        table: "dispatchTable",
        searchInput: "dispatchSearch",
        columns: ["id", "orderId", "carrier", "tracking", "status"],
        emptyMessage: "No dispatches found."
    }
};

function showForm(formId) {
    document.getElementById(formId).classList.remove("hidden");
}

function hideForm(formId) {
    document.getElementById(formId).classList.add("hidden");
}

async function request(url, options) {
    const response = await fetch(url, {
        ...options,
        headers: {
            Accept: "application/json",
            ...options?.headers
        }
    });
    if (response.status === 401) {
        window.location.href = "login.html?error=session";
        throw new Error("Your session has expired. Please sign in again.");
    }

    if (!response.ok) {
        let message = `Request failed (${response.status}).`;
        try {
            const body = await response.json();
            if (body.error) {
                message = body.error;
            } else if (body.message) {
                message = body.message;
            }
        } catch {
            if (response.status >= 500) {
                message = "The server could not complete the request. Check the database configuration.";
            }
        }
        throw new Error(message);
    }
    return response;
}

function setTableMessage(table, message, columnCount) {
    table.replaceChildren();
    const row = document.createElement("tr");
    row.dataset.message = "true";
    const cell = document.createElement("td");
    cell.colSpan = columnCount;
    cell.textContent = message;
    row.append(cell);
    table.append(row);
}

function filterTable(tableId, inputId) {
    const table = document.getElementById(tableId);
    const input = document.getElementById(inputId);
    if (!table || !input) {
        return;
    }

    table.querySelector("[data-search-message]")?.remove();
    const query = input.value.trim().toLocaleLowerCase();
    const rows = Array.from(table.rows).filter(row => row.dataset.message !== "true");
    let visibleCount = 0;

    for (const row of rows) {
        row.hidden = !row.textContent.toLocaleLowerCase().includes(query);
        if (!row.hidden) {
            visibleCount++;
        }
    }

    if (rows.length > 0 && visibleCount === 0) {
        const row = document.createElement("tr");
        row.dataset.searchMessage = "true";
        const cell = document.createElement("td");
        cell.colSpan = table.closest("table").tHead.rows[0].cells.length;
        cell.textContent = "No matching records found.";
        row.append(cell);
        table.append(row);
    }
}

function searchTable(event, tableId, inputId) {
    event.preventDefault();
    filterTable(tableId, inputId);
    return false;
}

async function loadResource(name) {
    const resource = resources[name];
    const table = document.getElementById(resource.table);
    if (!table) {
        return;
    }

    const columnCount = resource.columns.length + 1;
    try {
        const response = await request(resource.url);
        const records = await response.json();
        table.replaceChildren();
        if (records.length === 0) {
            setTableMessage(table, resource.emptyMessage, columnCount);
            return;
        }

        for (const record of records) {
            const row = document.createElement("tr");
            for (const column of resource.columns) {
                const cell = document.createElement("td");
                let value = record[column];
                if (value != null && name === "products" && column === "price") {
                    value = `₹${value}`;
                } else if (value != null && name === "orders" && column === "amount") {
                    value = `₹${value}`;
                }
                cell.textContent = value == null ? "" : String(value);
                row.append(cell);
            }

            const actionCell = document.createElement("td");
            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "delete-btn";
            deleteButton.textContent = "Delete";
            deleteButton.addEventListener("click", () => deleteRecord(name, record.id));
            const editButton = document.createElement("button");
            editButton.type = "button";
            editButton.className = "edit-btn";
            editButton.textContent = "Edit";
            editButton.addEventListener("click", () => startEdit(name, record, row));
            actionCell.append(editButton);
            actionCell.append(deleteButton);
            row.append(actionCell);
            table.append(row);
        }
        filterTable(resource.table, resource.searchInput);
    } catch (error) {
        console.error(`Failed to load ${name}:`, error);
        setTableMessage(table, error.message, columnCount);
    }
}

function startEdit(name, record, row) {
    const resource = resources[name];

    for (let index = 0; index < resource.columns.length; index++) {
        const column = resource.columns[index];
        if (column === "id") {
            continue;
        }

        const input = document.createElement("input");
        const parameter = resource.editParameters?.[column] || column;
        input.type = ["amount", "price"].includes(parameter) ? "number"
            : ["quantity", "capacity", "orderId"].includes(parameter) ? "number" : "text";
        if (input.type === "number") {
            input.min = parameter === "orderId" ? "1" : "0";
            if (["amount", "price"].includes(parameter)) {
                input.step = "0.01";
            } else {
                input.step = "1";
            }
        }
        input.required = true;
        input.value = record[column] == null ? "" : String(record[column]);
        input.setAttribute("aria-label", `Edit ${column}`);
        input.dataset.editParameter = parameter;
        const cell = row.cells[index];
        cell.replaceChildren(input);
    }

    const actionCell = row.cells[resource.columns.length];
    actionCell.replaceChildren();
    const saveButton = document.createElement("button");
    saveButton.type = "button";
    saveButton.className = "save-btn";
    saveButton.textContent = "Save";
    saveButton.addEventListener("click", () => saveEdit(name, record.id, row));
    const cancelButton = document.createElement("button");
    cancelButton.type = "button";
    cancelButton.className = "cancel-edit-btn";
    cancelButton.textContent = "Cancel";
    cancelButton.addEventListener("click", () => loadResource(name));
    actionCell.append(saveButton, cancelButton);
}

async function saveEdit(name, id, row) {
    const resource = resources[name];
    const inputs = Array.from(row.querySelectorAll("[data-edit-parameter]"));
    const invalidInput = inputs.find(input => !input.reportValidity());
    if (invalidInput) {
        return;
    }

    const body = new URLSearchParams();
    for (const input of inputs) {
        body.set(input.dataset.editParameter, input.value.trim());
    }

    body.set("id", String(id));
    try {
        await request(resource.url, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body
        });
        await loadResource(name);
    } catch (error) {
        window.alert(error.message);
    }
}

async function deleteRecord(name, id) {
    const resource = resources[name];
    if (!window.confirm("Delete this record?")) {
        return;
    }

    try {
        await request(`${resource.url}?id=${encodeURIComponent(id)}`, { method: "DELETE" });
        await loadResource(name);
    } catch (error) {
        window.alert(error.message);
    }
}

async function saveRecord(event, name, form) {
    event.preventDefault();
    const status = getFormStatus(form);
    status.textContent = "";

    try {
        const body = new URLSearchParams(new FormData(form));
        await request(resources[name].url, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body
        });
        form.reset();
        hideForm(form.closest(".form-box").id);
        await loadResource(name);
    } catch (error) {
        status.textContent = error.message;
    }
}

function getFormStatus(form) {
    let status = form.parentElement.querySelector(".form-message");
    if (!status) {
        status = document.createElement("p");
        status.className = "form-message";
        status.setAttribute("role", "status");
        status.setAttribute("aria-live", "polite");
        form.after(status);
    }
    return status;
}

async function loadSelectOptions(id, url, placeholder, labelForRecord, valueForRecord = record => record.id) {
    const select = document.getElementById(id);
    if (!select) {
        return;
    }

    try {
        const response = await request(url);
        const records = await response.json();
        select.replaceChildren();
        const emptyOption = document.createElement("option");
        emptyOption.value = "";
        emptyOption.textContent = placeholder;
        select.append(emptyOption);

        for (const record of records) {
            const option = document.createElement("option");
            option.value = valueForRecord(record);
            option.textContent = labelForRecord(record);
            select.append(option);
        }
    } catch (error) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = error.message;
        option.disabled = true;
        select.replaceChildren(option);
    }
}

function addOrder(event) {
    return saveRecord(event, "orders", event.currentTarget);
}

function addInventory(event) {
    return saveRecord(event, "inventory", event.currentTarget);
}

function addCustomer(event) {
    return saveRecord(event, "customers", event.currentTarget);
}

function addWarehouse(event) {
    return saveRecord(event, "warehouses", event.currentTarget);
}

function addDispatch(event) {
    return saveRecord(event, "dispatches", event.currentTarget);
}

function loadProducts() {
    return loadResource("products");
}

function loadOrders() {
    return loadResource("orders");
}

function loadInventory() {
    return loadResource("inventory");
}

function loadCustomers() {
    return loadResource("customers");
}

function loadWarehouses() {
    return loadResource("warehouses");
}

function loadDispatches() {
    return loadResource("dispatches");
}

document.addEventListener("DOMContentLoaded", () => {
    for (const name of Object.keys(resources)) {
        if (document.getElementById(resources[name].table)) {
            loadResource(name);
        }
    }

    loadSelectOptions("orderCustomer", "api/customers", "Choose customer",
        customer => `${customer.name} (${customer.email || "no email"})`,
        customer => customer.name);
    loadSelectOptions("inventoryProduct", "addProduct", "Choose product",
        product => product.name, product => product.name);
    loadSelectOptions("inventoryLocation", "api/warehouses", "Choose warehouse",
        warehouse => `${warehouse.name} — ${warehouse.location || "No location"}`,
        warehouse => `${warehouse.name} — ${warehouse.location || "No location"}`);
    loadSelectOptions("dispatchOrder", "api/orders", "Choose order",
        order => `#${order.id} — ${order.customer}`);

    const loginMessage = document.getElementById("loginMessage");
    if (loginMessage) {
        const query = new URLSearchParams(window.location.search);
        if (query.get("error") === "invalid") {
            loginMessage.textContent = "Invalid email or password.";
        } else if (query.get("error") === "session") {
            loginMessage.textContent = "Your session expired. Please sign in again.";
        } else if (query.get("registered") === "1") {
            loginMessage.textContent = "Account created. You can now sign in.";
        }
    }

    const signupForm = document.getElementById("signupForm");
    if (signupForm) {
        signupForm.addEventListener("submit", async event => {
            event.preventDefault();
            const message = document.getElementById("signupMessage");
            const submitButton = signupForm.querySelector('button[type="submit"]');
            const phone = document.getElementById("phone").value.trim();

            if (!/^\d{10}$/.test(phone)) {
                message.textContent = "Please enter a 10-digit phone number without spaces or a country code.";
                return;
            }

            message.textContent = "";
            submitButton.disabled = true;
            try {
                const response = await fetch(signupForm.action, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
                    },
                    body: new URLSearchParams(new FormData(signupForm))
                });

                if (response.ok && response.redirected
                    && new URL(response.url).searchParams.get("registered") === "1") {
                    window.location.assign(response.url);
                    return;
                }

                if (response.status === 409) {
                    message.textContent = "An account with this email already exists. Sign in with that email or use another one.";
                } else if (response.status === 400) {
                    message.textContent = "Check your name, valid email, 10-digit phone number, and password (at least 8 characters).";
                } else if (response.status >= 500) {
                    message.textContent = "The account could not be saved. The server could not write to the database; please try again or contact the administrator.";
                } else {
                    message.textContent = `Account creation failed (HTTP ${response.status}). Please check your details and try again.`;
                }
            } catch (error) {
                console.error("Admin signup request failed:", error);
                message.textContent = "Could not reach the server. Check the connection and try again.";
            } finally {
                submitButton.disabled = false;
            }
        });
    }
});
