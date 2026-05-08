# TraceSpring — HTTP Request Inspector Lab

An educational Spring Boot application that **visualizes the full lifecycle of an HTTP request inside Spring MVC** — every stage, every component, every millisecond — in a live browser dashboard.

---

## The Problem It Solves

Spring MVC processes every HTTP request through a layered pipeline: servlet filters, a dispatcher, interceptors, AOP proxies, and finally your controller. In normal development you only ever see the controller. The rest is invisible.

TraceSpring makes that pipeline visible. Every request gets a unique ID and a timestamp at each stage. The dashboard shows the full lifecycle in real time — so you can see exactly what Spring calls, in what order, and how long each stage takes.

This is not a production monitoring tool. It is a **learning and debugging instrument** designed to build an accurate mental model of Spring MVC internals.

---

## What It Shows

For every incoming HTTP request, TraceSpring captures and displays:

| What | Detail |
|---|---|
| **Unique request ID** | UUID assigned at the servlet filter boundary — before Spring MVC touches the request |
| **HTTP method & path** | Captured before any routing happens |
| **Request headers** | All headers, extracted at filter entry |
| **Query parameters** | Extracted at filter entry |
| **Handler method** | `ClassName#methodName` of the matched `@RestController` method |
| **HTTP response status** | Captured after controller execution |
| **Total execution time** | Wall-clock ms from filter entry to response flush |
| **Per-stage timestamps** | Absolute clock time and `+Nms` offset from request start for all 7 lifecycle stages |
| **Exception info** | Class name and message if the controller or interceptor threw |

---

## The 7 Lifecycle Stages

Each request records timestamps at these stages, in this exact order:

```
1  FILTER_START                  ← OncePerRequestFilter body begins
2  REQUEST_RECEIVED              ← requestId assigned, wrappers attached, trace saved
3  INTERCEPTOR_PRE_HANDLE        ← HandlerInterceptor.preHandle() fires
4  CONTROLLER_EXECUTION          ← @RestController method body starts (via AOP)
5  INTERCEPTOR_POST_HANDLE       ← HandlerInterceptor.postHandle() fires
6  INTERCEPTOR_AFTER_COMPLETION  ← HandlerInterceptor.afterCompletion() fires (always)
7  RESPONSE_SENT                 ← filter finally block flushes response to wire
```

The delta between stages reveals where time is spent. The ordering reveals how Spring's dispatch cycle actually works, which is different from how most documentation describes it.

---

## How the Visualization Works

### Live Dashboard

Open `http://localhost:8082/debug/dashboard` in a browser.

The dashboard polls `/debug/traces` every 2 seconds and renders all captured requests. No page reload needed.

**Stats bar** — four live counters at the top:
- Total requests in memory
- Average response time (completed requests only)
- Requests in the last 60 seconds
- Error rate (4xx + 5xx as % of completed)

**Trace table** — one row per request, newest first:

```
Request ID   Method   Path              Status   Handler                    Time    Stages
a3f2bc01…    GET      /api/users        200      UserController#getUsers    12ms    7 / 7
40059fdc…    POST     /api/users        201      UserController#createUser  46ms    7 / 7
00897363…    GET      /api/orders/42    400      OrderController#getOrder   3ms     7 / 7
```

Click any row to expand the full lifecycle timeline for that request:

```
● FILTER_START                    02:20:55.236   +0ms
  ↓
● REQUEST_RECEIVED                02:20:55.236   +0ms
  ↓
● INTERCEPTOR_PRE_HANDLE          02:20:55.237   +1ms
  ↓
● CONTROLLER_EXECUTION            02:20:55.240   +4ms
  ↓
● INTERCEPTOR_POST_HANDLE         02:20:55.247   +11ms
  ↓
● INTERCEPTOR_AFTER_COMPLETION    02:20:55.247   +11ms
  ↓
● RESPONSE_SENT                   02:20:55.248   +12ms
```

Request headers and query params are shown alongside the timeline.

**Controls:**
- **⏸ Pause** — stops live polling; rows freeze. Click **▶ Resume** to restart (fetches immediately on resume)
- **⚡ Generate Traffic** — fires 12 staggered requests covering GET, POST, PUT, PATCH, DELETE, and status codes 200, 201, 204, 400, 404 — instant mixed-method view without any manual curl
- **⌫ Clear view** — hides current rows, resets `sessionStart` so counts restart from zero; server-side data is not deleted
- **Guide ▾** — opens a three-column reference panel inside the dashboard (see below)

### In-Dashboard Guide

Clicking "Guide" opens a panel with:

1. **How It Works** — a visual lifecycle flow diagram showing each Spring component (Filter → DispatcherServlet → Interceptor → AOP Aspect → Controller → Interceptor → Filter) with the stage badge recorded at each point
2. **Stage Reference** — all 7 stages with description, purpose, and which Java class records it
3. **Legend & Column Reference** — HTTP method badge colours, status code badge colours, what every table column means, and the list of all available endpoints

