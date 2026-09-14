/**
 * Tests for the Cloudflare Worker in worker.js.
 *
 * The worker is exercised end-to-end through its `fetch(request)` handler - the same entry
 * point Cloudflare invokes - so method handling, the upstream request it makes, the headers
 * it serves and its error mapping are all covered by real requests. The only thing stubbed
 * is the global `fetch` the worker uses to pull install.sh from GitHub: each test decides
 * what that upstream answers and records how it was called.
 *
 * Runs on the Node built-in test runner with no dependencies: `node --test` (Node 20+).
 * `Request`, `Response` and `fetch` are Web-standard globals in Node 18+, matching the
 * workerd runtime closely enough for these tests.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";

import worker from "./worker.js";

const SCRIPT = "#!/usr/bin/env bash\necho jenesis\n";

/**
 * Drive the worker for one request. `upstream` is what the stubbed global fetch answers,
 * either a string body (served as 200) or `{ status, body }` to simulate a failure. The
 * returned `calls` records every upstream request, so a test can assert what was asked for
 * and with which Cloudflare cache options.
 */
async function call({ method = "GET", upstream = SCRIPT } = {}) {
  const calls = [];
  const saved = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return typeof upstream === "string"
      ? new Response(upstream, { status: 200 })
      : new Response(upstream.body ?? "", { status: upstream.status });
  };
  try {
    const response = await worker.fetch(new Request("https://get.jenesis.build/", { method }));
    return { response, calls };
  } finally {
    globalThis.fetch = saved;
  }
}

test("a GET serves the upstream script verbatim", async () => {
  const { response } = await call();
  assert.equal(response.status, 200);
  assert.equal(await response.text(), SCRIPT);
});

test("the response is typed as a shell script, so curl | bash is not fed HTML", async () => {
  const { response } = await call();
  assert.equal(response.headers.get("content-type"), "text/x-shellscript; charset=utf-8");
});

test("the response is cacheable for five minutes", async () => {
  const { response } = await call();
  assert.equal(response.headers.get("cache-control"), "public, max-age=300");
});

test("the response names the source it proxied", async () => {
  const { response } = await call();
  assert.equal(
    response.headers.get("x-source"),
    "https://raw.githubusercontent.com/jenesis/jenesis/main/installation/install.sh",
  );
});

test("a HEAD is served like a GET rather than rejected", async () => {
  const { response } = await call({ method: "HEAD" });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "text/x-shellscript; charset=utf-8");
});

test("every other method is rejected with 405 and an Allow header", async () => {
  for (const method of ["POST", "PUT", "DELETE", "PATCH", "OPTIONS"]) {
    const { response, calls } = await call({ method });
    assert.equal(response.status, 405, method);
    assert.equal(response.headers.get("Allow"), "GET, HEAD", method);
    assert.equal(calls.length, 0, method);
  }
});

test("the upstream is fetched with Cloudflare caching asked for", async () => {
  const { calls } = await call();
  assert.equal(calls.length, 1);
  assert.equal(
    calls[0].url,
    "https://raw.githubusercontent.com/jenesis/jenesis/main/installation/install.sh",
  );
  assert.deepEqual(calls[0].options.cf, { cacheTtl: 300, cacheEverything: true });
});

test("an upstream 404 is a 502 naming the status, never a 200 with an error page", async () => {
  const { response } = await call({ upstream: { status: 404, body: "404: Not Found" } });
  assert.equal(response.status, 502);
  assert.equal(await response.text(), "Upstream fetch failed: 404");
});

test("an upstream 500 is a 502", async () => {
  const { response } = await call({ upstream: { status: 500 } });
  assert.equal(response.status, 502);
  assert.equal(await response.text(), "Upstream fetch failed: 500");
});

test("a failed upstream is not cached", async () => {
  const { response } = await call({ upstream: { status: 503 } });
  assert.equal(response.headers.get("cache-control"), null);
  assert.equal(response.headers.get("content-type"), "text/plain;charset=UTF-8");
});

test("the source it proxies is a file this repository actually has", async () => {
  const { calls } = await call();
  const path = new URL(calls[0].url).pathname;
  assert.match(path, /\/installation\/install\.sh$/);
  assert.ok(
    existsSync(new URL("./install.sh", import.meta.url)),
    "worker.js points at installation/install.sh, which must sit beside it",
  );
});
