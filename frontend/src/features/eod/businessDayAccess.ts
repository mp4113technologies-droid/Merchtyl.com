import type { UserRole } from '../../api/types';

function isBusinessDayManager(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

export function resolveBusinessDayAccess(roles: UserRole[], permissions: string[] | undefined) {
  const hasPermission = (permission: string) => permissions?.includes(permission) ?? false;
  const legacyManagerFallback = permissions === undefined && isBusinessDayManager(roles);

  return {
    canView: hasPermission('BUSINESS_DAY_VIEW') || legacyManagerFallback,
    canOpen: hasPermission('BUSINESS_DAY_OPEN') || legacyManagerFallback,
    canClose: hasPermission('BUSINESS_DAY_CLOSE') || legacyManagerFallback,
    canReopen: hasPermission('BUSINESS_DAY_REOPEN') || legacyManagerFallback
  };
}