### Raw JSON API

If you prefer the data without the UI:

```bash
# all traces
curl http://localhost:8082/debug/traces | jq .

# single trace by requestId
curl http://localhost:8082/debug/traces/{requestId} | jq .
```

---

## How It Works — Architecture

### Component Map

```
src/main/java/com/tracespring/
│
├── filter/
│   └── RequestTracingFilter        OncePerRequestFilter — outermost layer
│
├── interceptor/
│   └── LifecycleInterceptor        HandlerInterceptor — inside DispatcherServlet
│
├── aspect/
│   └── ControllerTraceAspect       @Around AOP — fires at exact controller entry
│
├── model/
│   ├── LifecycleStage              Enum of the 7 stages
│   └── RequestTrace                Thread-safe per-request data holder
│
├── service/
│   └── TraceStore                  ConcurrentHashMap<requestId, RequestTrace>
│
├── wrapper/
│   ├── CachingRequestWrapper       Re-readable request body (extends HttpServletRequestWrapper)
│   └── CachingResponseWrapper      Interceptable response body (extends HttpServletResponseWrapper)
│
├── config/
│   └── WebMvcConfig                Registers LifecycleInterceptor via WebMvcConfigurer
│
└── controller/
    ├── UserController              GET, POST, PUT, PATCH, DELETE /api/users[/{id}]
    ├── OrderController             GET /api/orders/{id}
    └── DebugController             GET /debug/traces, /debug/traces/{id}, /debug/dashboard
```

### Request Flow in Detail

**1. Filter (`RequestTracingFilter`)**

Every request enters here first — before `DispatcherServlet`, before any Spring MVC routing. The filter:
- Generates a UUID `requestId`
- Wraps the `HttpServletRequest` in `CachingRequestWrapper` so the body can be read multiple times (normally an `InputStream` is single-read — the filter would drain it before the controller could parse JSON)
- Wraps the `HttpServletResponse` in `CachingResponseWrapper` so the response body can be inspected before being written to the wire
- Saves the `requestId` as a request attribute so downstream components (interceptor, aspect) can look up the same trace
- Records `FILTER_START` and `REQUEST_RECEIVED`, saves the `RequestTrace` to `TraceStore`
- Calls `chain.doFilter()` — Spring MVC takes over from here
- In the `finally` block (runs whether or not an exception occurred): records `RESPONSE_SENT`, flushes the cached response body to the actual wire, calls `trace.complete()` which computes total execution time

**2. Interceptor (`LifecycleInterceptor`)**

Registered via `WebMvcConfig`, fires inside `DispatcherServlet` on every matched request:

- `preHandle()` — fires before the controller. Reads `requestId` from the request attribute, looks up the trace, records `INTERCEPTOR_PRE_HANDLE`, and extracts the handler method name using Spring's `HandlerMethod` introspection
- `postHandle()` — fires after the controller returns successfully. Records `INTERCEPTOR_POST_HANDLE`. **Not called if the controller throws** — use `afterCompletion` for guaranteed teardown
- `afterCompletion()` — always fires, even on exception. Records `INTERCEPTOR_AFTER_COMPLETION`. If `ex` is non-null, saves exception class and message to the trace

**3. AOP Aspect (`ControllerTraceAspect`)**

The interceptor fires *around* the controller but cannot pinpoint the exact instant the controller method body starts. The `@Around` advice on `@RestController` can.

`RequestContextHolder.getRequestAttributes()` retrieves the current `HttpServletRequest` from a thread-local, reads the `requestId` attribute, and records `CONTROLLER_EXECUTION` before calling `pjp.proceed()`.

This is the only component that records the precise entry time into your business logic.

**4. Trace Store (`TraceStore`)**

A `ConcurrentHashMap<String, RequestTrace>` backed `@Service`. The filter writes on entry; the interceptor and aspect update fields on the same instance via the `requestId` key. Lock-free reads on the hot path. All traces live in JVM memory — restart clears them.

**5. Request/Response Wrappers**

`CachingRequestWrapper` reads the raw `InputStream` once into a `byte[]` on construction, then vends a fresh `ByteArrayInputStream` on every subsequent `getInputStream()` call. Without this, a Spring `@RequestBody` parser would consume the stream before the filter could log the body.

`CachingResponseWrapper` intercepts all `getOutputStream()` and `getWriter()` calls and tees the bytes into a `ByteArrayOutputStream`. The filter's `finally` block calls `copyBodyToResponse()` to flush the buffer to the real response stream. Without this, body interception would corrupt streaming responses.

---

## Sample Endpoints

These exist purely to generate traffic with different shapes for observing the lifecycle:

