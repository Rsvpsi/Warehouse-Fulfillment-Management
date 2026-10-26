# Warehouse Project

Warehouse Order Fulfillment and Picking Optimization Platform.

## Stack

- HTML
- CSS
- JavaScript
- Java 17+
- Spring Boot 4.1
- Jakarta Servlet API with embedded Tomcat
- MySQL

## Features

- Admin registration, login, session protection, and logout
- Database-backed products, orders, inventory, customers, warehouses, and dispatches
- Create, list, edit, and delete operations for warehouse records
- Dashboard summary, low-stock, recent-order, and pending-dispatch API endpoints

## API endpoints

All `/api/*` endpoints require an authenticated admin session and return JSON.
The existing collection endpoints support `GET`, `POST`, `PUT`, and `DELETE`:

| Endpoint | Purpose |
| --- | --- |
| `/api/products` | List and manage products |
| `/api/orders` | List and manage orders |
| `/api/inventory` | List and manage inventory |
| `/api/customers` | List and manage customers |
| `/api/warehouses` | List and manage warehouses |
| `/api/dispatches` | List and manage dispatches |

Create and edit records with `application/x-www-form-urlencoded` fields. For
products, use `name`, `sku`, `price`, and `quantity`; add `id` to update an
existing record. Delete with `DELETE /api/products?id=1`. Successful creates
return HTTP `201`, successful updates and deletes return HTTP `204`, and list
requests return a JSON array.

Additional read endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/dashboard/summary` | Counts and totals for products, orders, customers, inventory, low stock, and pending dispatches |
| `GET /api/inventory/low-stock?threshold=10` | Inventory at or below the requested quantity; threshold defaults to `10` and must be non-negative |
| `GET /api/orders/recent?limit=10` | Most recent orders; limit defaults to `10` and must be between `1` and `100` |
| `GET /api/dispatches/pending` | Dispatch records whose status is `Pending` (case-insensitive) |
| `GET /api/products/low-stock?threshold=10` | Products at or below the requested quantity; threshold defaults to `10` and must be non-negative |
| `GET /api/orders/by-status?status=Pending` | Orders matching the required status (case-insensitive) |
| `GET /api/inventory/by-location?location=Main%20Warehouse` | Inventory matching the required warehouse location |

For example, after signing in, request `GET /api/inventory/low-stock?threshold=5`
with the browser's authenticated session cookie to retrieve items with five or
fewer units.

## Database setup

For a new `warehouse_db` database, create the schema and tables in MySQL:

```powershell
Get-Content -Raw src\main\webapp\WEB-INF\schema.sql | mysql -u root -p
```

For an existing `warehouse_db` created before admin name and phone were saved,
apply the additive credentials migration:

```powershell
Get-Content -Raw src\main\webapp\WEB-INF\upgrade-admin-credentials.sql | mysql -u root -p
```

By default, the app connects to `jdbc:mysql://localhost:3306/warehouse_db`
with user `root` and an empty password. Admin email, name, phone, and a
PBKDF2-SHA256 password hash are saved in `admin_credentials`. Override the
connection defaults with
`WAREHOUSE_DB_URL`, `WAREHOUSE_DB_USER`, and `WAREHOUSE_DB_PASSWORD` environment
variables.

## Build and run

Build the executable WAR with Maven:

```powershell
mvn package
```

Run it with Spring Boot's embedded Tomcat:

```powershell
java -jar target\WarehousePro.war
```

Open `http://localhost:8080/login.html`. The WAR can also be deployed to a
compatible external servlet container. The HTML pages and Java endpoints are
served together by the application; opening the files with a static HTTP
server does not run the Java endpoints.