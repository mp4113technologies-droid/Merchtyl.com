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
import {
  Alert,
  AppBar,
  Avatar,
  Box,
  Button,
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
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
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
import { theme } from './theme';
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
import { NewStockCountPage, StockCountDetailPage, StockCountsPage } from '../features/inventory/StockCountPages';
import { HeldSalesPage, PosCartPage } from '../features/pos/PosPages';
import { FoodPosPage } from '../features/pos/FoodPosPage';
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
import { getCurrentRegisterSession, listRegisters, listStores } from '../api/client';
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
};

function LoadingState() {
  return (
    <Stack minHeight="100vh" alignItems="center" justifyContent="center" spacing={2} role="status" aria-live="polite">
      <CircularProgress aria-label="Loading application" />
      <Typography color="text.secondary">Loading workspace</Typography>
    </Stack>
  );
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
  return (
    <Stack spacing={2} sx={{ maxWidth: 680 }}>
      <ShieldOutlinedIcon color="secondary" sx={{ fontSize: 42 }} />
      <Typography variant="h5" component="h1">Unauthorized</Typography>
      <Typography color="text.secondary">
        This account does not have access to the requested area.
      </Typography>
      <Button component={Link} to="/" variant="contained" sx={{ alignSelf: 'flex-start' }}>
        Return to workspace
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
  const browserDeviceIdentifier = getApplicationDeviceIdentifier();
  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    refetchInterval: 15_000
  });

  return (
    <Box sx={{ width: '100vw', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box component="header" sx={{ px: { xs: 1.5, sm: 2 }, py: 1, bgcolor: 'primary.dark', color: 'primary.contrastText', display: 'flex', gap: 2, alignItems: 'center' }}>
        <Button component={Link} to="/store-menu" color="inherit" startIcon={<ArrowBackIcon />}>
          Back to Store Menu
        </Button>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>Merchtyl POS</Typography>
        <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
          <Typography variant="body2" fontWeight={700}>{current.data?.status === 'OPEN' ? 'Register OPEN' : 'No active register'}</Typography>
          <Typography variant="caption">{currentUser?.displayName ?? session?.displayName}</Typography>
        </Box>
      </Box>
      <Box component="main" sx={{ p: { xs: 1, md: 2 }, minHeight: 'calc(100vh - 56px)' }}>
        <Outlet />
      </Box>
    </Box>
  );
}

function StoreMenuPage() {
  const { currentUser, session, getValidAccessToken, logout } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const permissions = currentUser?.permissions ?? [];
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
  const store = stores.data?.content.find((item) => item.id === activeSession?.storeId);
  const register = registers.data?.content.find((item) => item.id === activeSession?.registerId);
  const isCashier = roles.includes('CASHIER');
  const operations = [
    { label: 'Retail POS', to: '/pos', visible: permissions.includes('POS_ACCESS') },
    { label: 'Food Menu', to: '/food-menu', visible: permissions.includes('FOOD_POS_ACCESS') },
    { label: 'Orders', to: '/sales', visible: permissions.includes('FOOD_ORDER_VIEW') },
    { label: 'Restaurant / Kitchen POS', to: '/pos/food', visible: permissions.includes('FOOD_POS_ACCESS') },
    { label: 'Inventory / Product Lookup', to: '/inventory', visible: true },
    { label: 'Returns', to: '/returns', visible: true },
    { label: 'Current Register', to: '/register/current', visible: true },
    { label: 'Cash Operations', to: '/register/cash-movements', visible: true },
    { label: 'Dashboard', to: '/', visible: !isCashier }
  ].filter((item) => item.visible);

  return (
    <Stack spacing={3} sx={{ maxWidth: 900 }}>
      <Box>
        <Typography variant="h4" component="h1">Store Menu</Typography>
        <Typography color="text.secondary">Store operations available to this account.</Typography>
      </Box>
      <Paper variant="outlined" sx={{ p: 3 }}>
        {activeSession ? (
          <Stack spacing={1.5}>
            <Typography variant="overline" color="text.secondary">Active register session</Typography>
            <Typography variant="h6">{register ? `${register.name} (${register.code})` : activeSession.registerId}</Typography>
            <Typography>{store ? `${store.name} (${store.code})` : activeSession.storeId} • OPEN</Typography>
            <Typography variant="body2" color="text.secondary">Opened {new Date(activeSession.openedAt).toLocaleString()}</Typography>
            <Button component={Link} to="/pos" variant="contained" startIcon={<PointOfSaleOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}>
              Return to POS
            </Button>
          </Stack>
        ) : (
          <Stack spacing={1.5}>
            <Typography variant="h6">No active register</Typography>
            <Button component={Link} to="/register/open" variant="contained" sx={{ alignSelf: 'flex-start' }}>Open / Select Register</Button>
          </Stack>
        )}
      </Paper>
      <Box>
        <Typography variant="h6" sx={{ mb: 2 }}>Store Operations</Typography>
        <Grid container spacing={2}>
          {operations.map((item) => (
            <Grid item xs={12} sm={6} key={item.to}>
              <Button component={Link} to={item.to} variant="outlined" fullWidth sx={{ minHeight: 64 }}>{item.label}</Button>
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
  const navItems = [
    ...(canViewPlatform ? [{ label: 'Platform', to: '/platform', icon: <ShieldOutlinedIcon /> }] : []),
    ...(canViewPlatform ? [{ label: 'Merchants', to: '/platform/merchants', icon: <StorefrontIcon /> }] : []),
    ...(canViewPlatform ? [{ label: 'Billing', to: '/platform/billing', icon: <PaymentsOutlinedIcon /> }] : []),
    ...(canViewPlatform ? [{ label: 'Platform audit', to: '/platform/audit', icon: <HistoryOutlinedIcon /> }] : []),
    ...(roles.includes('PLATFORM_SUPER_ADMIN') || roles.includes('PLATFORM_SUPPORT_ADMIN') ? [{ label: 'Platform Administrators', to: '/platform/admins', icon: <ManageAccountsOutlinedIcon /> }] : []),
    ...(canViewPlatform ? [{ label: 'Platform settings', to: '/platform/settings', icon: <SettingsInputComponentOutlinedIcon /> }] : []),
    ...(!canViewPlatform ? [{ label: 'Dashboard', to: '/', icon: <DashboardOutlinedIcon /> }] : []),
    ...(canViewMerchantBilling ? [{ label: 'Subscription & Billing', to: '/billing', icon: <PaymentsOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'POS', to: '/pos', icon: <PointOfSaleOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Held sales', to: '/pos/held-sales', icon: <PauseCircleOutlineIcon /> }] : []),
    ...(canViewStores ? [{ label: 'Stores', to: '/stores', icon: <StoreMallDirectoryOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Registers', to: '/registers', icon: <PointOfSaleOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Current register', to: '/register/current', icon: <LockOpenOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Cash movements', to: '/register/cash-movements', icon: <PaymentsOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Register history', to: '/register/history', icon: <ReceiptLongOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Returns', to: '/returns', icon: <ReceiptLongOutlinedIcon /> }] : []),
    ...(canViewCatalogue ? [{ label: 'Products', to: '/products', icon: <Inventory2OutlinedIcon /> }] : []),
    ...(canViewInventory ? [{ label: 'Inventory', to: '/inventory', icon: <AssignmentTurnedInOutlinedIcon /> }] : []),
    ...(canViewInventory ? [{ label: 'Stock counts', to: '/inventory/counts', icon: <AssignmentTurnedInOutlinedIcon /> }] : []),
    ...(canViewInventory ? [{ label: 'Adjustments', to: '/inventory/adjustments', icon: <AssignmentTurnedInOutlinedIcon /> }] : []),
    ...(canViewReports ? [{ label: 'Sales reports', to: '/reports/sales', icon: <AssessmentOutlinedIcon /> }] : []),
    ...(canViewReports ? [{ label: 'Register reports', to: '/reports/registers', icon: <AssessmentOutlinedIcon /> }] : []),
    ...(canViewReports ? [{ label: 'Lottery reports', to: '/reports/lottery', icon: <AssessmentOutlinedIcon /> }] : []),
    ...(canViewReports ? [{ label: 'Business day', to: '/business-day', icon: <EventAvailableOutlinedIcon /> }] : []),
    ...(canViewReports ? [{ label: 'EOD reports', to: '/end-of-day-reports', icon: <AssessmentOutlinedIcon /> }] : []),
    ...(canViewCatalogue ? [{ label: 'Categories', to: '/categories', icon: <CategoryOutlinedIcon /> }] : []),
    ...(canViewCatalogue ? [{ label: 'Brands', to: '/brands', icon: <BrandingWatermarkOutlinedIcon /> }] : []),
    ...(canViewCatalogue ? [{ label: 'Suppliers', to: '/suppliers', icon: <LocalShippingOutlinedIcon /> }] : []),
    ...(canRecordLottery ? [{ label: 'Lottery sale', to: '/lottery/sale', icon: <ConfirmationNumberOutlinedIcon /> }] : []),
    ...(canRecordLottery ? [{ label: 'Lottery payout', to: '/lottery/payout', icon: <PaymentsOutlinedIcon /> }] : []),
    ...(canRecordLottery ? [{ label: 'Lottery history', to: '/lottery/history', icon: <HistoryOutlinedIcon /> }] : []),
    ...(canRecordLottery ? [{ label: 'Lottery management', to: '/lottery/management', icon: <HistoryOutlinedIcon /> }] : []),
    ...(canViewLottery ? [{ label: 'Lottery operators', to: '/lottery/operators', icon: <ConfirmationNumberOutlinedIcon /> }] : []),
    ...(canViewLottery ? [{ label: 'Payout policies', to: '/lottery/payout-policies', icon: <PaymentsOutlinedIcon /> }] : []),
    ...(canViewLottery ? [{ label: 'Commission rules', to: '/lottery/commission-rules', icon: <CalculateOutlinedIcon /> }] : []),
    ...(canViewLottery ? [{ label: 'Settlements', to: '/lottery/settlements', icon: <CalculateOutlinedIcon /> }] : []),
    ...(canViewCatalogue ? [{ label: 'Units', to: '/settings/units', icon: <StraightenOutlinedIcon /> }] : []),
    ...(canViewTax ? [{ label: 'Tax', to: '/tax/rules', icon: <PublicOutlinedIcon /> }] : []),
    ...(canViewTax ? [{ label: 'Tax test', to: '/settings/taxes/test', icon: <CalculateOutlinedIcon /> }] : []),
    ...(canViewFeatures ? [{ label: 'Features', to: '/settings/features', icon: <ToggleOnOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Printers', to: '/settings/hardware/printers', icon: <SettingsInputComponentOutlinedIcon /> }] : []),
    ...(canViewRegisters ? [{ label: 'Scanner test', to: '/settings/hardware/scanner-test', icon: <QrCodeScannerOutlinedIcon /> }] : []),
    ...(canViewUsers ? [{ label: 'Users', to: '/users', icon: <ManageAccountsOutlinedIcon /> }] : []),
    ...(canViewUsers ? [{ label: 'Roles', to: '/roles', icon: <BadgeOutlinedIcon /> }] : []),
    { label: 'Unauthorized', to: '/unauthorized', icon: <ShieldOutlinedIcon /> }
  ];

  return (
    <Stack sx={{ height: '100%' }}>
      <Toolbar sx={{ gap: 1.5 }}>
        <StorefrontIcon color="primary" />
        <Typography variant="h6" component="div">Merchtyl</Typography>
      </Toolbar>
      <Divider />
      <List component="nav" sx={{ px: 1, py: 2 }}>
        {navItems.map((item) => (
          <ListItemButton
            key={item.to}
            component={Link}
            to={item.to}
            selected={item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)}
            onClick={onNavigate}
            sx={{ borderRadius: 1 }}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
      {activeRegister.data?.status === 'OPEN' ? (
        <Box sx={{ mt: 'auto', p: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Typography variant="overline" color="text.secondary">Active register</Typography>
          <Typography variant="body2" fontWeight={700} noWrap>
            Register {activeRegister.data.registerId.slice(0, 8)} • OPEN
          </Typography>
          <Button component={Link} to="/pos" size="small" variant="contained" fullWidth sx={{ mt: 1 }} onClick={onNavigate}>
            Return to POS
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
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
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
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
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
        color="inherit"
        elevation={0}
        sx={{
          borderBottom: '1px solid',
          borderColor: 'divider',
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` }
        }}
      >
        <Toolbar sx={{ gap: 2 }}>
          <IconButton
            color="inherit"
            edge="start"
            aria-label="Open navigation"
            onClick={() => setMobileOpen(true)}
            sx={{ display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ flexGrow: 1, minWidth: 0 }}>
            <Typography variant="subtitle1" component="div" noWrap>{displayName}</Typography>
            <Typography variant="body2" color="text.secondary" noWrap>{email}</Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ display: { xs: 'none', sm: 'flex' } }}>
            <Avatar sx={{ width: 34, height: 34 }}>
              <PersonOutlineIcon fontSize="small" />
            </Avatar>
            <Typography variant="body2" color="text.secondary">{roles[0] ?? 'User'}</Typography>
          </Stack>
          <Button color="inherit" startIcon={<LogoutIcon />} onClick={() => void logout()}>
            Sign out
          </Button>
          <Tooltip title="Keyboard shortcuts">
            <IconButton color="inherit" aria-label="Keyboard shortcuts" onClick={() => setShortcutsOpen(true)}>
              <KeyboardOutlinedIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" aria-label="Primary navigation" sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen && !isDesktop}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { width: drawerWidth }
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
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box' }
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
          width: { md: `calc(100% - ${drawerWidth}px)` },
          outline: 'none'
        }}
      >
        <Toolbar />
        <Box sx={{ p: { xs: 2, sm: 3, lg: 4 } }}>
          <Outlet />
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
        <Route element={<PosLayout />}>
          <Route path="/pos" element={<PosCartPage />} />
          <Route path="/pos/food" element={<FoodPosPage />} />
          <Route path="/pos/held-sales" element={<HeldSalesPage />} />
        </Route>
        <Route element={<AppShell />}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/store-menu" element={<StoreMenuPage />} />
          <Route path="/food-menu" element={<FoodMenuPage />} />
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

export function App({ initialEntries }: AppProps = {}) {
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
            <AppRoutes />
          </Router>
        </SessionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
