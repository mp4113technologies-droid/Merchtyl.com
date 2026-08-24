import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PwaPrompt } from './PwaPrompt';
import { pwaUpdateAvailableEvent } from '../../app/pwa';
import type { MerchtylServiceWorkerRegistration } from '../../app/pwa';

function installPromptEvent() {
  const event = new Event('beforeinstallprompt', { cancelable: true }) as Event & {
    prompt: ReturnType<typeof vi.fn>;
    userChoice: Promise<{ outcome: 'accepted'; platform: string }>;
  };
  event.prompt = vi.fn().mockResolvedValue(undefined);
  event.userChoice = Promise.resolve({ outcome: 'accepted', platform: 'web' });
  return event;
}

describe('PwaPrompt', () => {
  it('captures the browser install prompt and exposes an install action', async () => {
    const event = installPromptEvent();
    const preventDefault = vi.spyOn(event, 'preventDefault');

    render(<PwaPrompt />);

    window.dispatchEvent(event);

    expect(await screen.findByText('Install Merchtyl for quick access')).toBeInTheDocument();
    expect(preventDefault).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Install' }));

    expect(event.prompt).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(screen.queryByText('Install Merchtyl for quick access')).not.toBeInTheDocument();
    });
  });

  it('asks the waiting service worker to activate when an update is accepted', async () => {
    const postMessage = vi.fn();
    const registration: MerchtylServiceWorkerRegistration = {
      addEventListener: vi.fn(),
      installing: null,
      waiting: { postMessage } as unknown as ServiceWorker
    };

    render(<PwaPrompt />);

    window.dispatchEvent(new CustomEvent(pwaUpdateAvailableEvent, {
      detail: { registration }
    }));

    expect(await screen.findByText('A Merchtyl update is ready')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Update' }));

    expect(postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' });
  });
});
