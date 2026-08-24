import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ForgotPasswordPage, ResetPasswordPage } from './PasswordResetPages';

function renderPage(node: React.ReactNode, entry: string) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
    <MemoryRouter initialEntries={[entry]}>{node}</MemoryRouter>
  </QueryClientProvider>);
}

afterEach(() => vi.restoreAllMocks());

describe('password reset pages', () => {
  it('shows the generic forgot-password success message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'ok' }), { status: 200 }));
    renderPage(<ForgotPasswordPage />, '/forgot-password');
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'owner@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(await screen.findByText('If an eligible account exists, password reset instructions have been sent.')).toBeInTheDocument();
  });

  it('validates password confirmation before reset', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      minimumLength: 12, maximumLength: 128, requiresUppercase: true,
      requiresLowercase: true, requiresNumber: true, requiresSpecialCharacter: true
    }), { status: 200 }));
    renderPage(<ResetPasswordPage />, '/reset-password?token=test-token');
    fireEvent.change(screen.getByLabelText(/New Password/), { target: { value: 'ValidPassword1!' } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: 'DifferentPassword1!' } });
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Update password' })).toBeDisabled());
  });

  it('displays backend password policy violations and rules', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).includes('password-policy')) return new Response(JSON.stringify({
        minimumLength: 12, maximumLength: 128, requiresUppercase: true,
        requiresLowercase: true, requiresNumber: true, requiresSpecialCharacter: true
      }), { status: 200 });
      return new Response(JSON.stringify({
        code: 'PASSWORD_POLICY_VIOLATION', message: 'The password does not meet the required security policy.',
        correlationId: 'corr-1', violations: [{ field: 'newPassword', code: 'PASSWORD_TOO_SHORT', message: 'Password must contain at least 12 characters.' }]
      }), { status: 400 });
    });
    renderPage(<ResetPasswordPage />, '/reset-password?token=test-token');
    expect(await screen.findByText(/Use 12–128 characters/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/New Password/), { target: { value: 'Short1!' } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: 'Short1!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    expect(await screen.findByText('Password must contain at least 12 characters.')).toBeInTheDocument();
  });

  it('shows a safe expired-token message', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).includes('password-policy')) return new Response(JSON.stringify({
        minimumLength: 12, maximumLength: 128, requiresUppercase: true,
        requiresLowercase: true, requiresNumber: true, requiresSpecialCharacter: true
      }), { status: 200 });
      return new Response(JSON.stringify({ code: 'EXPIRED_RESET_TOKEN', message: 'expired', violations: [] }), { status: 410 });
    });
    renderPage(<ResetPasswordPage />, '/reset-password?token=url-safe_token');
    fireEvent.change(screen.getByLabelText(/New Password/), { target: { value: 'ValidPassword1!' } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: 'ValidPassword1!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument();
  });
});
