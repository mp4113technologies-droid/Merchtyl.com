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
const sessionStorage = new Map<string, string>();

Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
    clear: () => {
      storage.clear();
      sessionStorage.clear();
    }
  },
  configurable: true
});

Object.defineProperty(window, 'sessionStorage', {
  value: {
    getItem: (key: string) => sessionStorage.get(key) ?? null,
    setItem: (key: string, value: string) => sessionStorage.set(key, value),
    removeItem: (key: string) => sessionStorage.delete(key),
    clear: () => sessionStorage.clear()
  },
  configurable: true
});
