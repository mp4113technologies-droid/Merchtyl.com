export type BarcodeScannerSuffix = 'Enter' | 'Tab';

export type BarcodeScannerPreferences = {
  enabled: boolean;
  minLength: number;
  suffix: BarcodeScannerSuffix;
  maxInterKeyDelayMs: number;
  duplicatePreventionMs: number;
};

export type KeyboardWedgeScannerOptions = BarcodeScannerPreferences & {
  now?: () => number;
};

export type KeyboardWedgeScanResult =
  | { type: 'scan'; value: string }
  | { type: 'duplicate'; value: string };

export type KeyboardEventLike = {
  key: string;
  altKey?: boolean;
  ctrlKey?: boolean;
  metaKey?: boolean;
  preventDefault?: () => void;
  stopPropagation?: () => void;
};

export const barcodeScannerPreferencesKey = 'merchtyl.barcodeScannerPreferences';

export const defaultBarcodeScannerPreferences: BarcodeScannerPreferences = {
  enabled: true,
  minLength: 4,
  suffix: 'Enter',
  maxInterKeyDelayMs: 45,
  duplicatePreventionMs: 1500
};

export class KeyboardWedgeScanner {
  private readonly options: KeyboardWedgeScannerOptions;
  private buffer = '';
  private lastKeyAt: number | null = null;
  private maxObservedDelay = 0;
  private lastScan: { value: string; at: number } | null = null;

  constructor(options: Partial<KeyboardWedgeScannerOptions> = {}) {
    this.options = normalizeBarcodeScannerPreferences(options);
    this.options.now = options.now ?? (() => performance.now());
  }

  handleKeyDown(event: KeyboardEventLike): KeyboardWedgeScanResult | null {
    if (!this.options.enabled || event.altKey || event.ctrlKey || event.metaKey) {
      this.resetBuffer();
      return null;
    }

    const now = this.options.now?.() ?? performance.now();

    if (event.key === this.options.suffix) {
      const result = this.finish(now);
      if (result) {
        event.preventDefault?.();
        event.stopPropagation?.();
      }
      return result;
    }

    if (event.key === 'Escape' || event.key === 'Backspace') {
      this.resetBuffer();
      return null;
    }

    if (event.key.length !== 1) {
      return null;
    }

    if (this.lastKeyAt !== null) {
      const delay = now - this.lastKeyAt;
      if (delay > this.options.maxInterKeyDelayMs) {
        this.resetBuffer();
      } else {
        this.maxObservedDelay = Math.max(this.maxObservedDelay, delay);
      }
    }

    this.buffer += event.key;
    this.lastKeyAt = now;
    return null;
  }

  reset() {
    this.resetBuffer();
    this.lastScan = null;
  }

  private finish(now: number): KeyboardWedgeScanResult | null {
    const value = this.buffer.trim();
    const detected = value.length >= this.options.minLength
      && this.buffer.length >= this.options.minLength
      && this.maxObservedDelay <= this.options.maxInterKeyDelayMs
      && this.lastKeyAt !== null;

    this.resetBuffer();

    if (!detected) {
      return null;
    }

    if (this.lastScan?.value === value && now - this.lastScan.at <= this.options.duplicatePreventionMs) {
      this.lastScan = { value, at: now };
      return { type: 'duplicate', value };
    }

    this.lastScan = { value, at: now };
    return { type: 'scan', value };
  }

  private resetBuffer() {
    this.buffer = '';
    this.lastKeyAt = null;
    this.maxObservedDelay = 0;
  }
}

export function loadBarcodeScannerPreferences(): BarcodeScannerPreferences {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(barcodeScannerPreferencesKey) ?? '{}') as Partial<BarcodeScannerPreferences>;
    return normalizeBarcodeScannerPreferences(parsed);
  } catch {
    return { ...defaultBarcodeScannerPreferences };
  }
}

export function saveBarcodeScannerPreferences(preferences: BarcodeScannerPreferences) {
  window.localStorage.setItem(barcodeScannerPreferencesKey, JSON.stringify(normalizeBarcodeScannerPreferences(preferences)));
}

export function normalizeBarcodeScannerPreferences(preferences: Partial<BarcodeScannerPreferences>): BarcodeScannerPreferences {
  return {
    enabled: typeof preferences.enabled === 'boolean' ? preferences.enabled : defaultBarcodeScannerPreferences.enabled,
    minLength: clampInteger(preferences.minLength, 1, 64, defaultBarcodeScannerPreferences.minLength),
    suffix: preferences.suffix === 'Tab' ? 'Tab' : 'Enter',
    maxInterKeyDelayMs: clampInteger(preferences.maxInterKeyDelayMs, 10, 250, defaultBarcodeScannerPreferences.maxInterKeyDelayMs),
    duplicatePreventionMs: clampInteger(preferences.duplicatePreventionMs, 0, 10_000, defaultBarcodeScannerPreferences.duplicatePreventionMs)
  };
}

function clampInteger(value: unknown, min: number, max: number, fallback: number) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.min(Math.max(Math.trunc(parsed), min), max);
}
