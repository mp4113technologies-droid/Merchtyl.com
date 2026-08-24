import '@testing-library/jest-dom/vitest';
import { IDBKeyRange, indexedDB } from 'fake-indexeddb';

Object.defineProperty(globalThis, 'indexedDB', {
  value: indexedDB,
  configurable: true
});

Object.defineProperty(globalThis, 'IDBKeyRange', {
  value: IDBKeyRange,
  configurable: true
});

const storage = new Map<string, string>();

Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
    clear: () => storage.clear()
  },
  configurable: true
});
