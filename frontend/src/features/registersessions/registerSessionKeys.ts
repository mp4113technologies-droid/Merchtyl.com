export const registerSessionKeys = {
  current: (deviceIdentifier?: string, userId?: string) => [
    'register-session-current',
    userId ?? 'anonymous',
    deviceIdentifier ?? 'operator'
  ] as const
};
