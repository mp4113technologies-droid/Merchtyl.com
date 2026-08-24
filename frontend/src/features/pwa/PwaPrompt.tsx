import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Button, IconButton, Snackbar } from '@mui/material';
import * as React from 'react';
import {
  applyServiceWorkerUpdate,
  pwaUpdateAvailableEvent,
  type MerchtylServiceWorkerRegistration
} from '../../app/pwa';

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
};

export function PwaPrompt() {
  const [installPrompt, setInstallPrompt] = React.useState<BeforeInstallPromptEvent | null>(null);
  const [updateRegistration, setUpdateRegistration] = React.useState<MerchtylServiceWorkerRegistration | null>(null);

  React.useEffect(() => {
    const handleInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
    };
    const handleUpdateAvailable = (event: Event) => {
      const detail = (event as CustomEvent<{ registration: MerchtylServiceWorkerRegistration }>).detail;
      if (detail?.registration) {
        setUpdateRegistration(detail.registration);
      }
    };

    window.addEventListener('beforeinstallprompt', handleInstallPrompt);
    window.addEventListener(pwaUpdateAvailableEvent, handleUpdateAvailable);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleInstallPrompt);
      window.removeEventListener(pwaUpdateAvailableEvent, handleUpdateAvailable);
    };
  }, []);

  const install = async () => {
    const prompt = installPrompt;
    if (!prompt) {
      return;
    }
    setInstallPrompt(null);
    await prompt.prompt();
    await prompt.userChoice.catch(() => undefined);
  };

  return (
    <>
      <Snackbar
        open={Boolean(installPrompt)}
        message="Install Merchtyl for quick access"
        action={(
          <>
            <Button color="secondary" size="small" startIcon={<DownloadIcon />} onClick={() => void install()}>
              Install
            </Button>
            <IconButton aria-label="Dismiss install prompt" color="inherit" size="small" onClick={() => setInstallPrompt(null)}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </>
        )}
      />
      <Snackbar
        open={Boolean(updateRegistration)}
        message="A Merchtyl update is ready"
        action={(
          <>
            <Button
              color="secondary"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => {
                if (updateRegistration) {
                  applyServiceWorkerUpdate(updateRegistration);
                }
              }}
            >
              Update
            </Button>
            <IconButton aria-label="Dismiss update notification" color="inherit" size="small" onClick={() => setUpdateRegistration(null)}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </>
        )}
      />
    </>
  );
}
