import DownloadIcon from '@mui/icons-material/Download';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import HistoryIcon from '@mui/icons-material/History';
import InventoryIcon from '@mui/icons-material/Inventory2Outlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import ReportIcon from '@mui/icons-material/AssessmentOutlined';
import WarningIcon from '@mui/icons-material/WarningAmberOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Link, Navigate } from 'react-router-dom';
import {
  catalogueReferenceApi,
  getInventoryReport,
  listProducts,
  listStores,
  type InventoryReportParams
} from '../../api/client';
import type {
  CatalogueReference,
  InventoryActivityReportRow,
  InventoryReport,
  InventoryStockReportRow,
  Product,
  Store,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

type InventoryReportMode = 'current' | 'history' | 'low-stock' | 'negative-stock' | 'adjustments' | 'damaged' | 'expired';

type InventoryReportPageProps = {
  mode: InventoryReportMode;
};

type FilterState = {
  storeId: string;
  categoryId: string;
  productId: string;
  dateFrom: string;
  dateTo: string;
  lowStockThreshold: number;
};

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;

const defaultFilters: FilterState = {
  storeId: '',
  categoryId: '',
  productId: '',
  dateFrom: monthStart,
  dateTo: today,
  lowStockThreshold: 5
};

function canViewInventoryReports(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function useInventoryReportPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewInventoryReports(roles)
  };
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Inventory report request failed';
}

function formatQuantity(value: number) {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4
  }).format(Number(value ?? 0));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD'
  }).format(Number(value ?? 0));
}

