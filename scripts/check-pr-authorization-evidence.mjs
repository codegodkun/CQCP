#!/usr/bin/env node

import fs from "node:fs";

const requiredLabels = [
  "Task",
  "Governance mode",
  "Allowed files",
  "Forbidden files",
  "Commit authorization",
  "Push authorization",
  "Merge authorization",
  "Test evidence",
  "Independent review",
  "Memory writeback",
  "Out-of-scope confirmation",
];

const placeholderPatterns = [
  /^\s*$/,
  /^[-*]\s*$/,
  /^待确认\s*$/i,
  /^tbd\s*$/i,
  /^todo\s*$/i,
  /^n\/a\s*$/i,
  /^none\s*$/i,
  /^同前\s*$/i,
  /^见上文\s*$/i,
  /^已处理\s*$/i,
];

function usage() {
  console.error("Usage: node scripts/check-pr-authorization-evidence.mjs <pr-body-file>");
}

function readBody(filePath) {
  if (!filePath) {
    usage();
    process.exit(2);
  }

  try {
    return fs.readFileSync(filePath, "utf8");
  } catch (error) {
    console.error(`Failed to read PR body file: ${filePath}`);
    console.error(error.message);
    process.exit(2);
  }
}

function extractSection(body) {
  const headingPattern = /^#{2,3}\s+CQCP Authorization Evidence\s*$/im;
  const match = body.match(headingPattern);
  if (!match || match.index === undefined) {
    return null;
  }

  const sectionStart = match.index + match[0].length;
  const rest = body.slice(sectionStart);
  const nextHeading = rest.search(/^#{1,3}\s+/m);
  return nextHeading === -1 ? rest : rest.slice(0, nextHeading);
}

function valueForLabel(section, label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const linePattern = new RegExp(`^[*-][ \\t]+${escaped}:[ \\t]*(.*)$`, "im");
  const match = section.match(linePattern);
  if (!match) {
    return null;
  }

  const inlineValue = match[1].trim();
  if (inlineValue.length > 0) {
    return inlineValue;
  }

  return "";
}

function isPlaceholder(value) {
  return placeholderPatterns.some((pattern) => pattern.test(value));
}

const body = readBody(process.argv[2]);
const section = extractSection(body);
const failures = [];

if (!section) {
  failures.push("Missing section: CQCP Authorization Evidence");
} else {
  for (const label of requiredLabels) {
    const value = valueForLabel(section, label);

    if (value === null) {
      failures.push(`Missing field: ${label}`);
      continue;
    }

    if (isPlaceholder(value)) {
      failures.push(`Field has empty or placeholder value: ${label}`);
    }
  }
}

if (failures.length > 0) {
  console.error("CQCP authorization evidence check failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("CQCP authorization evidence check passed.");
