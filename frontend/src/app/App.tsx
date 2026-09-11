import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';
import BrandingWatermarkOutlinedIcon from '@mui/icons-material/BrandingWatermarkOutlined';
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import MenuIcon from '@mui/icons-material/Menu';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import PointOfSaleOutlinedIcon from '@mui/icons-material/PointOfSaleOutlined';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import SettingsInputComponentOutlinedIcon from '@mui/icons-material/SettingsInputComponentOutlined';
import StoreMallDirectoryOutlinedIcon from '@mui/icons-material/StoreMallDirectoryOutlined';
import StorefrontIcon from '@mui/icons-material/Storefront';
import StraightenOutlinedIcon from '@mui/icons-material/StraightenOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import AssignmentTurnedInOutlinedIcon from '@mui/icons-material/AssignmentTurnedInOutlined';
import PublicOutlinedIcon from '@mui/icons-material/PublicOutlined';
import QrCodeScannerOutlinedIcon from '@mui/icons-material/QrCodeScannerOutlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import ToggleOnOutlinedIcon from '@mui/icons-material/ToggleOnOutlined';
import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CloseIcon from '@mui/icons-material/Close';
import KeyboardOutlinedIcon from '@mui/icons-material/KeyboardOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import {
  Alert,
  AppBar,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  CssBaseline,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  ThemeProvider,
  Tooltip,
  Toolbar,
  Typography,
  useMediaQuery
} from '@mui/material';
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import {
  BrowserRouter,
  Link,
  MemoryRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation
} from 'react-router-dom';
import { SessionProvider, useSession } from './session';
import { merchtylTokens, posTheme, posTokens, theme } from './theme';
import { getApplicationDeviceIdentifier } from './deviceIdentity';
import { AuthPage } from '../features/auth/AuthPage';
import { FirstLoginPasswordChangePage } from '../features/auth/FirstLoginPasswordChangePage';
import { ForgotPasswordPage, ResetPasswordPage } from '../features/auth/PasswordResetPages';
import { PlatformAdminActivationPage } from '../features/auth/PlatformAdminActivationPage';
import { PlatformAdminsPage } from '../features/platform/PlatformAdminsPage';
import {
  PlatformBillingOverviewPage,
  PlatformBillingSettingsPage,
  PlatformInvoicesPage,
  PlatformPricingPlansPage,
  PlatformSubscriptionsPage
} from '../features/platform/PlatformBillingPages';
import { MerchantBillingPage } from '../features/billing/MerchantBillingPage';
import { NewRegisterPage, RegisterDetailPage, RegistersPage } from '../features/registers/RegisterPages';
import { CashMovementPage, RegisterClosePage, RegisterCurrentPage, RegisterHistoryPage, RegisterOpenPage } from '../features/registersessions/RegisterSessionPages';
import { NewStorePage, StoreDetailPage, StoresPage } from '../features/stores/StorePages';
import { NewUserPage, RolesPage, UserDetailPage, UsersPage } from '../features/users/UserPages';
import { StoreSelectionPage } from '../features/users/StoreSelectionPage';
import { BrandsPage, CategoriesPage, UnitsPage } from '../features/catalogue/CatalogueReferencePages';
import { NewSupplierPage, SupplierDetailPage, SuppliersPage } from '../features/suppliers/SupplierPages';
import { NewProductPage, ProductDetailPage, ProductsPage } from '../features/products/ProductPages';
import { InventoryAdjustmentsPage, NewInventoryAdjustmentPage } from '../features/inventory/InventoryAdjustmentPages';
import { InventoryReportingPage } from '../features/inventory/InventoryReportingPages';
import { InitialInventorySetupPage } from '../features/inventory/InitialInventorySetupPage';
import { NewStockCountPage, StockCountDetailPage, StockCountsPage } from '../features/inventory/StockCountPages';
import { HeldSalesPage, PosCartPage } from '../features/pos/PosPages';
import { FoodPosPage } from '../features/pos/FoodPosPage';
import { PosDeviceGuard } from '../features/pos/PosDeviceGuard';
import { DiscountDefinitionsPage } from '../features/discounts/DiscountDefinitionsPage';
import { FoodMenuPage } from '../features/foodmenu/FoodMenuPage';
import { NewReturnPage, ReturnDetailPage, ReturnsPage } from '../features/returns/ReturnPages';
import { LotteryOperatorDetailPage, LotteryOperatorsPage, NewLotteryOperatorPage } from '../features/lottery/LotteryOperatorPages';
import { LotteryPayoutPoliciesPage, LotteryPayoutPolicyDetailPage, NewLotteryPayoutPolicyPage } from '../features/lottery/LotteryPayoutPolicyPages';
import { LotterySalePage } from '../features/lottery/LotterySalePage';
import { LotteryPayoutPage } from '../features/lottery/LotteryPayoutPage';
import { LotteryManagementPage } from '../features/lottery/LotteryManagementPage';
import { LotteryHistoryPage } from '../features/lottery/LotteryHistoryPage';
import { LotteryCommissionRulePage } from '../features/lottery/LotteryCommissionRulePage';
import { LotterySettlementPage } from '../features/lottery/LotterySettlementPage';
import { RegisterReportsPage } from '../features/reports/RegisterReportsPage';
import { SalesReportsPage } from '../features/reports/SalesReportsPage';
import { LotteryReportsPage } from '../features/reports/LotteryReportsPage';
import { BusinessDayClosePage, BusinessDayHistoryPage, BusinessDayPage, EndOfDayReportDetailPage, EndOfDayReportsPage } from '../features/eod/BusinessDayPages';
import { resolveBusinessDayAccess } from '../features/eod/businessDayAccess';
import { PrinterSettingsPage } from '../features/settings/PrinterSettingsPage';
import { FeatureSettingsPage } from '../features/settings/FeatureSettingsPage';
import { ScannerTestPage } from '../features/settings/ScannerTestPage';
import {
  AdministrativeAreasPage,
  CountriesPage,
  ProductTaxCategoryAssignmentsPage,
  TaxCategoriesPage,
  TaxComponentsPage,
  TaxGeographyRedirect,
  TaxGroupComponentsPage,
  TaxGroupsPage,
  TaxJurisdictionsPage,
  TaxRatesPage,
  TaxRulesPage,
  TaxTypesPage
} from '../features/tax/TaxGeographyPages';
import { TaxSimulatorPage } from '../features/tax/TaxSimulatorPage';
import { OwnerDashboardPage } from '../features/dashboard/OwnerDashboardPage';
import { PwaPrompt } from '../features/pwa/PwaPrompt';
import { getBusinessDayOperationalState, getCurrentRegisterSession, listRegisters, listStores, openBusinessDay } from '../api/client';
import { MerchantPortalProvider, useMerchantPortal } from './MerchantPortalContext';
import { MerchtylLogo } from './MerchtylLogo';
import { useDeviceEnvironment } from './deviceEnvironment';
import { isMobileManagementRoute, isMobileNavigationRoute } from './mobileAccessPolicy';
import { MobileManagementRouteGuard, MobilePortalGuard } from './MobileAccessGuards';
import { PublicComingSoonPage } from '../features/public/PublicComingSoonPage';
import { registerSessionKeys } from '../features/registersessions/registerSessionKeys';
import {
  NewPlatformMerchantPage,
  PlatformAuditPage,
  PlatformDashboardPage,
  PlatformLoginPage,
  PlatformMerchantDetailPage,
  PlatformMerchantsPage,
  PlatformSettingsPage
} from '../features/platform/PlatformPages';

