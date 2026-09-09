import type { UserRole } from '../api/types';
import type { DeviceEnvironment } from './deviceEnvironment';

const PLATFORM_MANAGEMENT_ROLES = new Set<UserRole>(['PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT_ADMIN']);
const MERCHANT_MANAGEMENT_ROLES = new Set<UserRole>(['OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER']);

const MOBILE_MANAGEMENT_ROUTES = [
  '/',
  '/store-menu',
  '/stores',
  '/registers',
  '/register/history',
  '/reports',
  '/end-of-day-reports',
  '/users',
  '/select-store',
  '/business-day',
  '/unauthorized'
] as const;

const DESKTOP_OPERATIONAL_ROUTES = [
  '/pos',
  '/register/open',
  '/register/current',
  '/register/close',
  '/register/cash-movements',
  '/returns',
  '/lottery/sale',
  '/lottery/payout'
] as const;

function matchesRoute(pathname: string, route: string) {
  if (route === '/') return pathname === '/';
  return pathname === route || pathname.startsWith(`${route}/`);
}

export type MobileAccessPolicy = {
  mobilePortalAllowed: boolean;
  platformManagementUser: boolean;
  merchantManagementUser: boolean;
  posAllowed: boolean;
};

export function getMobileAccessPolicy(roles: UserRole[], device: DeviceEnvironment): MobileAccessPolicy {
  const platformManagementUser = roles.some((role) => PLATFORM_MANAGEMENT_ROLES.has(role));
  const merchantManagementUser = roles.some((role) => MERCHANT_MANAGEMENT_ROLES.has(role));
  return {
    mobilePortalAllowed: device.isDesktop || platformManagementUser || merchantManagementUser,
    platformManagementUser,
    merchantManagementUser,
    posAllowed: device.isDesktop
  };
}

export function isMobileManagementRoute(pathname: string) {
  return MOBILE_MANAGEMENT_ROUTES.some((route) => matchesRoute(pathname, route));
}

export function isDesktopOperationalRoute(pathname: string) {
  return DESKTOP_OPERATIONAL_ROUTES.some((route) => matchesRoute(pathname, route));
}

export function isMobileNavigationRoute(pathname: string) {
  return isMobileManagementRoute(pathname) && pathname !== '/store-menu' && pathname !== '/unauthorized';
}
