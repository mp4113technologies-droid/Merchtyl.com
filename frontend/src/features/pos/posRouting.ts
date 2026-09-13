export type PosRegisterType = 'RETAIL' | 'FOOD_SERVICE';

export function posRouteForRegisterType(registerType: string | null | undefined) {
  if (registerType === 'RETAIL') return '/pos' as const;
  if (registerType === 'FOOD_SERVICE') return '/pos/food' as const;
  return null;
}

export function activeSessionAllowsPos(
  activeRegisterType: string | null | undefined,
  requestedRegisterType: PosRegisterType
) {
  return activeRegisterType === null || activeRegisterType === requestedRegisterType;
}