| Method | Path | Status | Purpose |
|---|---|---|---|
| `GET` | `/api/users` | 200 | List all users |
| `GET` | `/api/users/{id}` | 200 / 404 | Single user — 404 if not found |
| `POST` | `/api/users` | 201 | Create user; tests body capture through caching wrapper |
| `PUT` | `/api/users/{id}` | 200 / 404 | Full replace — observe same lifecycle for write ops |
| `PATCH` | `/api/users/{id}` | 200 / 404 | Partial update |
| `DELETE` | `/api/users/{id}` | 204 / 404 | Delete — 204 No Content on success |
| `GET` | `/api/orders/{id}` | 200 / 400 | Returns 400 if `id ≤ 0` — useful for observing error traces |

The **⚡ Generate Traffic** button in the dashboard fires all of these in one burst, including intentional 400 and 404 responses, so you see mixed methods and status codes immediately.

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
git clone <repo>
cd TraceSpring
mvn spring-boot:run
```

App starts on port **8082**.

### Use

```bash
# open the dashboard
open http://localhost:8082/debug/dashboard

# quickest path: click "⚡ Generate Traffic" in the dashboard
# — fires 12 mixed requests automatically

# or drive traffic manually
curl http://localhost:8082/api/users
curl http://localhost:8082/api/users/1
curl -X POST http://localhost:8082/api/users \
     -H "Content-Type: application/json" \
     -d '{"name":"Carol","email":"carol@example.com"}'
curl -X PUT http://localhost:8082/api/users/1 \
     -H "Content-Type: application/json" \
     -d '{"name":"Alice Updated","email":"alice@example.com","role":"SUPERADMIN"}'
curl -X PATCH http://localhost:8082/api/users/2 \
     -H "Content-Type: application/json" \
     -d '{"role":"MODERATOR"}'
curl -X DELETE http://localhost:8082/api/users/1
curl http://localhost:8082/api/orders/42
curl http://localhost:8082/api/orders/-1     # triggers 400

# raw trace JSON
curl http://localhost:8082/debug/traces | jq .
```

Watch the dashboard update in real time. Click any row to see the per-stage timeline.

---

## Log Output

Every lifecycle stage is also logged to the console via SLF4J:

```
02:20:55.236 INFO  [nio-8082-exec-1] c.t.filter.RequestTracingFilter     : [FILTER START   ] GET /api/users | requestId=118b263f-...
02:20:55.248 INFO  [nio-8082-exec-1] c.t.interceptor.LifecycleInterceptor: [INTERCEPTOR PRE ] handler=UserController#getUsers | requestId=118b263f-...
02:20:55.249 INFO  [nio-8082-exec-1] c.t.aspect.ControllerTraceAspect    : [CONTROLLER     ] UserController#getUsers | requestId=118b263f-...
02:20:55.287 INFO  [nio-8082-exec-1] c.t.interceptor.LifecycleInterceptor: [INTERCEPTOR POST] status=200 | requestId=118b263f-...
02:20:55.287 INFO  [nio-8082-exec-1] c.t.interceptor.LifecycleInterceptor: [INTERCEPTOR DONE] clean | requestId=118b263f-...
02:20:55.288 INFO  [nio-8082-exec-1] c.t.filter.RequestTracingFilter     : [FILTER END     ] GET /api/users | status=200 | 51ms | requestId=118b263f-...
```

The thread name (`nio-8082-exec-1`) shows all stages for a single request run on the same thread — confirming Spring MVC's synchronous, thread-per-request model under default Tomcat configuration.

---

## Technical Constraints (by design)

| Constraint | Why |
|---|---|
| No database | In-memory only. Restart clears traces. Keeps the focus on the MVC pipeline, not persistence. |
| No reactive stack | Spring MVC (Servlet API) only. The lifecycle is fundamentally different in WebFlux — that's a separate topic. |
| No external JS libraries | Dashboard is pure HTML/CSS/JS. No build step, no CDN dependency, works offline. |
| Traces never evicted | For a learning tool, seeing every request is the point. In production you would add a size cap or TTL. |

---

## Extending This Project

**Add a size cap to TraceStore**

```java
// evict oldest entry when over 500
if (traces.size() > 500) {
    traces.keySet().stream().findFirst().ifPresent(traces::remove);
}
```

**Add async tracking**

When using `@Async` or `DeferredResult` the thread changes mid-request. Fix by copying `requestId` into MDC at filter entry and propagating the MDC map to child threads via a `TaskDecorator` on your `ThreadPoolTaskExecutor`.

```java
executor.setTaskDecorator(runnable -> {
    Map<String, String> ctx = MDC.getCopyOfContextMap();
    return () -> { MDC.setContextMap(ctx); runnable.run(); };
});
```

**Add a UI timeline chart**

Replace the text-based stage list in the expanded row with a horizontal bar chart (CSS widths scaled to total execution time). No library needed.

**Add request body capture**

`CachingRequestWrapper#getBodyAsString()` already has the bytes. Surface them in the trace model and dashboard.

**Extend to WebFlux**

The entire architecture changes: no `ThreadLocal`, no `HandlerInterceptor`. Use `WebFilter` + `WebFluxConfigurer` + reactor context for propagation. A good follow-up project.
