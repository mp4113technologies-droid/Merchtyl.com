import { detectDeviceEnvironment } from './deviceEnvironment';
import { getMobileAccessPolicy, isDesktopOperationalRoute, isMobileManagementRoute, isMobileNavigationRoute } from './mobileAccessPolicy';

const mobile = detectDeviceEnvironment({
  userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)',
  platform: 'iPhone',
  maxTouchPoints: 5
});
const desktop = detectDeviceEnvironment({
  userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
  platform: 'Win32',
  maxTouchPoints: 0
});

describe('mobile access policy', () => {
  it.each(['OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER'] as const)('allows %s into mobile merchant management', (role) => {
    expect(getMobileAccessPolicy([role], mobile).mobilePortalAllowed).toBe(true);
  });

  it.each(['PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT_ADMIN'] as const)('allows %s into mobile platform management', (role) => {
    const policy = getMobileAccessPolicy([role], mobile);
    expect(policy.mobilePortalAllowed).toBe(true);
    expect(policy.platformManagementUser).toBe(true);
    expect(policy.posAllowed).toBe(false);
  });

  it.each(['CASHIER', 'KITCHEN'] as const)('blocks %s from the mobile merchant portal', (role) => {
    expect(getMobileAccessPolicy([role], mobile).mobilePortalAllowed).toBe(false);
  });

  it('does not let any role bypass the mobile POS policy', () => {
    expect(getMobileAccessPolicy(['OWNER'], mobile).posAllowed).toBe(false);
    expect(getMobileAccessPolicy(['MANAGER'], mobile).posAllowed).toBe(false);
    expect(getMobileAccessPolicy(['PLATFORM_SUPER_ADMIN'], mobile).posAllowed).toBe(false);
  });

  it.each(['CASHIER', 'KITCHEN', 'OWNER', 'MANAGER', 'PLATFORM_SUPER_ADMIN'] as const)('leaves desktop behavior unchanged for %s', (role) => {
    const policy = getMobileAccessPolicy([role], desktop);
    expect(policy.mobilePortalAllowed).toBe(true);
    expect(policy.posAllowed).toBe(true);
  });

  it('classifies management and operational routes centrally', () => {
    expect(isMobileManagementRoute('/reports/sales')).toBe(true);
    expect(isMobileManagementRoute('/users/new')).toBe(true);
    expect(isMobileManagementRoute('/business-day/history')).toBe(true);
    expect(isDesktopOperationalRoute('/pos/food')).toBe(true);
    expect(isDesktopOperationalRoute('/register/cash-movements')).toBe(true);
    expect(isDesktopOperationalRoute('/returns/new')).toBe(true);
    expect(isDesktopOperationalRoute('/lottery/sale')).toBe(true);
    expect(isMobileNavigationRoute('/reports/sales')).toBe(true);
    expect(isMobileNavigationRoute('/pos')).toBe(false);
  });
});