const drawerWidth = 264;

const queryClientOptions = {
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000
    }
  }
};

type AppProps = {
  initialEntries?: string[];
  hostname?: string;
};

function LoadingState() {
  return (
    <Stack minHeight="100dvh" alignItems="center" justifyContent="center" spacing={2} role="status" aria-live="polite">
      <MerchtylLogo variant="icon" size="icon" decorative />
      <CircularProgress aria-label="Loading application" />
      <Typography color="text.secondary">Loading workspace</Typography>
    </Stack>
  );
}

function UnknownPortal({ unavailable = false }: { unavailable?: boolean }) {
  return <Stack minHeight="100dvh" alignItems="center" justifyContent="center" spacing={2} px={3} textAlign="center">
    <Typography variant="h4" component="h1">{unavailable ? 'This Merchtyl portal is currently unavailable.' : "We couldn't find this Merchtyl portal."}</Typography>
    <Button href="https://www.merchtyl.com" variant="contained">Go to Merchtyl</Button>
  </Stack>;
}

function PortalBoundary({ children }: { children: React.ReactNode }) {
  const { portalContext: context, merchant, loading, error } = useMerchantPortal();
  const location = useLocation();
  const merchantPortal = context.type === 'MERCHANT' || (context.type === 'DEVELOPMENT' && Boolean(context.merchantSlug));
  if (context.type === 'PUBLIC') return <PublicComingSoonPage />;
  if (context.type === 'UNKNOWN') return <UnknownPortal />;
  const platformPublicAuthPath = ['/login', '/forgot-password', '/reset-password', '/activate-platform-admin']
    .includes(location.pathname);
  if (context.type === 'PLATFORM' && !location.pathname.startsWith('/platform') && !platformPublicAuthPath) {
    return <Navigate to="/platform" replace />;
  }
  if (merchantPortal) {
    if (loading) return <LoadingState />;
    if (error) return <UnknownPortal />;
    if (!merchant?.active) return <UnknownPortal unavailable />;
    if (location.pathname.startsWith('/platform')) return <UnknownPortal />;
  }
  return <>{children}</>;
}

function ProtectedRoute() {
  const location = useLocation();
  const { status, session } = useSession();

  if (status === 'loading') {
    return <LoadingState />;
  }
  if (!session) {
    if (location.pathname.startsWith('/platform')) {
      return <Navigate to={`/platform/login?from=${encodeURIComponent(location.pathname)}`} replace />;
    }
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

function UnauthorizedPage() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const platformUser = roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN');
  return (
    <Stack spacing={2} sx={{ maxWidth: 680 }}>
      <ShieldOutlinedIcon color="secondary" sx={{ fontSize: 42 }} />
      <Typography variant="h5" component="h1">You don't have access to this feature</Typography>
      <Typography color="text.secondary">
        {platformUser
          ? "Your account doesn't currently have permission to use this platform section."
          : "Your account doesn't currently have permission to use this section for the selected store."}
      </Typography>
      <Typography color="text.secondary">
        {platformUser
          ? 'If you believe you should have access, contact a Platform Super Admin.'
          : 'If you believe you should have access, contact your Store Manager or Owner.'}
      </Typography>
      <Button component={Link} to={platformUser ? '/platform' : '/store-menu'} variant="contained" sx={{ alignSelf: 'flex-start' }}>
        {platformUser ? 'Return to Platform Dashboard' : 'Return to Store Menu'}
      </Button>
    </Stack>
  );
}

function HomeRedirect() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const destination = homeDestination(roles);
  if (destination) return <Navigate to={destination} replace />;
  return <OwnerDashboardPage />;
}

