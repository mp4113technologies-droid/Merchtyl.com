import { describe, expect, it } from 'vitest';
import { compactFilterBarSx } from './responsive';

describe('compact desktop responsive helpers', () => {
  it('uses three contained filter columns at the 1024 class', () => {
    const styles = compactFilterBarSx as Record<string, unknown>;
    const columns = styles.gridTemplateColumns as Record<string, string>;
    const children = styles['& > *'] as Record<string, unknown>;

    expect(columns.md).toBe('repeat(3, minmax(0, 1fr))');
    expect(children.minWidth).toBe('0 !important');
    expect(styles.display).toBe('grid');
  });
});
