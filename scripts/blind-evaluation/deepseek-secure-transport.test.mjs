import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";
import {
  assertDeepSeekTlsEnvironment,
  createPinnedLookup,
  isForbiddenDeepSeekAddress,
  postDeepSeekJson,
  resolveAndValidateDeepSeekAddresses,
} from "./deepseek-secure-transport.mjs";

test("rejects private, mapped, NAT64, 6to4 and documentation addresses", () => {
  for (const address of [
    "127.0.0.1",
    "10.0.0.1",
    "169.254.169.254",
    "192.0.2.1",
    "::1",
    "::ffff:127.0.0.1",
    "64:ff9b::c000:201",
    "2002:c000:0201::",
    "2001:db8::1",
    "fc00::1",
    "::2",
    "1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
    "4000::1",
    "3fff::1",
    "2620:4f:8000::1",
    "5f00::1",
    "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
  ]) {
    assert.equal(
      isForbiddenDeepSeekAddress(address),
      true,
      address,
    );
  }
  assert.equal(isForbiddenDeepSeekAddress("8.8.8.8"), false);
  assert.equal(
    isForbiddenDeepSeekAddress("2606:4700:4700::1111"),
    false,
  );
});

test("rejects a mixed public and private DNS answer set", async () => {
  const resolver = {
    resolve4: async () => ["8.8.8.8", "10.0.0.1"],
    resolve6: async () => [],
  };
  await assert.rejects(
    resolveAndValidateDeepSeekAddresses({ resolver }),
    /DEEPSEEK_DNS_ADDRESS_REJECTED/,
  );
});

test("accepts one validated address family when the other DNS query times out", async () => {
  const timeout = new Error("query timed out");
  timeout.code = "ETIMEOUT";
  const resolver = {
    resolve4: async () => ["8.8.8.8"],
    resolve6: async () => {
      throw timeout;
    },
  };
  assert.deepEqual(
    await resolveAndValidateDeepSeekAddresses({ resolver }),
    [{ address: "8.8.8.8", family: 4 }],
  );
});

test("fails closed when both DNS address-family queries fail", async () => {
  const resolver = {
    resolve4: async () => {
      throw Object.assign(new Error("IPv4 unavailable"), {
        code: "ETIMEOUT",
      });
    },
    resolve6: async () => {
      throw Object.assign(new Error("IPv6 unavailable"), {
        code: "ESERVFAIL",
      });
    },
  };
  await assert.rejects(
    resolveAndValidateDeepSeekAddresses({ resolver }),
    /DEEPSEEK_DNS_RESOLUTION_FAILED/,
  );
});

test("absolute DNS deadline rejects a hung resolver and invokes cancellation", async () => {
  let cancelled = false;
  const never = new Promise(() => {});
  const resolver = {
    resolve4: async () => never,
    resolve6: async () => never,
    cancel: () => {
      cancelled = true;
    },
  };
  const started = Date.now();
  await assert.rejects(
    resolveAndValidateDeepSeekAddresses({
      resolver,
      timeoutMs: 25,
    }),
    (error) => error.code === "MODEL_TIMEOUT",
  );
  assert.equal(cancelled, true);
  assert.ok(Date.now() - started < 500);
});

test("uses the OS resolver only when c-ares returns no address", async () => {
  let lookupOptions;
  const resolver = {
    resolve4: async () => {
      throw Object.assign(new Error("IPv4 unavailable"), {
        code: "ETIMEOUT",
      });
    },
    resolve6: async () => {
      throw Object.assign(new Error("IPv6 unavailable"), {
        code: "ESERVFAIL",
      });
    },
    lookup: async (_hostname, options) => {
      lookupOptions = options;
      return [
        { address: "9.9.9.9", family: 4 },
        { address: "2001:4860:4860::8888", family: 6 },
      ];
    },
  };
  assert.deepEqual(
    await resolveAndValidateDeepSeekAddresses({ resolver }),
    [
      { address: "2001:4860:4860::8888", family: 6 },
      { address: "9.9.9.9", family: 4 },
    ],
  );
  assert.deepEqual(lookupOptions, { all: true, verbatim: true });
});