export function homeDestination(roles: string[]) {
  if (roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN')) return '/platform';
  if (roles.includes('CASHIER') && roles.includes('KITCHEN')) return '/store-menu';
  if (roles.includes('KITCHEN')) return '/pos/food';
  if (roles.includes('CASHIER')) return '/store-menu';
  return null;
}

function PosLayout() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const location = useLocation();
  const browserDeviceIdentifier = getApplicationDeviceIdentifier();
  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    refetchInterval: 15_000
  });
  const stores = useQuery({
    queryKey: ['stores', 'pos'],
    queryFn: async () => listStores(await getValidAccessToken(), { size: 100 })
  });
  const registers = useQuery({
    queryKey: ['registers', 'pos'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { size: 100 })
  });
  const activeStore = stores.data?.content.find((store) => store.id === current.data?.storeId);
  const activeRegister = registers.data?.content.find((register) => register.id === current.data?.registerId);
  const posLabel = location.pathname.startsWith('/pos/food') ? 'Restaurant POS' : 'Retail POS';

  return (
    <ThemeProvider theme={posTheme}>
      <Box sx={{ width: '100%', minWidth: 0, minHeight: '100dvh', bgcolor: 'background.default' }}>
        <Box component="header" sx={{ height: 56, px: { xs: 1, sm: 2 }, background: `linear-gradient(100deg, ${posTokens.colors.navyDark}, ${posTokens.colors.navy})`, color: '#fff', display: 'flex', gap: { xs: 0.75, sm: 1.5 }, alignItems: 'center', overflow: 'hidden' }}>
          <Button component={Link} to="/store-menu" color="inherit" size="small" startIcon={<ArrowBackIcon />} sx={{ flexShrink: 0, '&:hover': { bgcolor: 'rgba(255,255,255,.1)' } }}>
            Back to Store Menu
          </Button>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ flexGrow: 1, minWidth: 0 }}>
            <Box sx={{ width: 34, height: 34, borderRadius: 1, bgcolor: '#fff', p: 0.35, display: 'grid', placeItems: 'center', flexShrink: 0 }}>
              <MerchtylLogo variant="icon" size="icon" />
            </Box>
            <Typography variant="h6" noWrap sx={{ fontSize: 19 }}>{posLabel}</Typography>
          </Stack>
          <Stack direction="row" alignItems="center" spacing={{ xs: 0.75, md: 1.25 }} sx={{ minWidth: 0 }}>
            <Box sx={{ minWidth: 0, maxWidth: 180, display: { xs: 'none', sm: 'block' } }}>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,.7)' }}>Store</Typography>
              <Typography variant="body2" fontWeight={700} noWrap title={activeStore ? `${activeStore.name} (${activeStore.code})` : undefined}>{activeStore ? `${activeStore.name} (${activeStore.code})` : 'Not selected'}</Typography>
            </Box>
            <Box sx={{ minWidth: 0, maxWidth: 180, display: { xs: 'none', md: 'block' } }}>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,.7)' }}>Register</Typography>
              <Typography variant="body2" fontWeight={700} noWrap title={activeRegister ? `${activeRegister.name} (${activeRegister.code})` : undefined}>{activeRegister ? `${activeRegister.name} (${activeRegister.code})` : 'Not selected'}</Typography>
            </Box>
            <Chip size="small" label={current.data?.status === 'OPEN' ? '● OPEN' : 'NO REGISTER'} sx={{ flexShrink: 0, bgcolor: current.data?.status === 'OPEN' ? 'rgba(22,163,74,.2)' : 'rgba(255,255,255,.12)', color: '#fff', fontWeight: 800, border: '1px solid', borderColor: current.data?.status === 'OPEN' ? 'rgba(134,239,172,.45)' : 'rgba(255,255,255,.24)' }} />
            <Typography variant="caption" noWrap sx={{ display: { xs: 'none', lg: 'block' }, color: 'rgba(255,255,255,.75)' }}>{currentUser?.displayName ?? session?.displayName}</Typography>
          </Stack>
        </Box>
        <Box component="main" sx={{ p: { xs: 1, md: 2 }, minWidth: 0, minHeight: 'calc(100dvh - 56px)' }}>
          <Outlet />
        </Box>
      </Box>
    </ThemeProvider>
  );
}

