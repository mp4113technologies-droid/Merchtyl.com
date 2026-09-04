import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ApiClientError, getCurrentUser, login, logout as logoutRequest, platformLogin, refreshSession } from '../api/client';
import type { AuthResponse, CurrentUserResponse } from '../api/types';

type LoginCredentials = {
  email: string;
  password: string;
};

type SessionStatus = 'loading' | 'authenticated' | 'anonymous';

type SessionContextValue = {
  session: AuthResponse | null;
  currentUser: CurrentUserResponse | null;
  status: SessionStatus;
  sessionExpired: boolean;
  loginWithCredentials: (credentials: LoginCredentials) => Promise<AuthResponse>;
  loginWithPlatformCredentials: (credentials: LoginCredentials) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  clearSessionExpired: () => void;
  getValidAccessToken: () => Promise<string>;
};

const storageKey = 'merchtyl.session';
const refreshLeewayMs = 60_000;
const SessionContext = createContext<SessionContextValue | null>(null);

function readStoredSession(): AuthResponse | null {
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthResponse;
  } catch {
    window.localStorage.removeItem(storageKey);
    return null;
  }
}

function storeSession(session: AuthResponse | null) {
  if (session) {
    window.localStorage.setItem(storageKey, JSON.stringify(session));
    return;
  }
  window.localStorage.removeItem(storageKey);
}

type AuthenticatedSession = AuthResponse & {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
};

function requireAuthenticatedSession(session: AuthResponse): asserts session is AuthenticatedSession {
  if (session.authenticationStatus === 'PASSWORD_CHANGE_REQUIRED'
      || !session.accessToken
      || !session.accessTokenExpiresAt
      || (!isPlatformSession(session) && (!session.refreshToken || !session.refreshTokenExpiresAt))) {
    throw new ApiClientError('Password change is required', 409, 'password_change_required');
  }
}

function expiresSoon(timestamp: string) {
  return Date.parse(timestamp) <= Date.now() + refreshLeewayMs;
}

function expired(timestamp: string) {
  return Date.parse(timestamp) <= Date.now();
}

function isPlatformSession(session: AuthResponse) {
  return session.roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN');
}

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUserResponse | null>(null);
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [sessionExpired, setSessionExpired] = useState(false);

  const clearSession = useCallback((expiredSession = false) => {
    storeSession(null);
    setSession(null);
    setCurrentUser(null);
    setStatus('anonymous');
    setSessionExpired(expiredSession);
  }, []);

  const persistAuthenticatedSession = useCallback((nextSession: AuthResponse, user?: CurrentUserResponse) => {
    requireAuthenticatedSession(nextSession);
    storeSession(nextSession);
    setSession(nextSession);
    setCurrentUser(user ?? {
      userId: nextSession.userId,
      email: nextSession.email,
      displayName: nextSession.displayName,
      roles: nextSession.roles
    });
    setStatus('authenticated');
    setSessionExpired(false);
  }, []);

  const refreshStoredSession = useCallback(async (source: AuthResponse) => {
    if (!source.refreshToken || !source.refreshTokenExpiresAt || expired(source.refreshTokenExpiresAt)) {
      throw new ApiClientError('Session expired', 401, 'session_expired');
    }
    const refreshed = await refreshSession({ refreshToken: source.refreshToken });
    const user = isPlatformSession(refreshed)
      ? undefined
      : await getCurrentUser(refreshed.accessToken!);
    persistAuthenticatedSession(refreshed, user);
    return refreshed;
  }, [persistAuthenticatedSession]);

  const getValidAccessToken = useCallback(async () => {
    if (!session) {
      throw new ApiClientError('Authentication is required', 401, 'unauthorized');
    }
    if (!session.accessToken || !session.accessTokenExpiresAt) {
      throw new ApiClientError('Authentication is required', 401, 'unauthorized');
    }
    const accessToken = session.accessToken;
    if (!expiresSoon(session.accessTokenExpiresAt)) {
      return accessToken;
    }
    if (isPlatformSession(session) && !expired(session.accessTokenExpiresAt)) {
      return accessToken;
    }
    try {
      const refreshed = await refreshStoredSession(session);
      requireAuthenticatedSession(refreshed);
      return refreshed.accessToken;
    } catch (error) {
      clearSession(true);
      throw error;
    }
  }, [clearSession, refreshStoredSession, session]);

  const loginWithCredentials = useCallback(async (credentials: LoginCredentials) => {
    const nextSession = await login(credentials);
    if (nextSession.authenticationStatus === 'PASSWORD_CHANGE_REQUIRED') {
      return nextSession;
    }
    requireAuthenticatedSession(nextSession);
    const user = await getCurrentUser(nextSession.accessToken);
    persistAuthenticatedSession(nextSession, user);
    return nextSession;
  }, [persistAuthenticatedSession]);

  const loginWithPlatformCredentials = useCallback(async (credentials: LoginCredentials) => {
    const nextSession = await platformLogin(credentials);
    persistAuthenticatedSession(nextSession);
    return nextSession;
  }, [persistAuthenticatedSession]);

  const logout = useCallback(async () => {
    const refreshToken = session?.refreshToken ?? null;
    clearSession(false);
    if (refreshToken) {
      try {
        await logoutRequest(refreshToken);
      } catch {
        // The client must still end the local session when server logout cannot complete.
      }
    }
  }, [clearSession, session?.refreshToken]);

  const clearSessionExpired = useCallback(() => setSessionExpired(false), []);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      const stored = readStoredSession();
      if (!stored) {
        if (!cancelled) {
          setStatus('anonymous');
        }
        return;
      }

      try {
        requireAuthenticatedSession(stored);
        if (!isPlatformSession(stored) && expiresSoon(stored.accessTokenExpiresAt)) {
          await refreshStoredSession(stored);
        } else if (!cancelled) {
          const user = isPlatformSession(stored)
            ? {
                userId: stored.userId,
                email: stored.email,
                displayName: stored.displayName,
                roles: stored.roles
              }
            : await getCurrentUser(stored.accessToken!);
          persistAuthenticatedSession(stored, user);
        }
      } catch {
        if (!cancelled) {
          clearSession(true);
        }
      }
    }

    bootstrap();
    return () => {
      cancelled = true;
    };
  }, [clearSession, persistAuthenticatedSession, refreshStoredSession]);

  useEffect(() => {
    if (!session) {
      return undefined;
    }
    if (isPlatformSession(session)) {
      return undefined;
    }
    if (!session.accessTokenExpiresAt) {
      return undefined;
    }
    const delay = Math.max(Date.parse(session.accessTokenExpiresAt) - Date.now() - refreshLeewayMs, 0);
    const timeout = window.setTimeout(() => {
      refreshStoredSession(session).catch(() => clearSession(true));
    }, delay);
    return () => window.clearTimeout(timeout);
  }, [clearSession, refreshStoredSession, session]);

  const value = useMemo<SessionContextValue>(() => ({
    session,
    currentUser,
    status,
    sessionExpired,
    loginWithCredentials,
    loginWithPlatformCredentials,
    logout,
    clearSessionExpired,
    getValidAccessToken
  }), [clearSessionExpired, currentUser, getValidAccessToken, loginWithCredentials, loginWithPlatformCredentials, logout, session, sessionExpired, status]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error('useSession must be used within SessionProvider');
  }
  return context;
}