test("OS resolver fallback still rejects a mixed public and private set", async () => {
  const resolver = {
    resolve4: async () => [],
    resolve6: async () => [],
    lookup: async () => [
      { address: "9.9.9.9", family: 4 },
      { address: "192.168.0.1", family: 4 },
    ],
  };
  await assert.rejects(
    resolveAndValidateDeepSeekAddresses({ resolver }),
    /DEEPSEEK_DNS_ADDRESS_REJECTED/,
  );
});

test("pinned lookup never performs a second DNS lookup or accepts host drift", async () => {
  const lookup = createPinnedLookup([
    { address: "8.8.8.8", family: 4 },
  ]);
  const selected = await new Promise((resolve, reject) =>
    lookup("api.deepseek.com", {}, (error, address, family) =>
      error ? reject(error) : resolve({ address, family }),
    ),
  );
  assert.deepEqual(selected, { address: "8.8.8.8", family: 4 });
  await assert.rejects(
    new Promise((resolve, reject) =>
      lookup("attacker.invalid", {}, (error, address) =>
        error ? reject(error) : resolve(address),
      ),
    ),
    /DEEPSEEK_LOOKUP_HOST_DRIFT/,
  );
});

test("HTTPS transport pins address while retaining hostname and TLS SNI", async () => {
  let observed;
  const requestFactory = (options, onResponse) => {
    observed = options;
    const request = new EventEmitter();
    request.setTimeout = () => {};
    request.destroy = (error) => request.emit("error", error);
    request.end = () => {
      const response = new EventEmitter();
      response.statusCode = 200;
      response.headers = { "content-type": "application/json" };
      response.destroy = (error) => response.emit("error", error);
      onResponse(response);
      response.emit("data", Buffer.from("{}"));
      response.emit("end");
    };
    return request;
  };
  const response = await postDeepSeekJson({
    body: Buffer.from("{}"),
    secret: "TEST_ONLY",
    addresses: [{ address: "8.8.8.8", family: 4 }],
    requestFactory,
  });
  assert.equal(response.status, 200);
  assert.equal(observed.hostname, "api.deepseek.com");
  assert.equal(observed.servername, "api.deepseek.com");
  assert.equal(observed.agent, false);
  assert.equal(observed.rejectUnauthorized, true);
  assert.equal(typeof observed.checkServerIdentity, "function");
  assert.deepEqual(observed.ca, (await import("node:tls")).default.rootCertificates);
  assert.equal(observed.path, "/chat/completions");
  const selected = await new Promise((resolve, reject) =>
    observed.lookup(
      "api.deepseek.com",
      {},
      (error, address, family) =>
        error ? reject(error) : resolve({ address, family }),
    ),
  );
  assert.deepEqual(selected, { address: "8.8.8.8", family: 4 });
});

test("validated DNS answers are deduplicated and canonically sorted", async () => {
  const resolver = {
    resolve4: async () => ["9.9.9.9", "8.8.8.8", "9.9.9.9"],
    resolve6: async () => ["2001:4860:4860::8888"]
  };
  assert.deepEqual(
    await resolveAndValidateDeepSeekAddresses({ resolver }),
    [
      { address: "2001:4860:4860::8888", family: 6 },
      { address: "8.8.8.8", family: 4 },
      { address: "9.9.9.9", family: 4 }
    ]
  );
});

