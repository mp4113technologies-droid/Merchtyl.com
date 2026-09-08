import { describe, expect, it } from 'vitest';
import { merchtylTokens, theme } from './theme';

describe('responsive application theme', () => {
  it('uses the official Merchtyl blue system as the application theme', () => {
    expect(theme.palette.primary.main).toBe(merchtylTokens.colors.blue);
    expect(theme.palette.background.default).toBe(merchtylTokens.colors.page);
    expect(theme.palette.text.primary).toBe(merchtylTokens.colors.text);
    expect(theme.palette.success.main).toBe(merchtylTokens.colors.success);
  });

  it('contains document-level overflow prevention primitives', () => {
    const overrides = theme.components?.MuiCssBaseline?.styleOverrides as Record<string, Record<string, unknown>>;

    expect(overrides['*, *::before, *::after']?.boxSizing).toBe('border-box');
    expect(overrides['#root']?.minWidth).toBe(0);
    expect(overrides.body?.overflowX).toBeUndefined();
  });

  it('contains tables and viewport-bound overlays locally', () => {
    const tableRoot = theme.components?.MuiTableContainer?.styleOverrides?.root as Record<string, unknown>;
    const dialogPaper = theme.components?.MuiDialog?.styleOverrides?.paper as Record<string, unknown>;
    const drawerPaper = theme.components?.MuiDrawer?.styleOverrides?.paper as Record<string, unknown>;
    const menuPaper = theme.components?.MuiMenu?.styleOverrides?.paper as Record<string, unknown>;

    expect(tableRoot.overflowX).toBe('auto');
    expect(tableRoot.maxWidth).toBe('100%');
    expect(dialogPaper.maxWidth).toBe('calc(100% - 32px)');
    expect(dialogPaper.maxHeight).toBe('calc(100dvh - 32px)');
    expect(drawerPaper.maxWidth).toBe('calc(100vw - 24px)');
    expect(menuPaper.maxWidth).toBe('calc(100vw - 32px)');
    expect(menuPaper.maxHeight).toBe('calc(100dvh - 96px)');
  });
});
