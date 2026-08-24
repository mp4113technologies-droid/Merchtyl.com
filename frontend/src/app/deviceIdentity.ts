export const applicationDeviceIdentifierKey = 'merchtyl.deviceIdentifier';

type OptionalBrowserCrypto = {
  randomUUID?: () => string;
  getRandomValues?: (array: Uint8Array) => Uint8Array;
};

function randomHex(bytes: number) {
  const values = new Uint8Array(bytes);
  (window.crypto as OptionalBrowserCrypto).getRandomValues?.(values);
  return Array.from(values, (value) => value.toString(16).padStart(2, '0')).join('');
}

function randomIdentifier() {
  const browserCrypto = window.crypto as OptionalBrowserCrypto | undefined;

  if (browserCrypto?.randomUUID) {
    return `browser:${browserCrypto.randomUUID()}`;
  }
  if (browserCrypto?.getRandomValues) {
    return `browser:${randomHex(16)}`;
  }
  return `browser:${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function getApplicationDeviceIdentifier() {
  const stored = window.localStorage.getItem(applicationDeviceIdentifierKey);
  if (stored) {
    return stored;
  }

  const identifier = randomIdentifier();
  window.localStorage.setItem(applicationDeviceIdentifierKey, identifier);
  return identifier;
}