test("TLS verification bypass environment fails before request creation", async () => {
  assert.throws(
    () =>
      assertDeepSeekTlsEnvironment({
        NODE_TLS_REJECT_UNAUTHORIZED: "0",
      }),
    /DEEPSEEK_TLS_VERIFICATION_BYPASS_FORBIDDEN/,
  );
  for (const key of [
    "NODE_OPTIONS",
    "NODE_EXTRA_CA_CERTS",
    "SSL_CERT_FILE",
    "SSL_CERT_DIR",
    "OPENSSL_CONF",
    "NODE_USE_ENV_PROXY",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "ALL_PROXY",
    "http_proxy",
    "https_proxy",
    "all_proxy",
  ]) {
    assert.throws(
      () => assertDeepSeekTlsEnvironment({ [key]: "forbidden" }),
      /DEEPSEEK_TRANSPORT_ENVIRONMENT_FORBIDDEN/,
      key,
    );
  }
  let called = false;
  await assert.rejects(
    postDeepSeekJson({
      body: Buffer.from("{}"),
      secret: "TEST_ONLY",
      addresses: [{ address: "8.8.8.8", family: 4 }],
      environment: { NODE_TLS_REJECT_UNAUTHORIZED: "0" },
      requestFactory: () => {
        called = true;
        throw new Error("must not run");
      },
    }),
    /DEEPSEEK_TLS_VERIFICATION_BYPASS_FORBIDDEN/,
  );
  assert.equal(called, false);
});

test("HTTPS transport aborts responses above one MiB", async () => {
  const requestFactory = (_options, onResponse) => {
    const request = new EventEmitter();
    request.setTimeout = () => {};
    request.destroy = (error) => request.emit("error", error);
    request.end = () => {
      const response = new EventEmitter();
      response.statusCode = 200;
      response.headers = { "content-type": "application/json" };
      response.destroy = (error) => response.emit("error", error);
      onResponse(response);
      response.emit("data", Buffer.alloc(1_048_577));
    };
    return request;
  };
  await assert.rejects(
    postDeepSeekJson({
      body: Buffer.from("{}"),
      secret: "TEST_ONLY",
      addresses: [{ address: "8.8.8.8", family: 4 }],
      requestFactory,
    }),
    (error) => error.code === "RESPONSE_TOO_LARGE",
  );
});

test("absolute transport deadline aborts a connection that never responds", async () => {
  let destroyed = false;
  const requestFactory = () => {
    const request = new EventEmitter();
    request.setTimeout = () => {};
    request.destroy = (error) => {
      destroyed = true;
      request.emit("error", error);
    };
    request.end = () => {};
    return request;
  };
  await assert.rejects(
    postDeepSeekJson({
      body: Buffer.from("{}"),
      secret: "TEST_ONLY",
      addresses: [{ address: "8.8.8.8", family: 4 }],
      requestFactory,
      timeoutMs: 25,
    }),
    (error) => error.code === "MODEL_TIMEOUT",
  );
  assert.equal(destroyed, true);
});

test("absolute transport deadline is not extended by a slow-drip response", async () => {
  let interval;
  let responseDestroyed = false;
  const requestFactory = (_options, onResponse) => {
    const request = new EventEmitter();
    request.setTimeout = () => {};
    request.destroy = (error) => request.emit("error", error);
    request.end = () => {
      const response = new EventEmitter();
      response.statusCode = 200;
      response.headers = { "content-type": "application/json" };
      response.destroy = (error) => {
        responseDestroyed = true;
        clearInterval(interval);
        response.emit("error", error);
      };
      onResponse(response);
      interval = setInterval(
        () => response.emit("data", Buffer.from(" ")),
        5,
      );
    };
    return request;
  };
  await assert.rejects(
    postDeepSeekJson({
      body: Buffer.from("{}"),
      secret: "TEST_ONLY",
      addresses: [{ address: "8.8.8.8", family: 4 }],
      requestFactory,
      timeoutMs: 30,
    }),
    (error) => error.code === "MODEL_TIMEOUT",
  );
  assert.equal(responseDestroyed, true);
});

test("expired stage deadline fails before request creation", async () => {
  let called = false;
  await assert.rejects(
    postDeepSeekJson({
      body: Buffer.from("{}"),
      secret: "TEST_ONLY",
      addresses: [{ address: "8.8.8.8", family: 4 }],
      deadlineAt: new Date(Date.now() - 1_000).toISOString(),
      requestFactory: () => {
        called = true;
        throw new Error("must not run");
      },
    }),
    (error) => error.code === "MODEL_TIMEOUT",
  );
  assert.equal(called, false);
});
