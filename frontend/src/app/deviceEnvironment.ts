import { useMemo } from 'react';

export type DeviceEnvironment = {
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
};

export type DeviceNavigator = Pick<Navigator, 'userAgent' | 'platform' | 'maxTouchPoints'> & {
  userAgentData?: { mobile?: boolean };
};

const MOBILE_USER_AGENT = /iPhone|iPod|Android.*Mobile|Windows Phone|IEMobile|BlackBerry|BB10|webOS|Opera Mini|Opera Mobi/i;
const TABLET_USER_AGENT = /iPad|Android(?!.*Mobile)|Tablet|Kindle|Silk|PlayBook/i;

export function detectDeviceEnvironment(deviceNavigator: DeviceNavigator): DeviceEnvironment {
  const userAgent = deviceNavigator.userAgent ?? '';
  const platform = deviceNavigator.platform ?? '';
  const maxTouchPoints = deviceNavigator.maxTouchPoints ?? 0;

  // Modern iPadOS can request desktop sites and report itself as MacIntel.
  const isDesktopModeIPad = platform === 'MacIntel' && maxTouchPoints > 1;
  const isTablet = isDesktopModeIPad || TABLET_USER_AGENT.test(userAgent);
  const isMobile = !isTablet && (deviceNavigator.userAgentData?.mobile === true || MOBILE_USER_AGENT.test(userAgent));

  return {
    isMobile,
    isTablet,
    isDesktop: !isMobile && !isTablet
  };
}

export function getDeviceEnvironment(): DeviceEnvironment {
  if (typeof navigator === 'undefined') {
    return { isMobile: false, isTablet: false, isDesktop: true };
  }
  return detectDeviceEnvironment(navigator as DeviceNavigator);
}

export function useDeviceEnvironment(): DeviceEnvironment {
  return useMemo(getDeviceEnvironment, []);
}
