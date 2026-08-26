import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PlatformAdminActivationPage } from './PlatformAdminActivationPage';
import { activatePlatformAdmin } from '../../api/client';

vi.mock('../../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/client')>();
  return {
    ...original,
    getPasswordPolicy: vi.fn().mockResolvedValue({ minimumLength: 8, maximumLength: 20, requiresUppercase: true, requiresLowercase: true, requiresNumber: true, requiresSpecialCharacter: true }),
    activatePlatformAdmin: vi.fn().mockResolvedValue(undefined)
  };
});

function renderPage(entry: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[entry]}><Routes><Route path="/activate-platform-admin" element={<PlatformAdminActivationPage />} /><Route path="/login" element={<div>Login destination</div>} /></Routes></MemoryRouter></QueryClientProvider>);
}

describe('PlatformAdminActivationPage', () => {
  beforeEach(() => { vi.clearAllMocks(); window.localStorage.clear(); });

  it('renders publicly and does not persist the token', () => {
    renderPage('/activate-platform-admin?token=raw-invitation-token');
    expect(screen.getByRole('heading', { name: 'Set up your account' })).toBeInTheDocument();
    expect(screen.getByLabelText(/New Password/)).toBeInTheDocument();
    expect(JSON.stringify(window.localStorage)).not.toContain('raw-invitation-token');
  });

  it('shows invalid-link UX when token is missing', () => {
    renderPage('/activate-platform-admin');
    expect(screen.getByText(/no longer valid or has expired/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/New Password/)).not.toBeInTheDocument();
  });

  it('submits the query token and redirects to login', async () => {
    renderPage('/activate-platform-admin?token=one-time-token');
    fireEvent.change(screen.getByLabelText(/New Password/), { target: { value: 'StrongPassword!2026' } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: 'StrongPassword!2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Set Password' }));
    await waitFor(() => expect(activatePlatformAdmin).toHaveBeenCalledWith({ token: 'one-time-token', password: 'StrongPassword!2026' }));
    await waitFor(() => expect(screen.getByText('Login destination')).toBeInTheDocument(), { timeout: 2500 });
  });
});
