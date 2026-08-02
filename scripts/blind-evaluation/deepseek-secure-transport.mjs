import dns from "node:dns/promises";
import https from "node:https";
import net from "node:net";
import tls from "node:tls";

export const DEEPSEEK_HOST = "api.deepseek.com";
export const DEEPSEEK_PATH = "/chat/completions";
export const MAX_DEEPSEEK_RESPONSE_BYTES = 1_048_576;
export const DEEPSEEK_ADDRESS_POLICY_VERSION =
  "iana-special-purpose-frozen-2026-07-29-v1";
export const DEEPSEEK_FORBIDDEN_ENVIRONMENT_KEYS = Object.freeze([
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
]);

const IPV4_FORBIDDEN = Object.freeze([
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10],
  ["127.0.0.0", 8],
  ["169.254.0.0", 16],
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.0.2.0", 24],
  ["192.31.196.0", 24],
  ["192.52.193.0", 24],
  ["192.88.99.0", 24],
  ["192.168.0.0", 16],
  ["192.175.48.0", 24],
  ["198.18.0.0", 15],
  ["198.51.100.0", 24],
  ["203.0.113.0", 24],
  ["224.0.0.0", 4],
  ["240.0.0.0", 4],
]);

const IPV6_FORBIDDEN = Object.freeze([
  ["::", 128],
  ["::1", 128],
  ["::ffff:0:0", 96],
  ["64:ff9b::", 96],
  ["64:ff9b:1::", 48],
  ["100::", 64],
  ["2001::", 23],
  ["2001:db8::", 32],
  ["2002::", 16],
  ["2620:4f:8000::", 48],
  ["3fff::", 20],
  ["5f00::", 16],
  ["fc00::", 7],
  ["fe80::", 10],
  ["ff00::", 8],
]);

const IPV6_GLOBAL_UNICAST = Object.freeze(["2000::", 3]);

const ipv4ToBigInt = (address) => {
  const octets = address.split(".").map(Number);
  if (
    octets.length !== 4 ||
    octets.some(
      (octet) =>
        !Number.isInteger(octet) || octet < 0 || octet > 255,
    )
  ) {
    throw new Error("INVALID_IPV4_ADDRESS");
  }
  return octets.reduce(
    (value, octet) => (value << 8n) | BigInt(octet),
    0n,
  );
};

const ipv6ToBigInt = (input) => {
  let address = input.toLowerCase().split("%", 1)[0];
  if (address.includes(".")) {
    const lastColon = address.lastIndexOf(":");
    const ipv4 = ipv4ToBigInt(address.slice(lastColon + 1));
    address =
      `${address.slice(0, lastColon)}:` +
      `${((ipv4 >> 16n) & 0xffffn).toString(16)}:` +
      `${(ipv4 & 0xffffn).toString(16)}`;
  }
  const halves = address.split("::");
  if (halves.length > 2) {
    throw new Error("INVALID_IPV6_ADDRESS");
  }
  const left = halves[0] ? halves[0].split(":") : [];
  const right =
    halves.length === 2 && halves[1] ? halves[1].split(":") : [];
  const missing = 8 - left.length - right.length;
  if (
    missing < 0 ||
    (halves.length === 1 && missing !== 0) ||
    (halves.length === 2 && missing < 1)
  ) {
    throw new Error("INVALID_IPV6_ADDRESS");
  }
  const groups = [
    ...left,
    ...Array.from({ length: missing }, () => "0"),
    ...right,
  ];
  if (
    groups.length !== 8 ||
    groups.some((group) => !/^[a-f0-9]{1,4}$/.test(group))
  ) {
    throw new Error("INVALID_IPV6_ADDRESS");
  }
  return groups.reduce(
    (value, group) => (value << 16n) | BigInt(`0x${group}`),
    0n,
  );
};

const inCidr = (value, network, prefix, bits) => {
  const shift = BigInt(bits - prefix);
  return (value >> shift) === (network >> shift);
};

export const isForbiddenDeepSeekAddress = (address) => {
  const family = net.isIP(address);
  if (family === 4) {
    const value = ipv4ToBigInt(address);
    return IPV4_FORBIDDEN.some(([network, prefix]) =>
      inCidr(value, ipv4ToBigInt(network), prefix, 32),
    );
  }
  if (family === 6) {
    const value = ipv6ToBigInt(address);
    if (
      !inCidr(
        value,
        ipv6ToBigInt(IPV6_GLOBAL_UNICAST[0]),
        IPV6_GLOBAL_UNICAST[1],
        128,
      )
    ) {
      return true;
    }
    return IPV6_FORBIDDEN.some(([network, prefix]) =>
      inCidr(value, ipv6ToBigInt(network), prefix, 128),
    );
  }
  return true;
};

