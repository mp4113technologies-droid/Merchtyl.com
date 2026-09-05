import { useQuery } from '@tanstack/react-query';
import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { resolveMerchantPortal } from '../api/client';
import { resolvePortalContext, type PortalContext } from './portalContext';

export type MerchantPortalMetadata = {
  merchantSlug: string;
  displayName: string;
  active: boolean;
};

type MerchantPortalState = {
  portalContext: PortalContext;
  merchant?: MerchantPortalMetadata;
  loading: boolean;
  error: boolean;
};

const MerchantPortalContext = createContext<MerchantPortalState | undefined>(undefined);

export function MerchantPortalProvider({ children, hostname }: { children: ReactNode; hostname?: string }) {
  const portalContext = useMemo(() => resolvePortalContext(hostname ?? window.location.hostname), [hostname]);
  const merchantSlug = portalContext.type === 'MERCHANT'
    ? portalContext.merchantSlug
    : portalContext.type === 'DEVELOPMENT' ? portalContext.merchantSlug : undefined;
  const query = useQuery({
    queryKey: ['merchant-portal', merchantSlug ?? ''],
    queryFn: () => resolveMerchantPortal(merchantSlug ?? ''),
    enabled: Boolean(merchantSlug),
    retry: false
  });

  useEffect(() => {
    if (merchantSlug && query.data?.displayName) document.title = `${query.data.displayName} | Merchtyl`;
    else if (portalContext.type === 'PLATFORM') document.title = 'Merchtyl Platform';
    else document.title = 'Merchtyl';
  }, [merchantSlug, portalContext.type, query.data?.displayName]);

  return <MerchantPortalContext.Provider value={{
    portalContext,
    merchant: query.data,
    loading: Boolean(merchantSlug) && query.isLoading,
    error: Boolean(merchantSlug) && query.isError
  }}>{children}</MerchantPortalContext.Provider>;
}

export function useMerchantPortal() {
  const value = useContext(MerchantPortalContext);
  if (!value) throw new Error('useMerchantPortal must be used within MerchantPortalProvider');
  return value;
}
