# Interview Notes — Redirect Hot-Path Optimization

A deep-dive on one concrete performance win in TinyRoute: making the URL-redirect
endpoint **2.6× faster** by fixing how it writes analytics to Redis. Written so I
can tell the story end-to-end (problem → diagnosis → fix → measurement → trade-offs →
what's next).

---

## 1. Context: what the redirect endpoint does

`GET /{shortUrl}` is the **hot path** — the one request that must be fast because it
runs on every single click. The system uses a two-tier analytics design:

- **Synchronous (hot path):** resolve the short URL (Redis cache, falling back to
  Postgres), then write click data to Redis — live counters plus a raw-event queue.
  It returns `302` immediately. **No** geo lookup, **no** user-agent parsing, **no**
  Postgres write happen here.
- **Asynchronous (background worker):** every 5s a scheduled job drains the raw-event
  queue, enriches each event (geo lookup, UA parsing), and persists to Postgres.
- **Live dashboard:** the frontend polls a `/live` endpoint every 3s, reading the
  Redis counters the hot path increments.

This separation is deliberate: the expensive work (DB writes, external HTTP) is kept
off the request the user is waiting on.

---

## 2. The problem

Load test (k6, 200 virtual users, 2 min, hitting 4 cached URLs so the path is
warm — Redis-only, no DB):

| metric | value |
|---|---|
| throughput | 2,047 req/s |
| median latency | 95 ms |
| p95 latency | 176 ms |
| min latency | 3.6 ms |

Two things stood out:

1. **2,047 RPS is low** for a path that should only touch Redis.
2. **median 95 ms vs min 3.6 ms** — a ~26× gap. The path *can* run in ~3.6 ms, but
   under load the typical request takes 95 ms. That gap is **contention/queuing**, not
   CPU — something is serializing.

It is a **closed-model** load test (each VU fires → waits → fires), so throughput is
governed by latency: `RPS ≈ VUs ÷ latency`. (200 ÷ 0.0976 s ≈ 2,047 ✓.) That means
**the only way to raise throughput is to lower per-request latency.**

---

## 3. Diagnosis: round-trip amplification

The hot path's `recordClick` looked like four Redis operations:

1. `INCR` the daily click counter
2. `SADD` the visitor's IP hash to today's unique-visitor set
3. `HINCRBY` the hourly breakdown hash
4. `LPUSH` the raw event onto the processing queue

But each operation was implemented as **two** Redis commands — the write **plus a
separate `EXPIRE`** to (re)set the key's TTL:

```java
// repeated for counter / set / hash / queue
redisTemplate.opsForValue().increment(key);            // round-trip 1
redisTemplate.expire(key, Duration.ofSeconds(ttl));    // round-trip 2
```

Each is a **blocking, synchronous round-trip**. So per redirect:

```
cache GET            → 1 round-trip
INCR  + EXPIRE       → 2
SADD  + EXPIRE       → 2
HINCRBY + EXPIRE     → 2
LPUSH + EXPIRE       → 2
                    ─────
                     ~9 sequential round-trips
```

All ~9 are issued one after another, multiplexed over a single Lettuce connection,
across 200 concurrent requests. **That serialization is the 95 ms.** Classic
round-trip amplification — like an N+1 query problem, but against Redis.

---

## 4. The fix (two parts: "A" and "B")

**A — collapse all the writes into one round-trip.** The four writes are
fire-and-forget (the hot path doesn't need their return values), so they can be sent
together instead of one at a time.

**B — stop re-`EXPIRE`ing on every click.** A key's TTL only needs to be set once,
when the key is created. Re-setting it on every click is wasted work.

### Implementation: a single atomic Lua script

I implemented both with **one Lua script executed server-side** (`EVALSHA`):

```lua
redis.call('INCR', KEYS[1])
if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[4]) end
redis.call('SADD', KEYS[2], ARGV[1])
if redis.call('TTL', KEYS[2]) < 0 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
redis.call('HINCRBY', KEYS[3], ARGV[2], 1)
if redis.call('TTL', KEYS[3]) < 0 then redis.call('EXPIRE', KEYS[3], ARGV[4]) end
redis.call('LPUSH', KEYS[4], ARGV[3])
if redis.call('TTL', KEYS[4]) < 0 then redis.call('EXPIRE', KEYS[4], ARGV[5]) end
return 1
```

- `KEYS` = daily counter, unique set, hourly hash, raw-event queue.
- `ARGV` = IP hash, hour field, serialized event JSON, analytics TTL, queue TTL.
- The whole thing runs in **one round-trip**, **atomically**.
- `if TTL < 0 then EXPIRE` sets the TTL **only when the key has none** (`-1` = no
  expiry). That's part B done correctly.

### Why Lua and not a Spring pipeline? (key talking point)

My first instinct was Spring's `executePipelined`, which batches commands into one
round-trip. But the **conditional `EXPIRE`** can't be expressed in a pipeline:
pipelined commands don't return their results until the pipeline flushes, so you
**can't branch** ("only `EXPIRE` if `TTL < 0`") in the middle of one. The conditional
needs to run **server-side**, which is exactly what a Lua script is for. Lua also gives
**atomicity** (all four writes succeed or none do), which a pipeline doesn't guarantee.

So: pipeline gets you part A only; Lua gets you A **and** B.

### Files changed
- `RedisAnalyticsHelper` — added `recordClickAtomic(...)` holding the script.
- `RedisAnalyticsEventQueue` — exposed `serialize()`, `queueKey()`, `queueTtlSeconds()`
  so the script gets the JSON payload, key, and queue TTL.
- `RedisAnalyticsService.recordClick` — replaced the four calls with one
  `recordClickAtomic(...)`.
- `RedisAnalyticsServiceTest` — updated to assert the single atomic call.

### One behavior nuance (be honest about it)
TTL is now anchored to the **first** write of a key instead of being refreshed on
every click. Because the keys are **date-stamped** (`analytics:url:42:daily:2026-05-29`),
each key is only written on its own day, so first-write-anchoring is functionally
equivalent — and more predictable. Not a regression.

---

## 5. The result

Same k6 test (200 VUs, 2 min, warm cache):

| metric | before | after | change |
|---|---|---|---|
| throughput | 2,047 RPS | **5,364 RPS** | **~2.6×** |
| median | 95 ms | **36 ms** | ~2.6× faster |
| p95 | 176 ms | **68 ms** | ~2.6× faster |
| errors | 0% | 0% | — |

Going from ~9 serial round-trips to ~2 (cache `GET` + one script) removed the
serialization that was the bottleneck. Throughput tracks the latency improvement
exactly, as the closed-model math predicts.

Data correctness is unchanged: live counters are still accurate and real-time (the hot
path still increments them synchronously), and the async enrichment pipeline is
untouched.

---

## 6. Finding the *next* ceiling (shows depth)

I also ran 500 VUs:

| VUs | throughput | median | p95 |
|---|---|---|---|
| 200 | 5,364 RPS | 36 ms | 68 ms |
| 500 | 5,140 RPS | 95 ms | 139 ms |

At 500 VUs throughput **didn't go up** (it even dipped slightly) but latency rose back
to 95 ms. That's a **saturation signature**: throughput plateaued around ~5,300 RPS, so
extra concurrency just **queues** (Little's law — when throughput is capped, more
concurrency means more latency, not more work done).

Likely ceilings (not yet confirmed — I'd measure before tuning):
1. **Test topology** — k6, the app, Redis, and Postgres all run on one machine and
   fight for CPU. A chunk of that ~5,300 ceiling is my laptop, not the app. **First
   step is to run k6 from a separate host.**
2. **Tomcat's default 200 worker threads** — suspicious that the sweet spot is exactly
   200 VUs.
3. **The single shared Lettuce connection** — every redirect's Redis ops funnel through
   it; worth ruling out with a connection pool.

This is why I'd lead with the **relative improvement** (2.6×, −61% p95), which is
hardware-independent, rather than the absolute "5,364 RPS," which depends on the box.

---

## 7. Sequel: the same anti-pattern on the background worker (live aggregates)

After fixing the hot path, I **audited the rest of the analytics pipeline for the same
round-trip amplification** — and found it in the async background worker.

### Where it was

The worker drains the raw-event queue every 5s and, for each event, calls
`recordLiveAggregates(...)` to bump five "live dashboard" dimension hashes: **country,
device, browser, OS, referrer**. Each dimension was written with the same write+`EXPIRE`
pair I'd just removed from the hot path:

```java
// repeated 5×, once per dimension
redisTemplate.opsForHash().increment(key, field, 1L);   // round-trip 1: HINCRBY
redisTemplate.expire(key, Duration.ofSeconds(ttl));      // round-trip 2: EXPIRE
```

So **5 dimensions × 2 commands = 10 Redis round-trips per event**. The worker processes
up to **500 events per batch**, so a single batch fired **~5,000 sequential round-trips**
just for live aggregates.

### Why it matters (and how it differs from the hot-path fix)

Important honesty point: **this is *not* on the user-facing redirect path**, so it does
**not** change redirect p95. The win here is on the **async worker** — lower Redis load
and faster batch drain, which protects headroom and keeps the live dashboard fresh under
high click volume. I'm careful not to claim a latency number I didn't measure: I quantify
this one by **round-trips eliminated**, not by a k6 figure.

### The fix — identical technique, one Lua script

```lua
-- KEYS[1..5] = country/device/browser/os/referrer hashes
-- ARGV[1..5] = field values   ARGV[6] = ttlSeconds
for i = 1, 5 do
    redis.call('HINCRBY', KEYS[i], ARGV[i], 1)
    if redis.call('TTL', KEYS[i]) < 0 then redis.call('EXPIRE', KEYS[i], ARGV[6]) end
end
return 1
```

- All five increments now run in **one atomic round-trip** instead of ten.
- `if TTL < 0 then EXPIRE` re-uses the **first-write-anchored TTL** trick (part B from the
  hot-path fix) — no redundant `EXPIRE` per click. Same justification: the keys are
  date-stamped, so first-write anchoring is functionally equivalent.

**Result (counted, not benchmarked):** per event **10 → 1** round-trip; per 500-event
batch **~5,000 → ~500**. A 10× reduction in Redis chatter on the worker path.

### Files changed
- `RedisAnalyticsHelper` — added `recordLiveAggregatesAtomic(...)` with the loop script;
  removed the now-unused `incrementHash(...)`.
- `RedisAnalyticsService.recordLiveAggregates` — replaced 5 `incrementHash` calls with one
  `recordLiveAggregatesAtomic`.
- `RedisAnalyticsServiceTest` — updated to assert the single atomic call.

### Trade-off I'd flag
The script is **hardcoded to exactly 5 dimensions** (`for i = 1, 5`) to keep an explicit,
type-checked Java signature that mirrors `recordClickAtomic`. Adding a 6th dimension means
touching both the loop bound and the method signature. I accepted that coupling because the
dimension set is stable; a generic variadic version would trade type-safety for flexibility
I don't currently need.

**The transferable lesson:** once I'd identified round-trip amplification as the failure
mode, it became a *pattern to grep for* across the codebase — not a one-off fix. Finding
and closing the second instance is the part I'd emphasize in an interview.

---

## 8. Likely interview questions & answers

**Q: Why was it slow if it only touches Redis?**
Round-trip amplification — ~9 sequential blocking Redis round-trips per redirect (each
write paired with its own `EXPIRE`), serialized under concurrency. Redis itself was
fine; the cost was the number of network hops.

**Q: How did you know it was contention, not CPU?**
The 26× gap between min (3.6 ms) and median (95 ms) under load — the path can run fast
when uncontended. And it's a closed-model test, so latency directly caps throughput.

**Q: Why a Lua script instead of pipelining?**
Pipelining batches commands but can't branch on intermediate results, so the
"set TTL only if absent" logic isn't expressible in it. That needs server-side
execution. Lua also makes the four writes atomic.

**Q: What are the downsides of the Lua approach?**
A script to maintain; it's all-or-nothing on failure (acceptable — it's wrapped in
try/catch and logged, and analytics loss on a Redis error is tolerable); and in a Redis
**Cluster** all keys must hash to the same slot (fine here — single instance).

**Q: How did you validate it?**
Re-ran the identical k6 script and compared before/after; updated unit tests to assert
the new single atomic call; ran the full backend suite.

**Q: What would you do next?**
Re-test from a separate machine and watch CPU to find the real ceiling, then tune the
Tomcat thread pool / Lettuce connection pool, and drop the unnecessary `@Transactional`
wrapper on cache-hit redirects (pure overhead when there's no DB work).

---

## 9. One-line summary (for résumé)

> Improved URL-shortener redirect throughput **~2.6× (2.0k → 5.4k req/s)** and cut
> **p95 latency ~61% (176 → 68 ms)** by replacing 9 per-request Redis round-trips with
> a single atomic Lua script (k6 load-tested, before/after).