export const assertDeepSeekTlsEnvironment = (
  environment = process.env,
) => {
  if (environment?.NODE_TLS_REJECT_UNAUTHORIZED === "0") {
    throw new Error("DEEPSEEK_TLS_VERIFICATION_BYPASS_FORBIDDEN");
  }
  if (
    DEEPSEEK_FORBIDDEN_ENVIRONMENT_KEYS.some(
      (key) =>
        typeof environment?.[key] === "string" &&
        environment[key].trim().length > 0,
    )
  ) {
    throw new Error("DEEPSEEK_TRANSPORT_ENVIRONMENT_FORBIDDEN");
  }
};

const resolveFamily = async (resolver, method, hostname) => {
  try {
    const values = await resolver[method](hostname);
    return values.map((value) =>
      typeof value === "string" ? value : value.address,
    );
  } catch (error) {
    if (
      ["ENODATA", "ENOTFOUND", "EAI_NODATA", "EAI_NONAME"].includes(
        error?.code,
      )
    ) {
      return [];
    }
    throw error;
  }
};

const modelTimeoutError = () => {
  const error = new Error("DeepSeek absolute deadline exceeded");
  error.code = "MODEL_TIMEOUT";
  return error;
};

const resolveDeadlineEpochMs = ({ deadlineAt, timeoutMs }) => {
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error("DEEPSEEK_TIMEOUT_INVALID");
  }
  const relativeDeadline = Date.now() + timeoutMs;
  if (deadlineAt === undefined) {
    return relativeDeadline;
  }
  const absoluteDeadline = Date.parse(deadlineAt);
  if (
    !Number.isFinite(absoluteDeadline) ||
    new Date(absoluteDeadline).toISOString() !== deadlineAt
  ) {
    throw new Error("DEEPSEEK_DEADLINE_INVALID");
  }
  return Math.min(relativeDeadline, absoluteDeadline);
};

const withAbsoluteDeadline = ({
  operation,
  deadlineEpochMs,
  onTimeout = () => {},
}) =>
  new Promise((resolve, reject) => {
    const remainingMs = deadlineEpochMs - Date.now();
    if (remainingMs <= 0) {
      try {
        onTimeout();
      } finally {
        reject(modelTimeoutError());
      }
      return;
    }
    let settled = false;
    const settle = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timer);
      callback(value);
    };
    const timer = setTimeout(() => {
      if (settled) {
        return;
      }
      try {
        onTimeout();
      } finally {
        settle(reject, modelTimeoutError());
      }
    }, remainingMs);
    Promise.resolve()
      .then(operation)
      .then(
        (value) => settle(resolve, value),
        (error) => settle(reject, error),
      );
  });

export const resolveAndValidateDeepSeekAddresses = async ({
  hostname = DEEPSEEK_HOST,
  resolver = dns,
  deadlineAt,
  timeoutMs = 60_000,
} = {}) => {
  if (hostname !== DEEPSEEK_HOST) {
    throw new Error("DEEPSEEK_HOST_NOT_ALLOWLISTED");
  }
  const deadlineEpochMs = resolveDeadlineEpochMs({
    deadlineAt,
    timeoutMs,
  });
  return withAbsoluteDeadline({
    deadlineEpochMs,
    onTimeout: () => {
      if (typeof resolver?.cancel === "function") {
        resolver.cancel();
      }
    },
    operation: async () => {
      const [ipv4Result, ipv6Result] = await Promise.allSettled([
        resolveFamily(resolver, "resolve4", hostname),
        resolveFamily(resolver, "resolve6", hostname),
      ]);
      const ipv4 =
        ipv4Result.status === "fulfilled" ? ipv4Result.value : [];
      const ipv6 =
        ipv6Result.status === "fulfilled" ? ipv6Result.value : [];
      let rawAddresses = [...ipv4, ...ipv6];
      if (rawAddresses.length === 0) {
        if (typeof resolver?.lookup !== "function") {
          throw new Error("DEEPSEEK_DNS_RESOLUTION_FAILED");
        }
        try {
          const systemAddresses = await resolver.lookup(hostname, {
            all: true,
            verbatim: true,
          });
          if (!Array.isArray(systemAddresses)) {
            throw new Error("DEEPSEEK_OS_DNS_RESULT_INVALID");
          }
          rawAddresses = systemAddresses.map((entry) => entry?.address);
        } catch {
          throw new Error("DEEPSEEK_DNS_RESOLUTION_FAILED");
        }
      }
      const addresses = [...new Set(rawAddresses)]
        .sort()
        .map((address) => ({
          address,
          family: typeof address === "string" ? net.isIP(address) : 0,
        }));
      if (
        addresses.length === 0 ||
        addresses.some(
          ({ address, family }) =>
            ![4, 6].includes(family) ||
            isForbiddenDeepSeekAddress(address),
        )
      ) {
        throw new Error("DEEPSEEK_DNS_ADDRESS_REJECTED");
      }
      return Object.freeze(
        addresses.map((entry) => Object.freeze({ ...entry })),
      );
    },
  });
};

