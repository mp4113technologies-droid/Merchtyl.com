export const registerSessionKeys = {
  current: (deviceIdentifier?: string) => ['register-session-current', deviceIdentifier ?? 'operator'] as const
};
