import * as React from 'react';

const MAX_TIMEOUT_MS = 2_147_483_647;

/** Refetches authoritative store state immediately after its next local business-date boundary. */
export function useBusinessDayBoundaryRefresh(nextBusinessDateAt: string | undefined, refetch: () => unknown) {
  React.useEffect(() => {
    if (!nextBusinessDateAt) return undefined;
    const boundary = Date.parse(nextBusinessDateAt);
    if (!Number.isFinite(boundary)) return undefined;

    const delay = Math.min(Math.max(boundary - Date.now() + 250, 0), MAX_TIMEOUT_MS);
    const timeout = window.setTimeout(() => {
      void refetch();
    }, delay);
    return () => window.clearTimeout(timeout);
  }, [nextBusinessDateAt, refetch]);
}
