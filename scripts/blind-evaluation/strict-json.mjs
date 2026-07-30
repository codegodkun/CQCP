const JSON_WHITESPACE = new Set([" ", "\t", "\n", "\r"]);

const syntaxError = () => {
  throw new Error("STRICT_JSON_INVALID");
};

export const parseJsonRejectDuplicateKeys = (text) => {
  if (typeof text !== "string") {
    syntaxError();
  }
  let index = 0;

  const skipWhitespace = () => {
    while (index < text.length && JSON_WHITESPACE.has(text[index])) {
      index += 1;
    }
  };

  const parseString = () => {
    if (text[index] !== '"') {
      syntaxError();
    }
    const start = index;
    index += 1;
    while (index < text.length) {
      const char = text[index];
      if (char === '"') {
        index += 1;
        try {
          return JSON.parse(text.slice(start, index));
        } catch {
          syntaxError();
        }
      }
      if (char === "\\") {
        index += 1;
        if (index >= text.length) {
          syntaxError();
        }
        if (text[index] === "u") {
          const hex = text.slice(index + 1, index + 5);
          if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
            syntaxError();
          }
          index += 5;
          continue;
        }
        if (!/["\\/bfnrt]/.test(text[index])) {
          syntaxError();
        }
        index += 1;
        continue;
      }
      if (char.charCodeAt(0) <= 0x1f) {
        syntaxError();
      }
      index += 1;
    }
    syntaxError();
  };

  const parseNumber = () => {
    const match = text
      .slice(index)
      .match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
    if (!match) {
      syntaxError();
    }
    index += match[0].length;
    const value = Number(match[0]);
    if (!Number.isFinite(value)) {
      syntaxError();
    }
    return value;
  };

  const parseLiteral = (literal, value) => {
    if (text.slice(index, index + literal.length) !== literal) {
      syntaxError();
    }
    index += literal.length;
    return value;
  };

  const parseArray = () => {
    index += 1;
    skipWhitespace();
    const value = [];
    if (text[index] === "]") {
      index += 1;
      return value;
    }
    while (index < text.length) {
      value.push(parseValue());
      skipWhitespace();
      if (text[index] === "]") {
        index += 1;
        return value;
      }
      if (text[index] !== ",") {
        syntaxError();
      }
      index += 1;
      skipWhitespace();
    }
    syntaxError();
  };

  const parseObject = () => {
    index += 1;
    skipWhitespace();
    const value = {};
    const keys = new Set();
    if (text[index] === "}") {
      index += 1;
      return value;
    }
    while (index < text.length) {
      const key = parseString();
      if (keys.has(key)) {
        throw new Error("STRICT_JSON_DUPLICATE_KEY");
      }
      keys.add(key);
      skipWhitespace();
      if (text[index] !== ":") {
        syntaxError();
      }
      index += 1;
      skipWhitespace();
      Object.defineProperty(value, key, {
        configurable: true,
        enumerable: true,
        value: parseValue(),
        writable: true,
      });
      skipWhitespace();
      if (text[index] === "}") {
        index += 1;
        return value;
      }
      if (text[index] !== ",") {
        syntaxError();
      }
      index += 1;
      skipWhitespace();
    }
    syntaxError();
  };

  const parseValue = () => {
    skipWhitespace();
    const char = text[index];
    if (char === '"') {
      return parseString();
    }
    if (char === "{") {
      return parseObject();
    }
    if (char === "[") {
      return parseArray();
    }
    if (char === "t") {
      return parseLiteral("true", true);
    }
    if (char === "f") {
      return parseLiteral("false", false);
    }
    if (char === "n") {
      return parseLiteral("null", null);
    }
    if (char === "-" || (char >= "0" && char <= "9")) {
      return parseNumber();
    }
    syntaxError();
  };

  const value = parseValue();
  skipWhitespace();
  if (index !== text.length) {
    syntaxError();
  }
  return value;
};

export const parseJsonBytesRejectDuplicateKeys = (bytes) => {
  if (!Buffer.isBuffer(bytes)) {
    syntaxError();
  }
  let text;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    syntaxError();
  }
  return parseJsonRejectDuplicateKeys(text);
};
