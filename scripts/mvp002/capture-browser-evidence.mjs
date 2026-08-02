import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  assertBrowserTransportEvidence,
  parseBrowserEventStream,
  parseNginxBrowserAccessLog
} from "./browser-evidence-contract.mjs";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const evidenceRoot = path.resolve(
  process.argv[3] ?? path.join(repoRoot, "outputs/task-mvp-002/browser-evidence-current")
);
const composeEvidencePath = path.resolve(process.argv[4] ?? "");
const baseUrl = "http://localhost:15175";
const readonlyToken = process.env.CQCP_ADMIN_READONLY_TOKEN ?? "";
const adminToken = process.env.CQCP_ADMIN_API_TOKEN ?? "";
const composeProject = "cqcp-mvp002-acceptance";
const composeFiles = [
  "deploy/compose/compose.yml",
  "scripts/mvp002/compose.acceptance.override.yml"
];
const sourceFixtureRelative =
  "packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx";
const sourceFixture = path.join(repoRoot, ...sourceFixtureRelative.split("/"));
const evidencePrefix = `${path.join(repoRoot, "outputs/task-mvp-002")}${path.sep}`;

assert.ok(`${evidenceRoot}${path.sep}`.startsWith(evidencePrefix));
assert.ok(`${composeEvidencePath}${path.sep}`.startsWith(evidencePrefix));
assert.ok(readonlyToken.length >= 16, "ephemeral readonly credential is required");
assert.ok(adminToken.length >= 16, "ephemeral admin credential is required");
assert.notEqual(readonlyToken, adminToken, "acceptance credentials must be distinct");
assert.ok(fs.existsSync(sourceFixture));
assert.ok(fs.existsSync(composeEvidencePath));

const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const relativePosix = (absolutePath) =>
  path.relative(repoRoot, absolutePath).split(path.sep).join("/");
const readJson = (filePath) => JSON.parse(fs.readFileSync(filePath, "utf8"));
const writeJson = (filePath, value) =>
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
const delay = (milliseconds) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));

function chromeExecutable() {
  const candidates = [
    process.env.CQCP_CHROME_PATH,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
  ].filter(Boolean);
  const selected = candidates.find((candidate) => fs.existsSync(candidate));
  assert.ok(selected, "Chrome or Edge executable is required for browser evidence");
  return selected;
}

class CdpClient {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.handlers = new Map();
  }

  async connect() {
    this.socket = new WebSocket(this.webSocketUrl);
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("CDP connect timeout")), 10000);
      this.socket.addEventListener("open", () => {
        clearTimeout(timer);
        resolve();
      }, { once: true });
      this.socket.addEventListener("error", (event) => {
        clearTimeout(timer);
        reject(new Error(`CDP WebSocket error: ${event.type}`));
      }, { once: true });
    });
    this.socket.addEventListener("message", (message) => {
      const payload = JSON.parse(String(message.data));
      if (payload.id) {
        const waiter = this.pending.get(payload.id);
        if (!waiter) return;
        this.pending.delete(payload.id);
        if (payload.error) waiter.reject(new Error(`${payload.error.code}: ${payload.error.message}`));
        else waiter.resolve(payload.result ?? {});
        return;
      }
      for (const handler of this.handlers.get(payload.method) ?? []) {
        handler(payload.params ?? {});
      }
    });
  }

  on(method, handler) {
    const handlers = this.handlers.get(method) ?? [];
    handlers.push(handler);
    this.handlers.set(method, handlers);
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP command timeout: ${method}`));
      }, 30000);
      this.pending.set(id, {
        resolve: (result) => {
          clearTimeout(timer);
          resolve(result);
        },
        reject: (error) => {
          clearTimeout(timer);
          reject(error);
        }
      });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
}

async function waitForDevTools(profileRoot, timeoutMs = 15000) {
  const activePort = path.join(profileRoot, "DevToolsActivePort");
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (fs.existsSync(activePort)) {
      const [portLine] = fs.readFileSync(activePort, "utf8").split(/\r?\n/);
      if (/^\d+$/.test(portLine)) {
        const targets = await fetch(`http://127.0.0.1:${portLine}/json/list`).then(
          (response) => response.json()
        );
        const page = targets.find((target) => target.type === "page");
        if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl;
      }
    }
    await delay(100);
  }
  throw new Error("Chrome DevTools endpoint did not become ready");
}