export const createPinnedLookup = (addresses) => {
  if (
    !Array.isArray(addresses) ||
    addresses.length === 0 ||
    addresses.some(
      ({ address, family }) =>
        net.isIP(address) !== family ||
        isForbiddenDeepSeekAddress(address),
    )
  ) {
    throw new Error("DEEPSEEK_PINNED_ADDRESS_SET_INVALID");
  }
  const frozen = addresses.map(({ address, family }) => ({
    address,
    family,
  }));
  return (hostname, options, callback) => {
    if (hostname !== DEEPSEEK_HOST) {
      callback(new Error("DEEPSEEK_LOOKUP_HOST_DRIFT"));
      return;
    }
    if (options?.all) {
      callback(null, frozen.map((entry) => ({ ...entry })));
      return;
    }
    callback(null, frozen[0].address, frozen[0].family);
  };
};

export const postDeepSeekJson = ({
  body,
  secret,
  addresses,
  timeoutMs = 60_000,
  deadlineAt,
  requestFactory = https.request,
  environment = process.env,
}) =>
  new Promise((resolve, reject) => {
    try {
      assertDeepSeekTlsEnvironment(environment);
    } catch (error) {
      reject(error);
      return;
    }
    if (!Buffer.isBuffer(body) || body.length === 0) {
      reject(new Error("DEEPSEEK_REQUEST_BODY_INVALID"));
      return;
    }
    if (typeof secret !== "string" || !secret.trim()) {
      reject(new Error("DEEPSEEK_SECRET_MISSING"));
      return;
    }
    let deadlineEpochMs;
    try {
      deadlineEpochMs = resolveDeadlineEpochMs({
        deadlineAt,
        timeoutMs,
      });
    } catch (error) {
      reject(error);
      return;
    }
    const remainingMs = deadlineEpochMs - Date.now();
    if (remainingMs <= 0) {
      reject(modelTimeoutError());
      return;
    }
    const lookup = createPinnedLookup(addresses);
    const abortController = new AbortController();
    let request;
    let response;
    let settled = false;
    const finish = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(deadlineTimer);
      callback(value);
    };
    const fail = (error) => finish(reject, error);
    const deadlineTimer = setTimeout(() => {
      if (settled) {
        return;
      }
      const error = modelTimeoutError();
      abortController.abort(error);
      response?.destroy?.(error);
      request?.destroy?.(error);
      fail(error);
    }, remainingMs);
    try {
      request = requestFactory(
      {
        protocol: "https:",
        hostname: DEEPSEEK_HOST,
        servername: DEEPSEEK_HOST,
        port: 443,
        method: "POST",
        path: DEEPSEEK_PATH,
        agent: false,
        lookup,
        signal: abortController.signal,
        rejectUnauthorized: true,
        checkServerIdentity: tls.checkServerIdentity,
        ca: tls.rootCertificates,
        headers: {
          Authorization: `Bearer ${secret.trim()}`,
          "Content-Type": "application/json",
          Accept: "application/json",
          "Content-Length": body.length,
        },
      },
      (incomingResponse) => {
        response = incomingResponse;
        const chunks = [];
        let total = 0;
        incomingResponse.on("data", (chunk) => {
          if (settled) {
            return;
          }
          const bytes = Buffer.from(chunk);
          total += bytes.length;
          if (total > MAX_DEEPSEEK_RESPONSE_BYTES) {
            const error = new Error(
              "DeepSeek response exceeded size limit",
            );
            error.code = "RESPONSE_TOO_LARGE";
            incomingResponse.destroy(error);
            return;
          }
          chunks.push(bytes);
        });
        incomingResponse.on("error", fail);
        incomingResponse.on("end", () => {
          if (settled) {
            return;
          }
          const contentType =
            typeof incomingResponse.headers?.["content-type"] === "string"
              ? incomingResponse.headers["content-type"]
              : Array.isArray(incomingResponse.headers?.["content-type"])
                ? incomingResponse.headers["content-type"][0]
                : "";
          const mediaType = contentType
            .split(";", 1)[0]
            .trim()
            .toLowerCase();
          finish(resolve, {
            status: incomingResponse.statusCode ?? 0,
            contentTypeClass:
              mediaType === "application/json"
                ? "APPLICATION_JSON"
                : mediaType.length === 0
                  ? "UNSPECIFIED"
                  : "NON_JSON",
            body: Buffer.concat(chunks, total),
          });
        });
      },
    );
    } catch (error) {
      fail(error);
      return;
    }
    request.setTimeout(Math.min(timeoutMs, remainingMs), () => {
      const error = new Error("DeepSeek request timed out");
      error.code = "MODEL_TIMEOUT";
      request.destroy(error);
    });
    request.on("error", fail);
    request.end(body);
  });
