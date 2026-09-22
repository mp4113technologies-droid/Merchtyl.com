import { act, renderHook } from '@testing-library/react';
import { useBusinessDayBoundaryRefresh } from './useBusinessDayBoundaryRefresh';

describe('useBusinessDayBoundaryRefresh', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-21T02:59:50.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('refetches authoritative state just after the store-local date boundary', () => {
    const refetch = vi.fn();
    renderHook(() => useBusinessDayBoundaryRefresh('2026-09-21T03:00:00Z', refetch));

    act(() => vi.advanceTimersByTime(10_249));
    expect(refetch).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(1));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('does not schedule a refresh for an invalid boundary', () => {
    const refetch = vi.fn();
    renderHook(() => useBusinessDayBoundaryRefresh('not-a-date', refetch));

    act(() => vi.runAllTimers());
    expect(refetch).not.toHaveBeenCalled();
  });
});