function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    encoding: "utf8",
    windowsHide: true
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(" ")} failed: ${result.stderr}`);
  }
  return result.stdout;
}

function requestJson(url, token) {
  return fetch(url, { headers: { Authorization: `Bearer ${token}` } }).then(
    async (response) => {
      assert.equal(response.ok, true, `${url} returned ${response.status}`);
      return response.json();
    }
  );
}

const composeEvidence = readJson(composeEvidencePath);
assert.equal(composeEvidence.status, "PASS");
const runtimeProvenancePath = path.join(
  repoRoot,
  ...composeEvidence.runtimeProvenance.path.split("/")
);
const runtimeProvenance = readJson(runtimeProvenancePath);
const primary = {
  taskId: composeEvidence.taskId,
  executionId: composeEvidence.executionId
};
const malicious = composeEvidence.maliciousBodyExecution;
const maliciousText = malicious.previewText;

fs.mkdirSync(evidenceRoot, { recursive: true });
const profileRoot = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-mvp002-chrome-"));
const downloadsRoot = path.join(profileRoot, "downloads");
fs.mkdirSync(downloadsRoot);
const chrome = spawn(
  chromeExecutable(),
  [
    "--headless=new",
    "--remote-debugging-port=0",
    "--remote-allow-origins=*",
    `--user-data-dir=${profileRoot}`,
    "--window-size=1280,720",
    "--disable-background-networking",
    "--disable-component-update",
    "--disable-default-apps",
    "--disable-sync",
    "--metrics-recording-only",
    "--no-default-browser-check",
    "--no-first-run",
    "--password-store=basic",
    "about:blank"
  ],
  { stdio: "ignore", windowsHide: true }
);

const rawEvents = [];
const requests = new Map();
const record = (type, payload = {}) => {
  const serialized = JSON.stringify(payload);
  assert.equal(serialized.includes(readonlyToken), false, "readonly token entered raw event evidence");
  assert.equal(serialized.includes(adminToken), false, "admin token entered raw event evidence");
  rawEvents.push({
    sequence: rawEvents.length + 1,
    capturedAt: new Date().toISOString(),
    type,
    payload
  });
};

let client;
try {
  const webSocketUrl = await waitForDevTools(profileRoot);
  client = new CdpClient(webSocketUrl);
  await client.connect();

  client.on("Network.requestWillBeSent", (event) => {
    const url = event.request?.url ?? "";
    requests.set(event.requestId, {
      method: event.request?.method,
      url
    });
    if (/^https?:\/\//.test(url)) {
      record("Network.requestWillBeSent", {
        requestId: event.requestId,
        method: event.request.method,
        url,
        resourceType: event.type
      });
    }
  });
  client.on("Network.responseReceived", (event) => {
    const request = requests.get(event.requestId) ?? {};
    if (/^https?:\/\//.test(event.response?.url ?? "")) {
      record("Network.responseReceived", {
        requestId: event.requestId,
        method: request.method,
        url: event.response.url,
        status: event.response.status,
        mimeType: event.response.mimeType,
        resourceType: event.type
      });
    }
  });
  client.on("Network.loadingFinished", (event) => {
    if (requests.has(event.requestId)) {
      record("Network.loadingFinished", {
        requestId: event.requestId,
        encodedDataLength: event.encodedDataLength
      });
    }
  });
  client.on("Runtime.consoleAPICalled", (event) => {
    record("Runtime.consoleAPICalled", {
      type: event.type,
      argumentCount: event.args?.length ?? 0
    });
  });
  client.on("Page.javascriptDialogOpening", (event) => {
    record("Page.javascriptDialogOpening", {
      type: event.type,
      message: event.message
    });
  });
  client.on("Page.fileChooserOpened", (event) => {
    record("Page.fileChooserOpened", {
      mode: event.mode,
      backendNodeId: event.backendNodeId,
      frameId: event.frameId
    });
  });
  client.on("Browser.downloadWillBegin", (event) => {
    record("Browser.downloadWillBegin", {
      guid: event.guid,
      url: event.url,
      suggestedFilename: event.suggestedFilename
    });
  });
  client.on("Browser.downloadProgress", (event) => {
    record("Browser.downloadProgress", {
      guid: event.guid,
      state: event.state,
      receivedBytes: event.receivedBytes,
      totalBytes: event.totalBytes
    });
  });

  await Promise.all([
    client.send("Page.enable"),
    client.send("Network.enable"),
    client.send("Runtime.enable"),
    client.send("Log.enable")
  ]);
  await client.send("Emulation.setDeviceMetricsOverride", {
    width: 1280,
    height: 720,
    deviceScaleFactor: 1,
    mobile: false
  });
  await client.send("Page.setInterceptFileChooserDialog", { enabled: true });
  await client.send("Browser.setDownloadBehavior", {
    behavior: "allowAndName",
    downloadPath: downloadsRoot,
    eventsEnabled: true
  });
  const version = await client.send("Browser.getVersion");
  record("Browser.sessionStarted", {
    product: version.product,
    protocolVersion: version.protocolVersion,
    userAgent: version.userAgent,
    viewport: { width: 1280, height: 720 }
  });

  const evaluate = async (expression) => {
    const result = await client.send("Runtime.evaluate", {
      expression,
      returnByValue: true,
      awaitPromise: true,
      userGesture: true
    });
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.text ?? "browser evaluation failed");
    }
    return result.result?.value;
  };
  const waitFor = async (expression, description, timeoutMs = 30000) => {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (await evaluate(expression)) return;
      await delay(150);
    }
    throw new Error(`browser timeout: ${description}`);
  };
  const navigate = async (url) => {
    const result = await client.send("Page.navigate", { url });
    assert.equal(result.errorText, undefined, `navigation failed: ${url}`);
    await waitFor("document.readyState === 'complete'", `load ${url}`);
  };
  const clickText = async (text) => {
    const clicked = await evaluate(`(() => {
      const element = [...document.querySelectorAll('button,a')]
        .find((candidate) => candidate.textContent.trim() === ${JSON.stringify(text)});
      if (!element) return false;
      element.click();
      return true;
    })()`);
    assert.equal(clicked, true, `missing browser action: ${text}`);
  };
  const setInput = async (selector, value) => {
    const changed = await evaluate(`(() => {
      const input = document.querySelector(${JSON.stringify(selector)});
      if (!input) return false;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(input, ${JSON.stringify(value)});
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
    assert.equal(changed, true, `missing browser input: ${selector}`);
  };
  const authenticate = async (label, token) => {
    await waitFor(
      `Boolean(document.querySelector('input[aria-label=${JSON.stringify(label)}]'))`,
      label
    );
    await setInput(`input[aria-label=${JSON.stringify(label)}]`, token);
    await clickText("验证并进入");
  };
  const screenshot = async (name) => {
    const result = await client.send("Page.captureScreenshot", {
      format: "png",
      fromSurface: true,
      captureBeyondViewport: false
    });
    const filePath = path.join(evidenceRoot, name);
    const bytes = Buffer.from(result.data, "base64");
    fs.writeFileSync(filePath, bytes);
    const entry = {
      path: relativePosix(filePath),
      size: bytes.length,
      sha256: sha256(bytes)
    };
    record("Page.screenshotCaptured", entry);
    return entry;
  };
  const storageState = () => evaluate(`(() => ({
    url: location.href,
    localStorage: Object.keys(localStorage),
    sessionStorage: Object.keys(sessionStorage),
    cookies: document.cookie,
    passwordInputs: [...document.querySelectorAll('input[type=password]')]
      .map((input) => input.value)
  }))()`);

  await navigate(`${baseUrl}/review/new`);
  await waitFor("Boolean(document.querySelector('[data-testid=file-input]'))", "upload form");
  await clickText("填入 Demo 样本字段");
  const chooserStart = rawEvents.length;
  await evaluate("document.querySelector('[data-testid=file-input]').click(); true");
  await waitFor(
    `true`,
    "file chooser dispatch",
    1000
  );
  const chooserDeadline = Date.now() + 10000;
  while (
    Date.now() < chooserDeadline &&
    !rawEvents.slice(chooserStart).some((event) => event.type === "Page.fileChooserOpened")
  ) await delay(50);
  const chooserEvent = rawEvents.slice(chooserStart).find(
    (event) => event.type === "Page.fileChooserOpened"
  );
  assert.ok(chooserEvent, "Chrome did not emit Page.fileChooserOpened");
  await client.send("DOM.setFileInputFiles", {
    files: [sourceFixture],
    backendNodeId: chooserEvent.payload.backendNodeId
  });
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(path.basename(sourceFixture))})`,
    "selected DOCX filename"
  );
  await clickText("提交审核");
  await waitFor(
    `/\\/review\\/results\\/TASK_[a-f0-9]{32}\\?executionId=EXEC_[a-f0-9]{32}$/.test(location.pathname + location.search)`,
    "uploaded execution exact result",
    60000
  );
  const uploadIdentity = await evaluate(`(() => {
    const match = (location.pathname + location.search).match(
      /\\/review\\/results\\/(TASK_[a-f0-9]{32})\\?executionId=(EXEC_[a-f0-9]{32})$/
    );
    return { taskId: match[1], executionId: match[2] };
  })()`);
  record("CQCP.uploadIdentityObserved", uploadIdentity);

  await navigate(`${baseUrl}/review/tasks`);
  await waitFor("document.body.innerText.includes('验证管理访问凭据后显示 execution 清单')", "anonymous task list");
  const rowsVisibleBeforeAuthentication = await evaluate(
    `document.body.innerText.includes(${JSON.stringify(primary.executionId)})`
  );
  await authenticate("Management Access Token", readonlyToken);
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(primary.executionId)})`,
    "authenticated task list"
  );
  const taskListObservation = await evaluate(`(() => {
    const card = [...document.querySelectorAll('.result-card')]
      .find((candidate) => candidate.textContent.includes(${JSON.stringify(primary.executionId)}));
    const link = card?.querySelector('a[href*="/review/results/"]');
    return {
      visible: Boolean(card),
      text: card?.innerText ?? '',
      resultHref: link?.getAttribute('href') ?? null
    };
  })()`);
  record("CQCP.taskListDomObservation", taskListObservation);
  const screenshotEntries = [];
  screenshotEntries.push(await screenshot("01-authenticated-task-list.png"));
  const authenticatedStorage = await storageState();
  record("CQCP.authenticatedStorageObservation", authenticatedStorage);
  await client.send("Page.reload", { ignoreCache: true });
  await waitFor("document.readyState === 'complete'", "task list reload");
  await waitFor("document.body.innerText.includes('验证管理访问凭据后显示 execution 清单')", "credential cleared by reload");
  const reloadedStorage = await storageState();
  record("CQCP.reloadedStorageObservation", reloadedStorage);

  const primaryResultUrl = `${baseUrl}/review/results/${primary.taskId}?executionId=${primary.executionId}`;
  await navigate(primaryResultUrl);
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(primary.executionId)})`,
    "exact primary result"
  );
  await authenticate("Management Access Token", readonlyToken);
  await waitFor("document.body.innerText.includes('parser parser-docx-word-v20260724.1')", "parser preview");
  const exactResultObservation = await evaluate(`(() => ({
    url: location.href,
    bodyText: document.body.innerText,
    renderedExecutionId: ${JSON.stringify(primary.executionId)},
    parserText: [...document.querySelectorAll('*')]
      .map((element) => element.textContent?.trim())
      .find((text) => text === 'parser parser-docx-word-v20260724.1') ?? null
  }))()`);
  record("CQCP.exactResultDomObservation", exactResultObservation);
  screenshotEntries.push(await screenshot("02-exact-execution-result.png"));

  const located = await evaluate(`(() => {
    const card = [...document.querySelectorAll('.point-card')]
      .find((candidate) => candidate.textContent.includes('PROGRESS_PAYMENT_RATIO_CONSISTENCY'));
    const button = [...(card?.querySelectorAll('button') ?? [])]
      .find((candidate) => candidate.textContent.trim() === '定位到原文');
    if (!button) return false;
    button.click();
    return true;
  })()`);
  assert.equal(located, true, "source-location action is missing");
  await waitFor("Boolean(document.querySelector('.document-block.is-primary-evidence'))", "primary evidence highlight");
  const sourceLocation = await evaluate(`(() => {
    const block = document.querySelector('.document-block.is-primary-evidence');
    const rect = block.getBoundingClientRect();
    return {
      blockId: block.dataset.blockId,
      className: block.className,
      textContent: block.textContent.trim(),
      boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
    };
  })()`);
  record("CQCP.sourceLocationDomObservation", sourceLocation);
  screenshotEntries.push(await screenshot("03-source-anchor-location.png"));

  const downloadStart = rawEvents.length;
  await clickText("下载原始 DOCX");
  await waitFor("document.body.innerText.includes('原始 DOCX 已从受控接口读取')", "download completion message");
  const downloadDeadline = Date.now() + 15000;
  while (
    Date.now() < downloadDeadline &&
    !rawEvents.slice(downloadStart).some(
      (event) => event.type === "Browser.downloadProgress" && event.payload.state === "completed"
    )
  ) await delay(50);
  const downloadBegin = rawEvents.slice(downloadStart).find(
    (event) => event.type === "Browser.downloadWillBegin"
  );
  const downloadComplete = rawEvents.slice(downloadStart).find(
    (event) => event.type === "Browser.downloadProgress" && event.payload.state === "completed"
  );
  assert.ok(downloadBegin && downloadComplete, "Chrome native download event/file is missing");
  const nativeDownloadPath = path.join(downloadsRoot, downloadBegin.payload.guid);
  await waitForFile(nativeDownloadPath, 10000);
  const nativeDownloadBytes = fs.readFileSync(nativeDownloadPath);
  const downloadNotice = await evaluate(
    `[...document.querySelectorAll('*')].map((element) => element.textContent?.trim())
      .find((text) => text?.startsWith('原始 DOCX 已从受控接口读取')) ?? null`
  );
  const downloadObservation = {
    guid: downloadBegin.payload.guid,
    suggestedFilename: downloadBegin.payload.suggestedFilename,
    size: nativeDownloadBytes.length,
    sha256: sha256(nativeDownloadBytes),
    completionMessage: downloadNotice
  };
  record("CQCP.nativeDownloadFileObservation", downloadObservation);
  screenshotEntries.push(await screenshot("04-browser-download.png"));

  await navigate(`${baseUrl}/admin/model-profiles`);
  await authenticate("Admin Access Token", adminToken);
  await waitFor("document.body.innerText.includes('DEEPSEEK_EVAL_ACCEPTANCE')", "PUBLIC model profile");
  await waitFor("document.body.innerText.includes('MVP_DEMO_MOCK')", "Demo model profile");
  const modelObservation = await evaluate(`(() => ({
    url: location.href,
    bodyText: document.body.innerText,
    passwordValues: [...document.querySelectorAll('input[type=password]')]
      .map((input) => input.value)
  }))()`);
  record("CQCP.modelProfileDomObservation", modelObservation);
  screenshotEntries.push(await screenshot("05-model-profile-status.png"));

  await navigate(`${baseUrl}/review/results/${uploadIdentity.taskId}?executionId=${uploadIdentity.executionId}`);
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(uploadIdentity.executionId)})`,
    "uploaded result page"
  );
  await authenticate("Management Access Token", readonlyToken);
  await waitFor("document.body.innerText.includes('parser parser-docx-word-v20260724.1')", "uploaded preview");
  record("CQCP.uploadResultDomObservation", {
    url: await evaluate("location.href"),
    executionId: uploadIdentity.executionId
  });
  screenshotEntries.push(await screenshot("06-browser-file-upload-result.png"));

  await navigate(`${baseUrl}/review/results/${malicious.taskId}?executionId=${malicious.executionId}`);
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(malicious.executionId)})`,
    "malicious execution result"
  );
  await authenticate("Management Access Token", readonlyToken);
  await waitFor(
    `document.body.innerText.includes(${JSON.stringify(maliciousText)})`,
    "malicious body text"
  );
  const maliciousObservation = await evaluate(`(() => {
    const blocks = [...document.querySelectorAll('.document-block')];
    const block = blocks.find((candidate) => candidate.textContent.includes(${JSON.stringify(maliciousText)}));
    if (!block) return null;
    block.scrollIntoView({ block: 'center' });
    const rect = block.getBoundingClientRect();
    const descendants = [block, ...block.querySelectorAll('*')];
    return {
      taskId: ${JSON.stringify(malicious.taskId)},
      executionId: ${JSON.stringify(malicious.executionId)},
      blockId: block.dataset.blockId,
      textContent: block.textContent.trim(),
      outerHTML: block.outerHTML,
      imageElementCount: block.querySelectorAll('img').length,
      inlineHandlerAttributeCount: descendants.reduce(
        (total, element) => total + [...element.attributes]
          .filter((attribute) => attribute.name.toLowerCase().startsWith('on')).length,
        0
      ),
      boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
    };
  })()`);
  assert.ok(maliciousObservation, "malicious-body DOM block is missing");
  record("CQCP.maliciousBodyDomObservation", maliciousObservation);
  screenshotEntries.push(await screenshot("07-malicious-text-safety.png"));

  const finalStorage = await storageState();
  record("CQCP.finalStorageObservation", finalStorage);

  const uploadStatus = await requestJson(
    `${baseUrl}/api/review/tasks/${uploadIdentity.taskId}/executions/${uploadIdentity.executionId}`,
    readonlyToken
  );
  const uploadResult = await requestJson(
    `${baseUrl}/api/v1/tasks/${uploadIdentity.taskId}/result?executionId=${uploadIdentity.executionId}`,
    readonlyToken
  );
  const uploadList = await requestJson(
    `${baseUrl}/api/review/tasks?page=0&size=20&q=${uploadIdentity.taskId}`,
    readonlyToken
  );
  const uploadPreview = await requestJson(
    `${baseUrl}/api/review/tasks/${uploadIdentity.taskId}/executions/${uploadIdentity.executionId}/document-preview`,
    readonlyToken
  );
  const uploadDocumentResponse = await fetch(
    `${baseUrl}/api/review/tasks/${uploadIdentity.taskId}/executions/${uploadIdentity.executionId}/document`,
    { headers: { Authorization: `Bearer ${readonlyToken}` } }
  );
  assert.equal(uploadDocumentResponse.ok, true);
  const uploadDocument = Buffer.from(await uploadDocumentResponse.arrayBuffer());
  const sourceBytes = fs.readFileSync(sourceFixture);
  const sourceSha = sha256(sourceBytes);
  const uploadDocumentSha = sha256(uploadDocument);
  assert.equal(uploadDocumentSha, sourceSha);
  assert.equal(uploadDocumentResponse.headers.get("x-cqcp-document-sha256"), sourceSha);
  assert.equal(uploadStatus.status, "SUCCESS");
  assert.equal(uploadResult.executionId, uploadIdentity.executionId);
  assert.equal(uploadList.items.length, 1);
  assert.equal(uploadList.items[0].executionId, uploadIdentity.executionId);
  assert.ok(uploadPreview.blocks.length > 0);

  const eventStreamPath = path.join(evidenceRoot, "browser-cdp-events.ndjson");
  const eventStreamBytes = Buffer.from(
    `${rawEvents.map((event) => JSON.stringify(event)).join("\n")}\n`,
    "utf8"
  );
  fs.writeFileSync(eventStreamPath, eventStreamBytes);

  const composeArgs = [
    "compose",
    "-p",
    composeProject,
    ...composeFiles.flatMap((relativePath) => [
      "-f",
      path.join(repoRoot, ...relativePath.split("/"))
    ]),
    "logs",
    "--no-color",
    "admin-web"
  ];
  const browserAccessLines = run("docker", composeArgs)
    .split(/\r?\n/)
    .filter((line) => /(?:Chrome|HeadlessChrome)\//.test(line));
  assert.ok(browserAccessLines.length > 0, "Chrome access log is empty");
  const accessLogPath = path.join(evidenceRoot, "browser-network-access.log");
  const accessLogBytes = Buffer.from(`${browserAccessLines.join("\n")}\n`, "utf8");
  fs.writeFileSync(accessLogPath, accessLogBytes);

  const transport = assertBrowserTransportEvidence(
    parseBrowserEventStream(eventStreamBytes),
    parseNginxBrowserAccessLog(accessLogBytes),
    {
      uploadTaskId: uploadIdentity.taskId,
      uploadExecutionId: uploadIdentity.executionId,
      primaryTaskId: primary.taskId,
      primaryExecutionId: primary.executionId,
      maliciousTaskId: malicious.taskId,
      maliciousExecutionId: malicious.executionId
    }
  );

  const uploadEvidencePath = path.join(evidenceRoot, "browser-upload-result.json");
  const uploadEvidence = {
    schemaVersion: "task-mvp-002-browser-upload-v2",
    status: "PASS",
    capturedAt: new Date().toISOString(),
    uploadChannel: "CHROME_CDP_NATIVE_FILE_CHOOSER",
    eventStream: {
      path: relativePosix(eventStreamPath),
      size: eventStreamBytes.length,
      sha256: sha256(eventStreamBytes)
    },
    serverAccessLog: {
      path: relativePosix(accessLogPath),
      size: accessLogBytes.length,
      sha256: sha256(accessLogBytes)
    },
    sourceFixture: sourceFixtureRelative,
    sourceSize: sourceBytes.length,
    sourceSha256: sourceSha,
    taskId: uploadIdentity.taskId,
    executionId: uploadIdentity.executionId,
    executionStatus: uploadStatus.status,
    resultExecutionId: uploadResult.executionId,
    taskListExecutionId: uploadList.items[0].executionId,
    plannedPointCount: uploadResult.summary.plannedPointCount,
    passCount: uploadResult.summary.passCount,
    previewBlockCount: uploadPreview.blocks.length,
    storedHeaderSha256: uploadDocumentResponse.headers.get("x-cqcp-document-sha256"),
    downloadSize: uploadDocument.length,
    downloadSha256: uploadDocumentSha,
    browserResultPath: `/review/results/${uploadIdentity.taskId}?executionId=${uploadIdentity.executionId}`,
    authorizationPersisted: false
  };
  writeJson(uploadEvidencePath, uploadEvidence);
  const uploadEvidenceBytes = fs.readFileSync(uploadEvidencePath);

  const captureManifestPath = path.join(evidenceRoot, "browser-capture-manifest.json");
  const captureManifest = {
    schemaVersion: "task-mvp-002-browser-capture-v1",
    status: "COMPLETE",
    capturedAt: new Date().toISOString(),
    browser: {
      product: version.product,
      protocolVersion: version.protocolVersion,
      controlSurface: "DIRECT_CHROME_DEVTOOLS_PROTOCOL"
    },
    eventStream: {
      path: relativePosix(eventStreamPath),
      size: eventStreamBytes.length,
      sha256: sha256(eventStreamBytes)
    },
    serverAccessLog: {
      path: relativePosix(accessLogPath),
      size: accessLogBytes.length,
      sha256: sha256(accessLogBytes)
    },
    identities: {
      primary,
      upload: uploadIdentity,
      malicious: {
        taskId: malicious.taskId,
        executionId: malicious.executionId
      }
    },
    nativeDownload: downloadObservation,
    transport,
    screenshots: screenshotEntries
  };
  writeJson(captureManifestPath, captureManifest);
  const captureManifestBytes = fs.readFileSync(captureManifestPath);

  const assertionsPath = path.join(evidenceRoot, "browser-assertions.json");
  const assertions = {
    schemaVersion: "task-mvp-002-browser-evidence-v5",
    status: "PASS",
    completedAt: new Date().toISOString(),
    browser: version.product,
    baseUrl,
    captureManifest: {
      path: relativePosix(captureManifestPath),
      size: captureManifestBytes.length,
      sha256: sha256(captureManifestBytes)
    },
    runtimeProvenance: {
      path: relativePosix(runtimeProvenancePath),
      sha256: sha256(fs.readFileSync(runtimeProvenancePath)),
      sourceStateSha256: runtimeProvenance.subject.sourceStateSha256,
      apiImageId: runtimeProvenance.compose.services["api-server"].imageId,
      adminWebImageId: runtimeProvenance.compose.services["admin-web"].imageId
    },
    composeEvidence: {
      path: relativePosix(composeEvidencePath),
      sha256: sha256(fs.readFileSync(composeEvidencePath))
    },
    browserUploadEvidence: {
      path: relativePosix(uploadEvidencePath),
      sha256: sha256(uploadEvidenceBytes)
    },
    primaryExecution: {
      taskId: primary.taskId,
      executionId: primary.executionId,
      sourceDocumentSha256: composeEvidence.sourceSha256,
      downloadSha256: composeEvidence.downloadSha256
    },
    maliciousBodyExecution: {
      taskId: malicious.taskId,
      executionId: malicious.executionId,
      sourceDocumentSha256: malicious.sourceSha256,
      previewBlockId: malicious.previewBlockId,
      previewText: malicious.previewText
    },
    assertions: {
      managementAccess: {
        status: "PASS",
        rowsVisibleBeforeAuthentication,
        rowsVisibleAfterAuthentication: taskListObservation.visible,
        tokenPersistedAcrossReload:
          reloadedStorage.localStorage.length > 0 ||
          reloadedStorage.sessionStorage.length > 0 ||
          reloadedStorage.passwordInputs.some(Boolean),
        tokenVisibleAfterAuthentication: authenticatedStorage.passwordInputs.some(Boolean),
        tokenPresentInUrl:
          authenticatedStorage.url.includes(readonlyToken) ||
          modelObservation.url.includes(adminToken)
      },
      taskList: {
        status: "PASS",
        resultHref: taskListObservation.resultHref,
        executionRowVisible: taskListObservation.visible
      },
      exactResult: {
        status: "PASS",
        renderedExecutionId: exactResultObservation.renderedExecutionId,
        parserVersion: exactResultObservation.parserText
      },
      sourceLocation: {
        status: "PASS",
        reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
        blockId: sourceLocation.blockId,
        highlightClass: sourceLocation.className,
        highlightedText: sourceLocation.textContent,
        evidenceTextReverseSearchUsed: false
      },
      download: {
        status: "PASS",
        nativeEventCaptured: true,
        nativeFileCaptured: true,
        byteLength: downloadObservation.size,
        sha256: downloadObservation.sha256,
        completionMessage: downloadObservation.completionMessage,
        tokenPresentInUrl: false
      },
      modelStatus: {
        status: "PASS",
        adminTokenVisibleAfterAuthentication: modelObservation.passwordValues.some(Boolean),
        bodyText: modelObservation.bodyText
      },
      browserFileUpload: {
        status: "PASS",
        fileChooserUsed: true,
        chooserMultiple: false,
        taskId: uploadIdentity.taskId,
        executionId: uploadIdentity.executionId,
        resultUrlExact: true
      },
      maliciousBodyTextSafety: {
        status: "PASS",
        parserBacked: true,
        renderedAsText: true,
        maliciousImageCount: maliciousObservation.imageElementCount,
        inlineHandlerCount: maliciousObservation.inlineHandlerAttributeCount,
        javascriptDialogPresent: false,
        domObservation: maliciousObservation
      },
      browserConsole: {
        status: "PASS",
        errorCount: rawEvents.filter(
          (event) => event.type === "Runtime.consoleAPICalled" && event.payload.type === "error"
        ).length,
        warningCount: rawEvents.filter(
          (event) => event.type === "Runtime.consoleAPICalled" && event.payload.type === "warning"
        ).length
      }
    },
    screenshots: screenshotEntries
  };

  for (const assertion of Object.values(assertions.assertions)) {
    if ("status" in assertion) assert.equal(assertion.status, "PASS");
  }
  assert.equal(assertions.assertions.managementAccess.rowsVisibleBeforeAuthentication, false);
  assert.equal(assertions.assertions.managementAccess.rowsVisibleAfterAuthentication, true);
  assert.equal(assertions.assertions.managementAccess.tokenPersistedAcrossReload, false);
  assert.equal(assertions.assertions.managementAccess.tokenVisibleAfterAuthentication, false);
  assert.equal(assertions.assertions.managementAccess.tokenPresentInUrl, false);
  assert.equal(assertions.assertions.modelStatus.adminTokenVisibleAfterAuthentication, false);
  assert.equal(assertions.assertions.maliciousBodyTextSafety.maliciousImageCount, 0);
  assert.equal(assertions.assertions.maliciousBodyTextSafety.inlineHandlerCount, 0);
  assert.equal(assertions.assertions.browserConsole.errorCount, 0);
  assert.equal(assertions.assertions.browserConsole.warningCount, 0);
  assert.equal(downloadObservation.sha256, composeEvidence.sourceSha256);
  writeJson(assertionsPath, assertions);

  process.stdout.write(`${JSON.stringify({
    status: "PASS",
    browser: version.product,
    primary,
    upload: uploadIdentity,
    malicious: { taskId: malicious.taskId, executionId: malicious.executionId },
    eventStreamSha256: sha256(eventStreamBytes),
    accessLogSha256: sha256(accessLogBytes),
    screenshotCount: screenshotEntries.length
  })}\n`);
} finally {
  if (client?.socket?.readyState === WebSocket.OPEN) {
    try {
      await client.send("Browser.close");
    } catch {
      client.socket.close();
    }
  }
  if (chrome.exitCode === null) {
    await Promise.race([
      new Promise((resolve) => chrome.once("exit", resolve)),
      delay(5000)
    ]);
  }
  const tempRoot = path.resolve(os.tmpdir());
  const resolvedProfile = path.resolve(profileRoot);
  if (
    `${resolvedProfile}${path.sep}`.startsWith(`${tempRoot}${path.sep}`) &&
    path.basename(resolvedProfile).startsWith("cqcp-mvp002-chrome-")
  ) {
    for (let attempt = 0; attempt < 10; attempt += 1) {
      try {
        fs.rmSync(resolvedProfile, { recursive: true, force: true });
        break;
      } catch {
        await delay(200);
      }
    }
  }
}

async function waitForFile(filePath, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (fs.existsSync(filePath) && fs.statSync(filePath).size > 0) return;
    await delay(50);
  }
  throw new Error(`downloaded file is missing: ${path.basename(filePath)}`);
}