function formatDate(value: string | null) {
  if (!value) {
    return 'No transactions';
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function categoryLabel(category?: CatalogueReference) {
  return category ? `${category.name} (${category.code})` : 'Uncategorized';
}

function productLabel(product?: Product) {
  return product ? `${product.name} (${product.sku})` : 'Unknown product';
}

function csvEscape(value: string | number | null | undefined) {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadCsv(filename: string, rows: string[][]) {
  const csv = rows.map((row) => row.map(csvEscape).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function cleanParams(filters: FilterState): InventoryReportParams {
  return {
    storeId: filters.storeId || undefined,
    categoryId: filters.categoryId || undefined,
    productId: filters.productId || undefined,
    dateFrom: filters.dateFrom || undefined,
    dateTo: filters.dateTo || undefined,
    lowStockThreshold: filters.lowStockThreshold
  };
}

function pageTitle(mode: InventoryReportMode) {
  switch (mode) {
    case 'history':
      return 'Inventory history';
    case 'low-stock':
      return 'Low stock';
    case 'negative-stock':
      return 'Negative stock';
    case 'adjustments':
      return 'Inventory adjustments report';
    case 'damaged':
      return 'Damaged inventory';
    case 'expired':
      return 'Expired inventory';
    default:
      return 'Inventory';
  }
}

function pageSubtitle(mode: InventoryReportMode) {
  switch (mode) {
    case 'history':
      return 'Adjustment, damaged, and expired movement activity';
    case 'low-stock':
      return 'Products at or below the reporting reorder threshold';
    case 'negative-stock':
      return 'Products with quantity below zero';
    case 'adjustments':
      return 'Manual inventory adjustment quantity and value';
    case 'damaged':
      return 'Damaged stock written out of inventory';
    case 'expired':
      return 'Expired stock written out of inventory';
    default:
      return 'Current quantity and valuation by product';
  }
}

function LoadingPanel({ label: loadingLabel }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={loadingLabel} />
      <Typography color="text.secondary">{loadingLabel}</Typography>
    </Stack>
  );
}

function ReportNavigation({ mode }: { mode: InventoryReportMode }) {
  const items = [
    { mode: 'current' as const, label: 'Current', to: '/inventory', icon: <InventoryIcon /> },
    { mode: 'history' as const, label: 'History', to: '/inventory/history', icon: <HistoryIcon /> },
    { mode: 'low-stock' as const, label: 'Low stock', to: '/inventory/low-stock', icon: <ReportIcon /> },
    { mode: 'negative-stock' as const, label: 'Negative stock', to: '/inventory/negative-stock', icon: <WarningIcon /> },
    { mode: 'adjustments' as const, label: 'Adjustments', to: '/inventory/adjustment-report', icon: <ReportIcon /> },
    { mode: 'damaged' as const, label: 'Damaged', to: '/inventory/damaged', icon: <ErrorOutlineIcon /> },
    { mode: 'expired' as const, label: 'Expired', to: '/inventory/expired', icon: <WarningIcon /> }
  ];

  return (
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      {items.map((item) => (
        <Button
          key={item.mode}
          component={Link}
          to={item.to}
          variant={mode === item.mode ? 'contained' : 'outlined'}
          startIcon={item.icon}
        >
          {item.label}
        </Button>
      ))}
    </Stack>
  );
}

function InventoryFilters({
  stores,
  categories,
  products,
  filters,
  onChange
}: {
  stores: Store[];
  categories: CatalogueReference[];
  products: Product[];
  filters: FilterState;
  onChange: (next: FilterState) => void;
}) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Store"
            value={filters.storeId}
            onChange={(event) => onChange({ ...filters, storeId: event.target.value })}
            fullWidth
          >
            <MenuItem value="">All stores</MenuItem>
            {stores.map((store) => (
              <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Category"
            value={filters.categoryId}
            onChange={(event) => onChange({ ...filters, categoryId: event.target.value, productId: '' })}
            fullWidth
          >
            <MenuItem value="">All categories</MenuItem>
            {categories.map((category) => (
              <MenuItem key={category.id} value={category.id}>{categoryLabel(category)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Product"
            value={filters.productId}
            onChange={(event) => onChange({ ...filters, productId: event.target.value })}
            fullWidth
          >
            <MenuItem value="">All products</MenuItem>
            {products.map((product) => (
              <MenuItem key={product.id} value={product.id}>{productLabel(product)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Date from"
            type="date"
            value={filters.dateFrom}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => onChange({ ...filters, dateFrom: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Date to"
            type="date"
            value={filters.dateTo}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => onChange({ ...filters, dateTo: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Low stock threshold"
            type="number"
            value={filters.lowStockThreshold}
            inputProps={{ min: 0, step: 1 }}
            onChange={(event) => onChange({
              ...filters,
              lowStockThreshold: Math.max(0, Number(event.target.value) || 0)
            })}
            fullWidth
          />
        </Grid>
      </Grid>
    </Paper>
  );
}

function MetricCard({ title, value, detail }: { title: string; value: string; detail?: string }) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{title}</Typography>
        <Typography variant="h5" component="p" sx={{ mt: 0.75 }}>{value}</Typography>
        {detail ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>{detail}</Typography> : null}
      </CardContent>
    </Card>
  );
}

function SummaryCards({ report }: { report: InventoryReport }) {
  const metrics = [
    { title: 'Current stock', value: formatQuantity(report.currentStock), detail: `${report.stockItemCount} stocked items` },
    { title: 'Inventory value', value: formatMoney(report.inventoryValue), detail: 'At current product cost' },
    { title: 'Low stock', value: String(report.lowStockCount), detail: `Threshold ${formatQuantity(report.lowStockThreshold)}` },
    { title: 'Negative stock', value: String(report.negativeStockCount), detail: 'Below zero quantity' },
    { title: 'Adjustments', value: formatQuantity(report.adjustmentQuantity), detail: formatMoney(report.adjustmentValue) },
    { title: 'Damaged', value: formatQuantity(report.damagedQuantity), detail: formatMoney(report.damagedValue) },
    { title: 'Expired', value: formatQuantity(report.expiredQuantity), detail: formatMoney(report.expiredValue) }
  ];

  return (
    <Grid container spacing={2}>
      {metrics.map((metric) => (
        <Grid item xs={12} sm={6} lg={3} key={metric.title}>
          <MetricCard title={metric.title} value={metric.value} detail={metric.detail} />
        </Grid>
      ))}
    </Grid>
  );
}

function InventoryTable({ rows, emptyLabel }: { rows: InventoryStockReportRow[]; emptyLabel: string }) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <TableContainer>
        <Table aria-label="Inventory report">
          <TableHead>
            <TableRow>
              <TableCell>Product</TableCell>
              <TableCell>Store</TableCell>
              <TableCell align="right">Current quantity</TableCell>
              <TableCell align="right">Cost</TableCell>
              <TableCell align="right">Inventory value</TableCell>
              <TableCell>Last movement</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={`${row.storeId}:${row.productId}`}>
                <TableCell>
                  <Typography fontWeight={600}>{row.productName}</Typography>
                  <Typography variant="body2" color="text.secondary">{row.productSku}</Typography>
                </TableCell>
                <TableCell>{row.storeName} ({row.storeCode})</TableCell>
                <TableCell align="right">
                  <Typography color={row.quantityOnHand < 0 ? 'error.main' : 'text.primary'} fontWeight={600}>
                    {formatQuantity(row.quantityOnHand)}
                  </Typography>
                </TableCell>
                <TableCell align="right">{formatMoney(row.cost)}</TableCell>
                <TableCell align="right">{formatMoney(row.inventoryValue)}</TableCell>
                <TableCell>{formatDate(row.lastTransactionAt)}</TableCell>
              </TableRow>
            ))}
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography color="text.secondary">{emptyLabel}</Typography>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}

function ActivityTable({ rows, emptyLabel }: { rows: InventoryActivityReportRow[]; emptyLabel: string }) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <TableContainer>
        <Table aria-label="Inventory activity report">
          <TableHead>
            <TableRow>
              <TableCell>Occurred</TableCell>
              <TableCell>Product</TableCell>
              <TableCell>Store</TableCell>
              <TableCell>Type</TableCell>
              <TableCell align="right">Quantity delta</TableCell>
              <TableCell align="right">Quantity</TableCell>
              <TableCell align="right">Value</TableCell>
              <TableCell>Reference</TableCell>
              <TableCell>Reason</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{formatDate(row.occurredAt)}</TableCell>
                <TableCell>
                  <Typography fontWeight={600}>{row.productName}</Typography>
                  <Typography variant="body2" color="text.secondary">{row.productSku}</Typography>
                </TableCell>
                <TableCell>{row.storeName} ({row.storeCode})</TableCell>
                <TableCell><Chip label={label(row.transactionType)} size="small" /></TableCell>
                <TableCell align="right">
                  <Typography color={row.quantityDelta < 0 ? 'error.main' : 'success.main'} fontWeight={600}>
                    {row.quantityDelta > 0 ? '+' : ''}{formatQuantity(row.quantityDelta)}
                  </Typography>
                </TableCell>
                <TableCell align="right">{formatQuantity(row.quantity)}</TableCell>
                <TableCell align="right">{formatMoney(row.inventoryValue)}</TableCell>
                <TableCell>{row.referenceType ?? 'None'}</TableCell>
                <TableCell>{row.reason ?? 'None'}</TableCell>
              </TableRow>
            ))}
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9}>
                  <Typography color="text.secondary">{emptyLabel}</Typography>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}

function stockRowsForMode(report: InventoryReport, mode: InventoryReportMode) {
  if (mode === 'low-stock') {
    return report.lowStockRows;
  }
  if (mode === 'negative-stock') {
    return report.negativeStockRows;
  }
  return report.stockRows;
}

function activityRowsForMode(report: InventoryReport, mode: InventoryReportMode) {
  if (mode === 'adjustments') {
    return report.adjustmentRows;
  }
  if (mode === 'damaged') {
    return report.damagedRows;
  }
  if (mode === 'expired') {
    return report.expiredRows;
  }
  return [...report.adjustmentRows, ...report.damagedRows, ...report.expiredRows]
    .sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime());
}

function exportStockRows(mode: InventoryReportMode, rows: InventoryStockReportRow[]) {
  downloadCsv(`${mode === 'current' ? 'inventory' : mode}.csv`, [
    ['Product', 'SKU', 'Store', 'Current Quantity', 'Cost', 'Inventory Value', 'Last Movement'],
    ...rows.map((row) => [
      row.productName,
      row.productSku,
      `${row.storeName} (${row.storeCode})`,
      String(row.quantityOnHand),
      row.cost.toFixed(2),
      row.inventoryValue.toFixed(2),
      row.lastTransactionAt ?? ''
    ])
  ]);
}

function exportActivityRows(mode: InventoryReportMode, rows: InventoryActivityReportRow[]) {
  downloadCsv(`inventory-${mode}.csv`, [
    ['Occurred', 'Product', 'SKU', 'Store', 'Type', 'Quantity Delta', 'Quantity', 'Value', 'Reference', 'Reason'],
    ...rows.map((row) => [
      row.occurredAt,
      row.productName,
      row.productSku,
      `${row.storeName} (${row.storeCode})`,
      row.transactionType,
      String(row.quantityDelta),
      String(row.quantity),
      row.inventoryValue.toFixed(2),
      row.referenceType ?? '',
      row.reason ?? ''
    ])
  ]);
}

export function InventoryReportingPage({ mode }: InventoryReportPageProps) {
  const { getValidAccessToken } = useSession();
  const { canView } = useInventoryReportPermissions();
  const [filters, setFilters] = React.useState(defaultFilters);
  const reportParams = React.useMemo(() => cleanParams(filters), [filters]);

  const stores = useQuery({
    queryKey: ['stores', 'inventory-reporting'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canView
  });
  const categories = useQuery({
    queryKey: ['categories', 'inventory-reporting'],
    queryFn: async () => catalogueReferenceApi.categories.list(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: canView
  });
  const products = useQuery({
    queryKey: ['products', 'inventory-reporting', filters.categoryId],
    queryFn: async () => listProducts(await getValidAccessToken(), {
      page: 0,
      size: 100,
      active: true,
      categoryId: filters.categoryId || undefined
    }),
    enabled: canView
  });
  const report = useQuery({
    queryKey: ['inventory-report', reportParams],
    queryFn: async () => getInventoryReport(await getValidAccessToken(), reportParams),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loading = stores.isLoading || categories.isLoading || products.isLoading || report.isLoading;
  const error = stores.error ?? categories.error ?? products.error ?? report.error;
  const allStores = stores.data?.content ?? [];
  const allCategories = categories.data?.content ?? [];
  const allProducts = products.data?.content ?? [];
  const reportData = report.data;
  const isActivityMode = mode === 'history' || mode === 'adjustments' || mode === 'damaged' || mode === 'expired';
  const visibleStockRows = reportData ? stockRowsForMode(reportData, mode) : [];
  const visibleActivityRows = reportData ? activityRowsForMode(reportData, mode) : [];

  const exportRows = () => {
    if (isActivityMode) {
      exportActivityRows(mode, visibleActivityRows);
      return;
    }
    exportStockRows(mode, visibleStockRows);
  };

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems={{ xs: 'stretch', lg: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">{pageTitle(mode)}</Typography>
          <Typography color="text.secondary">{pageSubtitle(mode)}</Typography>
        </Box>
        <Tooltip title="Refresh inventory reports">
          <IconButton
            aria-label="Refresh inventory reports"
            onClick={() => {
              void stores.refetch();
              void categories.refetch();
              void products.refetch();
              void report.refetch();
            }}
          >
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Button variant="outlined" startIcon={<DownloadIcon />} onClick={exportRows} disabled={!reportData}>
          Export CSV
        </Button>
      </Stack>

      <ReportNavigation mode={mode} />

      <InventoryFilters
        stores={allStores}
        categories={allCategories}
        products={allProducts}
        filters={filters}
        onChange={setFilters}
      />

      {loading ? <LoadingPanel label="Loading inventory report" /> : null}
      {error ? <Alert severity="error">{errorMessage(error)}</Alert> : null}

      {!loading && !error && reportData ? <SummaryCards report={reportData} /> : null}

      {!loading && !error && reportData && isActivityMode ? (
        <ActivityTable
          rows={visibleActivityRows}
          emptyLabel={`No ${pageTitle(mode).toLowerCase()} rows found.`}
        />
      ) : null}

      {!loading && !error && reportData && !isActivityMode ? (
        <InventoryTable
          rows={visibleStockRows}
          emptyLabel={mode === 'current' ? 'No inventory balances found.' : `No ${pageTitle(mode).toLowerCase()} items found.`}
        />
      ) : null}
    </Stack>
  );
}