function StoreMenuPage() {
  const { currentUser, session, getValidAccessToken, logout } = useSession();
  const queryClient = useQueryClient();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const permissions = currentUser?.permissions ?? [];
  const businessDayAccess = resolveBusinessDayAccess(roles, currentUser?.permissions);
  const browserDeviceIdentifier = getApplicationDeviceIdentifier();
  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    refetchInterval: 15_000
  });
  const stores = useQuery({
    queryKey: ['stores', 'store-menu'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 })
  });
  const registers = useQuery({
    queryKey: ['registers', 'store-menu'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100 })
  });
  const activeSession = current.data?.status === 'OPEN' ? current.data : null;
  const selectedStoreId = activeSession?.storeId
    ?? window.localStorage.getItem('merchtyl.activeStoreId')
    ?? stores.data?.content[0]?.id
    ?? '';
  const canViewBusinessDay = businessDayAccess.canView;
  const canOpenBusinessDay = businessDayAccess.canOpen;
  const businessDay = useQuery({
    queryKey: ['business-day', 'operational-state', selectedStoreId],
    queryFn: async () => getBusinessDayOperationalState(await getValidAccessToken(), selectedStoreId),
    enabled: canViewBusinessDay && Boolean(selectedStoreId)
  });
  const startBusinessDay = useMutation({
    mutationFn: async () => openBusinessDay(await getValidAccessToken(), { storeId: selectedStoreId }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] })
      ]);
    }
  });
  const store = stores.data?.content.find((item) => item.id === activeSession?.storeId);
  const register = registers.data?.content.find((item) => item.id === activeSession?.registerId);
  const foodServiceEnabled = activeSession
    ? Boolean(store?.capabilities?.includes('FOOD_SERVICE'))
    : Boolean(stores.data?.content.some((item) => item.capabilities?.includes('FOOD_SERVICE')));
  const retailEnabled = activeSession
    ? Boolean(store?.capabilities?.includes('RETAIL'))
    : Boolean(stores.data?.content.some((item) => item.capabilities?.includes('RETAIL')));
  const isCashier = roles.includes('CASHIER');
  const { isDesktop } = useDeviceEnvironment();
  const operations = [
    { label: 'Retail POS', to: '/pos', visible: retailEnabled && permissions.includes('POS_ACCESS'), desktopOnly: true },
    { label: 'Restaurant Menu', to: '/food-menu', visible: foodServiceEnabled && permissions.includes('FOOD_POS_ACCESS') },
    { label: 'Discounts', to: '/discounts', visible: permissions.includes('DISCOUNT_VIEW') },
    { label: 'Orders', to: '/sales', visible: foodServiceEnabled && permissions.includes('FOOD_ORDER_VIEW') },
    { label: 'Restaurant / Kitchen POS', to: '/pos/food', visible: foodServiceEnabled && permissions.includes('FOOD_POS_ACCESS'), desktopOnly: true },
    { label: 'Inventory / Product Lookup', to: '/inventory', visible: true },
    { label: 'Returns', to: '/returns', visible: true },
    { label: 'Current Register', to: '/register/current', visible: true },
    { label: 'Cash Operations', to: '/register/cash-movements', visible: true },
    { label: 'Dashboard', to: '/', visible: !isCashier }
  ].filter((item) => item.visible && (isDesktop || item.desktopOnly || isMobileManagementRoute(item.to)));

  return (
    <Stack spacing={{ xs: 2, lg: 3 }} sx={{ width: '100%', maxWidth: 1100, mx: 'auto', minWidth: 0 }}>
      <Box>
        <Typography variant="h4" component="h1">Store Menu</Typography>
        <Typography color="text.secondary">Store operations available to this account.</Typography>
      </Box>
      <Paper variant="outlined" sx={{ p: { xs: 2, lg: 3 } }}>
        {activeSession ? (
          <Stack spacing={1.5}>
            <Typography variant="overline" color="text.secondary">Active register session</Typography>
            <Typography variant="h6">{register ? `${register.name} (${register.code})` : activeSession.registerId}</Typography>
            <Typography>{store ? `${store.name} (${store.code})` : activeSession.storeId} • OPEN</Typography>
            <Typography variant="body2" color="text.secondary">Opened {new Date(activeSession.openedAt).toLocaleString()}</Typography>
            <Button component={Link} to={activeSession.registerType === 'FOOD_SERVICE' ? '/pos/food' : '/pos'} variant="contained" startIcon={<PointOfSaleOutlinedIcon />} disabled={!isDesktop} sx={{ alignSelf: 'flex-start' }}>
              {isDesktop ? 'Return to POS' : 'Desktop register required'}
            </Button>
          </Stack>
        ) : (
          <Stack spacing={1.5}>
            <Typography variant="h6">No active register</Typography>
            <Button component={Link} to="/register/open" variant="contained" disabled={!isDesktop} sx={{ alignSelf: 'flex-start' }}>
              {isDesktop ? 'Open / Select Register' : 'Desktop register required'}
            </Button>
          </Stack>
        )}
      </Paper>
      {canViewBusinessDay && selectedStoreId ? (
        <Paper variant="outlined" sx={{ p: { xs: 2, lg: 3 } }}>
          <Stack spacing={1.5}>
            <Typography variant="h6">Business Day</Typography>
            {businessDay.isLoading ? <CircularProgress size={24} aria-label="Loading business day" /> : null}
            {businessDay.data?.state === 'OPEN' ? (
              <Alert severity="success" action={businessDayAccess.canClose ? (
                <Button component={Link} to={`/business-day/close?storeId=${selectedStoreId}`} color="inherit">Close Business Day</Button>
              ) : undefined}>Business Day Open</Alert>
            ) : null}
            {(businessDay.data?.state === 'NO_BUSINESS_DAY_TODAY' || businessDay.data?.state === 'HISTORICAL_CLOSED') ? (
              <>
                <Typography>Business Day not started</Typography>
                {canOpenBusinessDay ? (
                  <Button variant="contained" startIcon={<LockOpenOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}
                    disabled={startBusinessDay.isPending} onClick={() => startBusinessDay.mutate()}>
                    {startBusinessDay.isPending ? 'Starting…' : 'Start Business Day'}
                  </Button>
                ) : null}
              </>
            ) : null}
            {businessDay.data?.state === 'PREVIOUS_DAY_STILL_OPEN' ? (
              <Alert severity="warning">The previous business day is still open. Ask a Manager or Owner to close it before starting today's business day.</Alert>
            ) : null}
            {businessDay.data?.state === 'CLOSED_TODAY' ? (
              <Alert severity="warning">Today's business day has been closed. Ask a Manager or Owner to reopen it.</Alert>
            ) : null}
            {startBusinessDay.isError ? <Alert severity="error">{startBusinessDay.error instanceof Error ? startBusinessDay.error.message : 'Unable to start the business day.'}</Alert> : null}
          </Stack>
        </Paper>
      ) : null}
      <Box>
        <Typography variant="h6" sx={{ mb: 2 }}>Store Operations</Typography>
        <Grid container spacing={2}>
          {operations.map((item) => (
            <Grid item xs={12} sm={6} md={4} key={item.to}>
              <Button component={Link} to={item.to} variant="outlined" fullWidth disabled={item.desktopOnly && !isDesktop} sx={{ minHeight: 64 }}>
                <Stack spacing={0.25}>
                  <span>{item.label}</span>
                  {item.desktopOnly && !isDesktop ? <Typography component="span" variant="caption">Desktop register required</Typography> : null}
                </Stack>
              </Button>
            </Grid>
          ))}
        </Grid>
      </Box>
      <Button color="inherit" startIcon={<LogoutIcon />} onClick={() => void logout()} sx={{ alignSelf: 'flex-start' }}>Logout</Button>
    </Stack>
  );
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const location = useLocation();
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canViewPlatform = roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN');
  const canViewMerchantBilling = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER');
  const canViewStores = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
  const canViewRegisters = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
  const canViewUsers = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
  const canViewCatalogue = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
  const canViewInventory = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
  const canViewTax = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
  const canViewReports = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
  const canViewFeatures = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
  const canRecordLottery = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
  const canViewLottery = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
  const browserDeviceIdentifier = getApplicationDeviceIdentifier();
  const activeRegister = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canViewRegisters,
    refetchInterval: 30_000
  });
  const accessibleStores = useQuery({
    queryKey: ['stores', 'navigation'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled: canViewStores
  });
  const foodServiceEnabled = Boolean(accessibleStores.data?.content.some((store) => store.capabilities?.includes('FOOD_SERVICE')));
  const permissions = currentUser?.permissions ?? [];
  const businessDayAccess = resolveBusinessDayAccess(roles, currentUser?.permissions);
  const { isDesktop: isDesktopDevice } = useDeviceEnvironment();
  const navigationSections = canViewPlatform ? [
    { id: 'platform', label: 'Platform', items: [
      { label: 'Overview', to: '/platform', icon: <ShieldOutlinedIcon /> },
      { label: 'Merchants', to: '/platform/merchants', icon: <StorefrontIcon /> },
      { label: 'Billing', to: '/platform/billing', icon: <PaymentsOutlinedIcon /> },
      { label: 'Audit', to: '/platform/audit', icon: <HistoryOutlinedIcon /> },
      { label: 'Platform Administrators', to: '/platform/admins', icon: <ManageAccountsOutlinedIcon /> },
      { label: 'Settings', to: '/platform/settings', icon: <SettingsInputComponentOutlinedIcon /> }
    ] }
  ] : [
    { id: 'overview', label: 'Overview', items: [
      { label: 'Dashboard', to: '/', icon: <DashboardOutlinedIcon />, visible: true },
      { label: 'Subscription & Billing', to: '/billing', icon: <PaymentsOutlinedIcon />, visible: canViewMerchantBilling }
    ] },
    { id: 'operations', label: 'Store Operations', items: [
      { label: 'Stores', to: '/stores', icon: <StoreMallDirectoryOutlinedIcon />, visible: canViewStores },
      { label: 'Registers', to: '/registers', icon: <PointOfSaleOutlinedIcon />, visible: canViewRegisters },
      { label: 'Current Register', to: '/register/current', icon: <LockOpenOutlinedIcon />, visible: canViewRegisters },
      { label: 'Cash Movements', to: '/register/cash-movements', icon: <PaymentsOutlinedIcon />, visible: canViewRegisters },
      { label: 'Register History', to: '/register/history', icon: <ReceiptLongOutlinedIcon />, visible: canViewRegisters },
      { label: 'Business Day', to: '/business-day', icon: <EventAvailableOutlinedIcon />, visible: businessDayAccess.canView }
    ] },
    { id: 'sales', label: 'Sales', items: [
      { label: 'Retail POS', to: '/pos', icon: <PointOfSaleOutlinedIcon />, visible: canViewRegisters, desktopOnly: true },
      { label: 'Restaurant POS', to: '/pos/food', icon: <RestaurantIcon />, visible: foodServiceEnabled && permissions.includes('FOOD_POS_ACCESS'), desktopOnly: true },
      { label: 'Held Sales', to: '/pos/held-sales', icon: <PauseCircleOutlineIcon />, visible: canViewRegisters },
      { label: 'Returns', to: '/returns', icon: <ReceiptLongOutlinedIcon />, visible: canViewRegisters },
      { label: 'Discounts', to: '/discounts', icon: <ConfirmationNumberOutlinedIcon />, visible: permissions.includes('DISCOUNT_VIEW') }
    ] },
    { id: 'catalog', label: 'Catalog', items: [
      { label: 'Products', to: '/products', icon: <Inventory2OutlinedIcon />, visible: canViewCatalogue },
      { label: 'Restaurant Menu', to: '/food-menu', icon: <RestaurantIcon />, visible: foodServiceEnabled && permissions.includes('FOOD_POS_ACCESS') },
      { label: 'Categories', to: '/categories', icon: <CategoryOutlinedIcon />, visible: canViewCatalogue },
      { label: 'Brands', to: '/brands', icon: <BrandingWatermarkOutlinedIcon />, visible: canViewCatalogue },
      { label: 'Units', to: '/settings/units', icon: <StraightenOutlinedIcon />, visible: canViewCatalogue },
      { label: 'Suppliers', to: '/suppliers', icon: <LocalShippingOutlinedIcon />, visible: canViewCatalogue }
    ] },
    { id: 'inventory', label: 'Inventory', items: [
      { label: 'Inventory', to: '/inventory', icon: <AssignmentTurnedInOutlinedIcon />, visible: canViewInventory },
      { label: 'Initial Inventory Setup', to: '/inventory/initial-setup', icon: <UploadFileIcon />, visible: canViewInventory && permissions.includes('INVENTORY_MANAGE') && permissions.includes('PRODUCT_CREATE') },
      { label: 'Stock Counts', to: '/inventory/counts', icon: <AssignmentTurnedInOutlinedIcon />, visible: canViewInventory },
      { label: 'Adjustments', to: '/inventory/adjustments', icon: <AssignmentTurnedInOutlinedIcon />, visible: canViewInventory }
    ] },
    { id: 'reports', label: 'Reports', items: [
      { label: 'Sales Reports', to: '/reports/sales', icon: <AssessmentOutlinedIcon />, visible: canViewReports },
      { label: 'Register Reports', to: '/reports/registers', icon: <AssessmentOutlinedIcon />, visible: canViewReports },
      { label: 'EOD Reports', to: '/end-of-day-reports', icon: <AssessmentOutlinedIcon />, visible: canViewReports },
      { label: 'Lottery Reports', to: '/reports/lottery', icon: <AssessmentOutlinedIcon />, visible: canViewReports }
    ] },
    { id: 'lottery', label: 'Lottery', items: [
      { label: 'Lottery Sale', to: '/lottery/sale', icon: <ConfirmationNumberOutlinedIcon />, visible: canRecordLottery },
      { label: 'Lottery Payout', to: '/lottery/payout', icon: <PaymentsOutlinedIcon />, visible: canRecordLottery },
      { label: 'Lottery History', to: '/lottery/history', icon: <HistoryOutlinedIcon />, visible: canRecordLottery },
      { label: 'Lottery Management', to: '/lottery/management', icon: <HistoryOutlinedIcon />, visible: canRecordLottery },
      { label: 'Lottery Operators', to: '/lottery/operators', icon: <ConfirmationNumberOutlinedIcon />, visible: canViewLottery },
      { label: 'Payout Policies', to: '/lottery/payout-policies', icon: <PaymentsOutlinedIcon />, visible: canViewLottery },
      { label: 'Commission Rules', to: '/lottery/commission-rules', icon: <CalculateOutlinedIcon />, visible: canViewLottery },
      { label: 'Settlements', to: '/lottery/settlements', icon: <CalculateOutlinedIcon />, visible: canViewLottery }
    ] },
    { id: 'configuration', label: 'Configuration', items: [
      { label: 'Tax', to: '/tax/rules', icon: <PublicOutlinedIcon />, visible: canViewTax },
      { label: 'Tax Test', to: '/settings/taxes/test', icon: <CalculateOutlinedIcon />, visible: canViewTax && import.meta.env.DEV },
      { label: 'Features', to: '/settings/features', icon: <ToggleOnOutlinedIcon />, visible: canViewFeatures },
      { label: 'Printers', to: '/settings/hardware/printers', icon: <SettingsInputComponentOutlinedIcon />, visible: canViewRegisters },
      { label: 'Scanner Test', to: '/settings/hardware/scanner-test', icon: <QrCodeScannerOutlinedIcon />, visible: canViewRegisters }
    ] },
    { id: 'access', label: 'Access & Security', items: [
      { label: 'Users', to: '/users', icon: <ManageAccountsOutlinedIcon />, visible: canViewUsers },
      { label: 'Roles', to: '/roles', icon: <BadgeOutlinedIcon />, visible: canViewUsers }
    ] }
  ];

  return (
    <Stack sx={{ height: '100%', minHeight: 0, overflow: 'hidden' }}>
      <Toolbar sx={{ minHeight: 64, px: 2, bgcolor: '#fff', flexShrink: 0 }}>
        <MerchtylLogo size="small" sx={{ objectPosition: 'left center' }} />
      </Toolbar>
      <Divider />
      <List component="div" sx={{ px: 1, py: 1.5, minWidth: 0, flex: 1, minHeight: 0, overflowY: 'auto' }}>
        {navigationSections.map((section) => {
          const visibleItems = section.items.filter((item) => (!('visible' in item) || item.visible !== false)
            && (isDesktopDevice || canViewPlatform || isMobileNavigationRoute(item.to)));
          if (visibleItems.length === 0) return null;
          return <Box component="li" key={section.id} sx={{ listStyle: 'none', mb: 1.75, '&:last-child': { mb: 0 } }}>
            <Typography component="div" variant="overline" sx={{ display: 'block', px: 1.25, mb: 0.25, color: 'text.secondary', fontSize: 11, fontWeight: 700, letterSpacing: '.07em', lineHeight: '22px' }}>{section.label}</Typography>
            <List disablePadding>
              {visibleItems.map((item) => {
                const selected = item.to === '/' || item.to === '/pos'
                  ? location.pathname === item.to
                  : location.pathname === item.to || location.pathname.startsWith(`${item.to}/`);
                return <ListItemButton key={item.to} component={Link} to={item.to} selected={selected} onClick={onNavigate} sx={{ minHeight: 40, py: 0.5, px: 1.25, mb: 0.25, borderRadius: 1, position: 'relative', '&.Mui-selected::before': { content: '""', position: 'absolute', left: 0, top: 7, bottom: 7, width: 3, borderRadius: 2, bgcolor: 'primary.main' }, '& .MuiListItemIcon-root': { minWidth: 34, color: selected ? 'primary.main' : 'text.secondary' }, '& .MuiSvgIcon-root': { fontSize: 20 }, '& .MuiListItemText-primary': { fontSize: 14, fontWeight: selected ? 700 : 500 } }}>
                  <ListItemIcon>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} secondary={'desktopOnly' in item && item.desktopOnly && !isDesktopDevice ? 'Desktop required' : undefined} />
                </ListItemButton>;
              })}
            </List>
          </Box>;
        })}
      </List>
      {activeRegister.data?.status === 'OPEN' ? (
        <Box sx={{ p: 1.5, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper', flexShrink: 0 }}>
          <Typography variant="overline" color="text.secondary">Active register</Typography>
          <Typography variant="body2" fontWeight={700} noWrap>
            Register {activeRegister.data.registerId.slice(0, 8)} • OPEN
          </Typography>
          <Button component={Link} to={activeRegister.data.registerType === 'FOOD_SERVICE' ? '/pos/food' : '/pos'} size="small" variant="contained" fullWidth sx={{ mt: 1 }} onClick={onNavigate} disabled={!isDesktopDevice}>
            {isDesktopDevice ? 'Return to POS' : 'Desktop required'}
          </Button>
        </Box>
      ) : null}
    </Stack>
  );
}

function AppShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const { currentUser, session, logout } = useSession();
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg'));
  const location = useLocation();
  const mainRef = useRef<HTMLElement | null>(null);
  const desktopNavRef = useRef<HTMLElement | null>(null);
  const mobileNavRef = useRef<HTMLElement | null>(null);
  const displayName = currentUser?.displayName ?? session?.displayName ?? 'User';
  const email = currentUser?.email ?? session?.email ?? '';
  const roles = currentUser?.roles ?? session?.roles ?? [];

  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
  }, [location.pathname]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const isEditable = target instanceof HTMLInputElement
        || target instanceof HTMLTextAreaElement
        || target instanceof HTMLSelectElement
        || target?.isContentEditable;

      if (event.key === '?' && !event.metaKey && !event.ctrlKey && !event.altKey && !isEditable) {
        event.preventDefault();
        setShortcutsOpen(true);
        return;
      }

      if (event.altKey && event.key.toLowerCase() === 'm') {
        event.preventDefault();
        if (isDesktop) {
          desktopNavRef.current?.querySelector<HTMLElement>('a, button')?.focus();
        } else {
          setMobileOpen(true);
        }
      }

      if (event.altKey && event.key.toLowerCase() === 's') {
        event.preventDefault();
        mainRef.current?.focus();
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isDesktop]);

  useEffect(() => {
    if (mobileOpen && !isDesktop) {
      window.setTimeout(() => {
        mobileNavRef.current?.querySelector<HTMLElement>('a, button')?.focus();
      });
    }
  }, [isDesktop, mobileOpen]);

  return (
    <Box sx={{ display: 'flex', width: '100%', minWidth: 0, minHeight: '100dvh', bgcolor: 'background.default' }}>
      <Box
        component="a"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          mainRef.current?.focus();
        }}
        sx={{
          position: 'fixed',
          left: 16,
          top: 16,
          zIndex: (muiTheme) => muiTheme.zIndex.tooltip + 1,
          px: 2,
          py: 1,
          borderRadius: 1,
          bgcolor: 'background.paper',
          color: 'primary.main',
          boxShadow: 3,
          transform: 'translateY(-150%)',
          transition: 'transform 120ms ease',
          '&:focus': {
            transform: 'translateY(0)'
          }
        }}
      >
        Skip to content
      </Box>
      <AppBar
        position="fixed"
        color="primary"
        elevation={0}
        sx={{
          background: `linear-gradient(100deg, ${merchtylTokens.colors.navyDark}, ${merchtylTokens.colors.navy})`,
          color: '#fff',
          width: { lg: `calc(100% - ${drawerWidth}px)` },
          ml: { lg: `${drawerWidth}px` }
        }}
      >
        <Toolbar sx={{ gap: { xs: 0.5, sm: 2 }, px: { xs: 1, sm: 2 } }}>
          <IconButton
            color="inherit"
            edge="start"
            aria-label="Open navigation"
            onClick={() => setMobileOpen(true)}
            sx={{ display: { lg: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ flexGrow: 1, minWidth: 0 }}>
            <Typography variant="subtitle1" component="div" noWrap>{displayName}</Typography>
            <Typography variant="body2" noWrap sx={{ color: 'rgba(255,255,255,.72)' }}>{email}</Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ display: { xs: 'none', sm: 'flex' } }}>
            <Avatar sx={{ width: 34, height: 34 }}>
              <PersonOutlineIcon fontSize="small" />
            </Avatar>
            <Typography variant="body2" sx={{ color: 'rgba(255,255,255,.78)' }}>{roles[0] ?? 'User'}</Typography>
          </Stack>
          <Button
            color="inherit"
            aria-label="Sign out"
            startIcon={<LogoutIcon />}
            onClick={() => void logout()}
            sx={{ minWidth: { xs: 40, sm: 'auto' }, px: { xs: 1, sm: 2 }, '& .MuiButton-startIcon': { mr: { xs: 0, sm: 1 } } }}
          >
            <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>Sign out</Box>
          </Button>
          <Tooltip title="Keyboard shortcuts">
            <IconButton color="inherit" aria-label="Keyboard shortcuts" onClick={() => setShortcutsOpen(true)}>
              <KeyboardOutlinedIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" aria-label="Primary navigation" sx={{ width: { lg: drawerWidth }, flexShrink: { lg: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen && !isDesktop}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', lg: 'none' },
            '& .MuiDrawer-paper': { width: { xs: 'calc(100vw - 24px)', sm: drawerWidth }, maxWidth: drawerWidth }
          }}
        >
          <Box ref={mobileNavRef}>
            <SidebarContent onNavigate={() => setMobileOpen(false)} />
          </Box>
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: 'none', lg: 'block' },
            '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box', borderColor: merchtylTokens.colors.border, bgcolor: '#fff' }
          }}
        >
          <Box ref={desktopNavRef}>
            <SidebarContent />
          </Box>
        </Drawer>
      </Box>

      <Box
        id="main-content"
        ref={mainRef}
        component="main"
        tabIndex={-1}
        aria-label="Workspace content"
        sx={{
          flexGrow: 1,
          width: 0,
          minWidth: 0,
          outline: 'none'
        }}
      >
        <Toolbar />
        <Box sx={{ width: '100%', maxWidth: 1920, mx: 'auto', minWidth: 0, p: { xs: 2, lg: 2.5, xl: 3 } }}>
          <MobileManagementRouteGuard><Outlet /></MobileManagementRouteGuard>
        </Box>
      </Box>
      <PwaPrompt />
      <Dialog open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Keyboard shortcuts</DialogTitle>
        <DialogContent>
          <Stack component="dl" spacing={1.5} sx={{ m: 0, pt: 1 }}>
            <Box>
              <Typography component="dt" fontWeight={700}>?</Typography>
              <Typography component="dd" color="text.secondary" sx={{ m: 0 }}>Open this shortcuts dialog</Typography>
            </Box>
            <Box>
              <Typography component="dt" fontWeight={700}>Alt + M</Typography>
              <Typography component="dd" color="text.secondary" sx={{ m: 0 }}>Move focus to navigation</Typography>
            </Box>
            <Box>
              <Typography component="dt" fontWeight={700}>Alt + S</Typography>
              <Typography component="dd" color="text.secondary" sx={{ m: 0 }}>Move focus to page content</Typography>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShortcutsOpen(false)} startIcon={<CloseIcon />}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function AppRoutes() {
  const { session } = useSession();

  return (
    <Routes>
      <Route path="/login" element={session ? <Navigate to="/" replace /> : <AuthPage />} />
      <Route path="/forgot-password" element={session ? <Navigate to="/" replace /> : <ForgotPasswordPage />} />
      <Route path="/reset-password" element={session ? <Navigate to="/" replace /> : <ResetPasswordPage />} />
      <Route path="/first-login/change-password" element={session ? <Navigate to="/" replace /> : <FirstLoginPasswordChangePage />} />
      <Route path="/platform/login" element={session ? <Navigate to="/platform" replace /> : <PlatformLoginPage />} />
      <Route path="/activate-platform-admin" element={<PlatformAdminActivationPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<PosDeviceGuard><PosLayout /></PosDeviceGuard>}>
          <Route path="/pos" element={<PosCartPage />} />
          <Route path="/pos/food" element={<FoodPosPage />} />
          <Route path="/pos/held-sales" element={<HeldSalesPage />} />
        </Route>
        <Route element={<MobilePortalGuard><AppShell /></MobilePortalGuard>}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/store-menu" element={<StoreMenuPage />} />
          <Route path="/food-menu" element={<FoodMenuPage />} />
          <Route path="/discounts" element={<DiscountDefinitionsPage />} />
          <Route path="/platform" element={<PlatformDashboardPage />} />
          <Route path="/platform/merchants" element={<PlatformMerchantsPage />} />
          <Route path="/platform/merchants/new" element={<NewPlatformMerchantPage />} />
          <Route path="/platform/merchants/:tenantId" element={<PlatformMerchantDetailPage />} />
          <Route path="/platform/audit" element={<PlatformAuditPage />} />
          <Route path="/platform/settings" element={<PlatformSettingsPage />} />
          <Route path="/platform/admins" element={<PlatformAdminsPage />} />
          <Route path="/platform/billing" element={<PlatformBillingOverviewPage />} />
          <Route path="/platform/billing/plans" element={<PlatformPricingPlansPage />} />
          <Route path="/platform/billing/subscriptions" element={<PlatformSubscriptionsPage />} />
          <Route path="/platform/billing/invoices" element={<PlatformInvoicesPage />} />
          <Route path="/platform/billing/settings" element={<PlatformBillingSettingsPage />} />
          <Route path="/billing" element={<MerchantBillingPage />} />
          <Route path="/stores" element={<StoresPage />} />
          <Route path="/stores/new" element={<NewStorePage />} />
          <Route path="/stores/:id" element={<StoreDetailPage />} />
          <Route path="/registers" element={<RegistersPage />} />
          <Route path="/registers/new" element={<NewRegisterPage />} />
          <Route path="/registers/:id" element={<RegisterDetailPage />} />
          <Route path="/register/open" element={<RegisterOpenPage />} />
          <Route path="/register/current" element={<RegisterCurrentPage />} />
          <Route path="/register/close" element={<RegisterClosePage />} />
          <Route path="/register/cash-movements" element={<CashMovementPage />} />
          <Route path="/register/history" element={<RegisterHistoryPage />} />
          <Route path="/returns" element={<ReturnsPage />} />
          <Route path="/returns/new" element={<NewReturnPage />} />
          <Route path="/returns/:id" element={<ReturnDetailPage />} />
          <Route path="/lottery/operators" element={<LotteryOperatorsPage />} />
          <Route path="/lottery/sale" element={<LotterySalePage />} />
          <Route path="/lottery/payout" element={<LotteryPayoutPage />} />
          <Route path="/lottery/history" element={<LotteryHistoryPage />} />
          <Route path="/lottery/management" element={<LotteryManagementPage />} />
          <Route path="/lottery/operators/new" element={<NewLotteryOperatorPage />} />
          <Route path="/lottery/operators/:id" element={<LotteryOperatorDetailPage />} />
          <Route path="/lottery/payout-policies" element={<LotteryPayoutPoliciesPage />} />
          <Route path="/lottery/payout-policies/new" element={<NewLotteryPayoutPolicyPage />} />
          <Route path="/lottery/payout-policies/:id" element={<LotteryPayoutPolicyDetailPage />} />
          <Route path="/lottery/commission-rules" element={<LotteryCommissionRulePage />} />
          <Route path="/lottery/settlements" element={<LotterySettlementPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/users/new" element={<NewUserPage />} />
          <Route path="/users/:id" element={<UserDetailPage />} />
          <Route path="/users/:id/edit" element={<UserDetailPage />} />
          <Route path="/users/:id/store-assignments" element={<UserDetailPage />} />
          <Route path="/select-store" element={<StoreSelectionPage />} />
          <Route path="/roles" element={<RolesPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/products/new" element={<NewProductPage />} />
          <Route path="/products/:id" element={<ProductDetailPage />} />
          <Route path="/inventory" element={<InventoryReportingPage mode="current" />} />
          <Route path="/inventory/initial-setup" element={<InitialInventorySetupPage />} />
          <Route path="/inventory/history" element={<InventoryReportingPage mode="history" />} />
          <Route path="/inventory/low-stock" element={<InventoryReportingPage mode="low-stock" />} />
          <Route path="/inventory/negative-stock" element={<InventoryReportingPage mode="negative-stock" />} />
          <Route path="/inventory/adjustment-report" element={<InventoryReportingPage mode="adjustments" />} />
          <Route path="/inventory/damaged" element={<InventoryReportingPage mode="damaged" />} />
          <Route path="/inventory/expired" element={<InventoryReportingPage mode="expired" />} />
          <Route path="/inventory/adjustments" element={<InventoryAdjustmentsPage />} />
          <Route path="/inventory/adjustments/new" element={<NewInventoryAdjustmentPage />} />
          <Route path="/inventory/counts" element={<StockCountsPage />} />
          <Route path="/inventory/counts/new" element={<NewStockCountPage />} />
          <Route path="/inventory/counts/:id" element={<StockCountDetailPage />} />
          <Route path="/reports/sales" element={<SalesReportsPage />} />
          <Route path="/reports/registers" element={<RegisterReportsPage />} />
          <Route path="/reports/lottery" element={<LotteryReportsPage />} />
          <Route path="/business-day" element={<BusinessDayPage />} />
          <Route path="/business-day/close" element={<BusinessDayClosePage />} />
          <Route path="/business-day/history" element={<BusinessDayHistoryPage />} />
          <Route path="/end-of-day-reports" element={<EndOfDayReportsPage />} />
          <Route path="/end-of-day-reports/:id" element={<EndOfDayReportDetailPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/brands" element={<BrandsPage />} />
          <Route path="/suppliers" element={<SuppliersPage />} />
          <Route path="/suppliers/new" element={<NewSupplierPage />} />
          <Route path="/suppliers/:id" element={<SupplierDetailPage />} />
          <Route path="/settings/units" element={<UnitsPage />} />
          <Route path="/settings/features" element={<FeatureSettingsPage />} />
          <Route path="/settings/taxes/test" element={<TaxSimulatorPage />} />
          <Route path="/settings/hardware/printers" element={<PrinterSettingsPage />} />
          <Route path="/settings/hardware/scanner-test" element={<ScannerTestPage />} />
          <Route path="/tax" element={<TaxGeographyRedirect />} />
          <Route path="/tax/rules" element={<TaxRulesPage />} />
          <Route path="/tax/categories" element={<TaxCategoriesPage />} />
          <Route path="/tax/groups" element={<TaxGroupsPage />} />
          <Route path="/tax/group-components" element={<TaxGroupComponentsPage />} />
          <Route path="/tax/product-category-assignments" element={<ProductTaxCategoryAssignmentsPage />} />
          <Route path="/tax/types" element={<TaxTypesPage />} />
          <Route path="/tax/components" element={<TaxComponentsPage />} />
          <Route path="/tax/rates" element={<TaxRatesPage />} />
          <Route path="/tax/countries" element={<CountriesPage />} />
          <Route path="/tax/administrative-areas" element={<AdministrativeAreasPage />} />
          <Route path="/tax/jurisdictions" element={<TaxJurisdictionsPage />} />
          <Route path="/unauthorized" element={<UnauthorizedPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export function App({ initialEntries, hostname }: AppProps = {}) {
  const Router = initialEntries ? MemoryRouter : BrowserRouter;
  const routerProps = initialEntries ? { initialEntries } : {};
  const [queryClient] = useState(() => new QueryClient(queryClientOptions));

  useEffect(() => {
    getApplicationDeviceIdentifier();
  }, []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <Router {...routerProps}>
            <MerchantPortalProvider hostname={hostname}>
              <PortalBoundary><AppRoutes /></PortalBoundary>
            </MerchantPortalProvider>
          </Router>
        </SessionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
