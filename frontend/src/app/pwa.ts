export const pwaUpdateAvailableEvent = 'merchtyl:pwa-update-available';

export type MerchtylServiceWorkerRegistration = Pick<
  ServiceWorkerRegistration,
  'addEventListener' | 'installing' | 'waiting'
>;

function notifyUpdateAvailable(registration: MerchtylServiceWorkerRegistration) {
  window.dispatchEvent(new CustomEvent(pwaUpdateAvailableEvent, { detail: { registration } }));
}

export function registerMerchtylServiceWorker() {
  if (!('serviceWorker' in navigator) || !import.meta.env.PROD) {
    return;
  }

  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js').then((registration) => {
      if (registration.waiting) {
        notifyUpdateAvailable(registration);
      }

      registration.addEventListener('updatefound', () => {
        const worker = registration.installing;
        if (!worker) {
          return;
        }
        worker.addEventListener('statechange', () => {
          if (worker.state === 'installed' && navigator.serviceWorker.controller) {
            notifyUpdateAvailable(registration);
          }
        });
      });
    }).catch((error) => {
      console.error('Service worker registration failed', error);
    });

    let refreshing = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshing) {
        return;
      }
      refreshing = true;
      window.location.reload();
    });
  });
}

export function applyServiceWorkerUpdate(registration: MerchtylServiceWorkerRegistration) {
  registration.waiting?.postMessage({ type: 'SKIP_WAITING' });
}
