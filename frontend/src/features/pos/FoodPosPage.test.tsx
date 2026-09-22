import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { Sale } from '../../api/types';
import { testReceiptDocument } from './receiptPrinter';
import * as receiptPrinter from './receiptPrinter';

const storeId = '00000000-0000-0000-0000-000000000901';
const sessionId = '00000000-0000-0000-0000-000000000902';
const saleId = '00000000-0000-0000-0000-000000000903';
const itemId = '00000000-0000-0000-0000-000000000904';
const productId = '00000000-0000-0000-0000-000000000905';

function response(body: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })); }
function page(content: unknown[]) { return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, first: true, last: true }; }
async function openCustomDiscount() {
  await userEvent.click(screen.getByRole('combobox', { name: 'Discount' }));
  await userEvent.click(await screen.findByRole('option', { name: 'Custom Discount' }));
}
function sale(quantity = 0, paid = false, completed = false) {
  const subtotal = quantity * 12;
  const tax = quantity * 1.8;
  return { id: saleId, storeId, registerId: 'register', registerSessionId: sessionId, createdBy: 'user', customerId: null, status: completed ? 'COMPLETED' : 'DRAFT', businessDate: '2026-08-28', saleChannel: 'POS', currencyCode: 'CAD', pricesIncludeTax: false, subtotalAmount: subtotal, discountAmount: 0, estimatedTaxAmount: tax, totalAmount: subtotal + tax, foodOrderToken: completed ? 'A008' : null, heldAt: null, cancelledAt: null, completedBy: completed ? 'user' : null, completedAt: completed ? '2026-08-28T12:00:00Z' : null, items: quantity ? [{ id: itemId, productId, lineNumber: 1, productSku: 'PIZZA', productName: 'Pepperoni Pizza', quantity, unitPrice: 12, discountAmount: 0, completedProductCost: null, completedProductPrice: null, completedProductCapabilities: null, priceOverride: false, ageVerified: false, serialNumber: null, externalReference: null, customerId: null, paymentMethodCode: null, lineSubtotal: subtotal, estimatedTaxAmount: tax, lineTotal: subtotal + tax, version: 0 }] : [], payments: paid ? [{ id: 'payment', method: 'CASH', amount: subtotal + tax, currencyCode: 'CAD', cashTendered: subtotal + tax, changeDue: 0, reference: null, notes: null, createdBy: 'user', completedAt: '2026-08-28T12:00:00Z', createdAt: '2026-08-28T12:00:00Z', version: 0 }] : [], paidAmount: paid ? subtotal + tax : 0, balanceDue: paid ? 0 : subtotal + tax, changeDue: 0, paymentComplete: paid, createdAt: '2026-08-28T12:00:00Z', updatedAt: '2026-08-28T12:00:00Z', version: 0 };
}
function discountedSale(quantity = 2, paid = false, completed = false) {
  const base = sale(quantity, paid, completed);
  const discountAmount = 2.4;
  const estimatedTaxAmount = 3.24;
  const totalAmount = 24.84;
  return { ...base, discountAmount, estimatedTaxAmount, totalAmount, paidAmount: paid ? totalAmount : 0, balanceDue: paid ? 0 : totalAmount, paymentComplete: paid,
    payments: paid ? [{ ...base.payments[0], amount: totalAmount, cashTendered: totalAmount }] : [],
    items: base.items.map(item => ({ ...item, discountAmount, estimatedTaxAmount, lineTotal: totalAmount })) };
}

describe('Food POS', () => {
  beforeEach(() => {
    const now = Date.now();
    window.localStorage.removeItem(`merchtyl.food-pos-state:${sessionId}`);
    window.localStorage.setItem('merchtyl.session', JSON.stringify({ accessToken: 'token', refreshToken: 'refresh', tokenType: 'Bearer', accessTokenExpiresAt: new Date(now + 900_000).toISOString(), refreshTokenExpiresAt: new Date(now + 86_400_000).toISOString(), userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'] }));
    vi.restoreAllMocks();
  });

  it('redirects an open Food Service register away from the Retail POS route', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, code: 'MAIN', name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories') || url.pathname.endsWith('/food-menu/items') || url.pathname.endsWith(`/stores/${storeId}/discounts`)) return response([]);
      if (url.pathname.endsWith('/registers') || url.pathname.endsWith('/devices') || url.pathname.endsWith('/products')) return response(page([]));
      return response({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByText("Joe's Kitchen")).toBeInTheDocument();
    expect(screen.getByTestId('pos-viewport')).toHaveStyle({ height: '100dvh', overflow: 'hidden' });
    expect(screen.getByTestId('restaurant-pos-shell')).toHaveStyle({ height: '100%', overflow: 'hidden' });
  });

  it('loads tiles and completes a taxed sale through shared checkout', async () => {
    const calls: string[] = [];
    let checkoutBody: unknown;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin); calls.push(`${init?.method ?? 'GET'} ${url.pathname}`);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS', 'POS_SALE_DISCOUNT'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, code: 'MAIN', name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', storeId, name: 'Pizza', displayOrder: 1, active: true, imageUrl: null, version: 0 }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', storeId, productName: 'Pepperoni Pizza', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', categoryName: 'Pizza', displayOrder: 1, available: true, imageUrl: null, version: 0 }, { id:'configured-item',storeId,displayName:'Build a Pizza',price:0,categoryId:'pizza',categoryName:'Pizza',displayOrder:2,available:true,variants:[{id:'small',name:'Small',price:10,displayOrder:1,available:true},{id:'large',name:'Large',price:18,displayOrder:2,available:true}],components:[{id:'tomato',name:'Tomato',includedByDefault:true,removable:true,allowExtra:false,extraPrice:0,displayOrder:1,active:true},{id:'pickles',name:'Pickles',includedByDefault:true,removable:true,allowExtra:true,extraPrice:.5,displayOrder:2,active:true}],modifierGroups:[{id:'sauce',name:'Wing Sauce',minimumSelections:1,maximumSelections:1,displayOrder:1,active:true,options:[{id:'hot',name:'Hot',priceAdjustment:0,displayOrder:1,available:true}]},{id:'nachos',name:'Nacho Choice',minimumSelections:1,maximumSelections:1,displayOrder:2,active:true,options:[{id:'loaded',name:'Loaded Nachos',priceAdjustment:3,displayOrder:1,available:true}]}],version:0 }]);
      if (url.pathname.endsWith(`/stores/${storeId}/discounts`)) return response([{ id: 'staff-discount', name: 'Staff Discount', type: 'DISCOUNT_PERCENTAGE', value: 10, description: null, active: true, version: 0 }]);
      if (url.pathname.endsWith('/sales/checkout')) {
        const request = JSON.parse(String(init?.body));
        checkoutBody = request;
        const quantity = request.items?.[0]?.quantity ?? 0;
        return response(request.discount ? discountedSale(quantity) : sale(quantity), 201);
      }
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(discountedSale(2, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response(discountedSale(2, true, true));
      if (url.pathname.endsWith(`/sales/${saleId}/receipt`)) return response({ receiptNumber: 'RCT-FOOD-1' });
      return response({ message: `Unexpected ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/pos/food']} />);
    expect(await screen.findByText("Joe's Kitchen")).toBeInTheDocument();
    expect(screen.getByTestId('restaurant-pos-shell')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Current order' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Restaurant menu' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Search menu' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Pizza' })).toBeInTheDocument();
    expect(screen.getByText(/From CA\$10\.00/)).toBeInTheDocument();
    await userEvent.click(screen.getByText('Build a Pizza'));
    expect(await screen.findByRole('dialog', { name: 'Customize Build a Pizza' })).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: /Small/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Included' }));
    await userEvent.click(screen.getByRole('button', { name: /Extra \+/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Hot' }));
    expect(screen.getByRole('button', { name: 'Add to Order' })).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: /Loaded Nachos/ }));
    expect(screen.getByRole('button', { name: 'Add to Order' })).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: 'Add to Order' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Small')).toBeInTheDocument();
    expect(screen.getByText('+ Wing Sauce: Hot')).toBeInTheDocument();
    expect(screen.getByText('+ Nacho Choice: Loaded Nachos')).toBeInTheDocument();
    expect(screen.getByText('NO TOMATO')).toBeInTheDocument();
    expect(screen.getByText('+ Extra Pickles')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    expect((await screen.findAllByText(/12\.00/)).length).toBeGreaterThan(0);
    expect(screen.getByText('At checkout')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Increase Pepperoni Pizza' }));
    expect((await screen.findAllByText(/24\.00/)).length).toBeGreaterThan(0);
    expect(calls.filter(call => call.includes('/sales/checkout'))).toHaveLength(0);
    await userEvent.click(screen.getByRole('combobox', { name: 'Discount' }));
    expect(await screen.findByRole('option', { name: 'Staff Discount — 10%' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: 'Staff Discount — 10%' }));
    expect(screen.getByText('Staff Discount (10%)')).toBeInTheDocument();
    expect(calls.filter(call => call.includes('/sales/checkout'))).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Remove Discount' }));
    await openCustomDiscount();
    const discountDialog = screen.getByRole('dialog', { name: 'Apply Discount' });
    const discountPaper = screen.getByTestId('discount-dialog-paper');
    expect(discountDialog).toBeVisible();
    expect(discountPaper).toHaveStyle({ width: 'calc(100vw - 24px)', maxWidth: '500px' });
    expect(discountPaper).not.toHaveClass('MuiDialog-paperFullScreen');
    expect(screen.getByRole('button', { name: 'Apply Discount' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Apply Discount' })).not.toBeInTheDocument());
    await openCustomDiscount();
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Percentage' }), '10');
    await userEvent.type(screen.getByRole('textbox', { name: 'Reason (optional)' }), 'Employee meal');
    await userEvent.click(screen.getByRole('button', { name: 'Apply Discount' }));
    expect(screen.getByText('Custom Discount (10%)')).toBeInTheDocument();
    expect(calls.filter(call => call.includes('/sales/checkout'))).toHaveLength(0);
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Apply Discount' })).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Edit Custom Discount' }));
    await userEvent.click(screen.getByRole('button', { name: 'Fixed Amount' }));
    await userEvent.clear(screen.getByRole('spinbutton', { name: 'Discount amount' }));
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Discount amount' }), '5');
    await userEvent.click(screen.getByRole('button', { name: 'Apply Discount' }));
    expect((await screen.findAllByText(/CA\$5\.00/)).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Apply Discount' })).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Remove Discount' }));
    expect(screen.getByRole('combobox', { name: 'Discount' })).toHaveAttribute('aria-expanded', 'false');
    await openCustomDiscount();
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Percentage' }), '10');
    await userEvent.type(screen.getByRole('textbox', { name: 'Reason (optional)' }), 'Employee meal');
    await userEvent.click(screen.getByRole('button', { name: 'Apply Discount' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Apply Discount' })).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Calculate Tax' }));
    await waitFor(() => expect(calls.filter(call => call.includes('/sales/checkout'))).toHaveLength(1));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    expect(calls.filter(call => call.includes('/sales/checkout'))).toHaveLength(1);
    expect(checkoutBody).toEqual({ registerSessionId: sessionId, saleChannel: 'POS', items: [{ foodMenuItemId: 'menu-item', quantity: 2 }], discount: { type: 'DISCOUNT_PERCENTAGE', value: 10, reason: 'Employee meal' } });
    expect((await screen.findAllByText(/24\.84/)).length).toBeGreaterThan(0);
    const firstPaymentDialog = await screen.findByRole('dialog', { name: 'Take payment' });
    const firstCashInput = within(firstPaymentDialog).getByRole('textbox', { name: 'Cash received' });
    await userEvent.clear(firstCashInput);
    await userEvent.type(firstCashInput, '60');
    expect(within(firstPaymentDialog).getAllByText(/CA\$60\.00/).length).toBeGreaterThan(0);
    expect(within(firstPaymentDialog).getByText(/CA\$35\.15/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    expect(await screen.findByText(/RCT-FOOD-1/)).toBeInTheDocument();
    await waitFor(() => expect(calls).toEqual(expect.arrayContaining([`POST /api/v1/sales/${saleId}/payments`, `POST /api/v1/sales/${saleId}/complete`, `GET /api/v1/sales/${saleId}/receipt`])));

    await userEvent.click(screen.getByRole('button', { name: 'New Order' }));
    expect(await screen.findByText('Tap a product tile to begin.')).toBeInTheDocument();
    expect(screen.queryByText('Order completed')).not.toBeInTheDocument();
    expect(screen.queryByText(/A008/)).not.toBeInTheDocument();
    expect(screen.queryByText('Custom Discount (10%)')).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Discount' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText('At checkout')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    const secondPaymentDialog = await screen.findByRole('dialog', { name: 'Take payment' });
    expect(within(secondPaymentDialog).getAllByText(/CA\$0\.00/).length).toBeGreaterThan(0);
    expect(within(secondPaymentDialog).getAllByText(/CA\$13\.80/).length).toBeGreaterThan(0);
    expect(within(secondPaymentDialog).queryByText(/CA\$60\.00/)).not.toBeInTheDocument();
    expect(calls.filter(call => call.endsWith('/food-menu/items'))).toHaveLength(1);
    expect(calls.filter(call => call.endsWith('/food-menu/categories'))).toHaveLength(1);
  });

  it('adds taxable and non-taxable custom food items without descriptions', async () => {
    let checkoutBody:{items:Array<Record<string,unknown>>}|undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input,init) => {
      const url=new URL(String(input),window.location.origin);
      if(url.pathname.endsWith('/auth/me'))return response({userId:'user',email:'kitchen@test',displayName:'Kitchen',roles:['KITCHEN'],permissions:['FOOD_POS_ACCESS','POS_CUSTOM_ITEM']});
      if(url.pathname.endsWith('/register-sessions/current'))return response({id:sessionId,storeId,registerId:'register',status:'OPEN',registerType:'FOOD_SERVICE'});
      if(url.pathname.endsWith('/stores'))return response(page([{id:storeId,code:'MAIN',name:'Main',currencyCode:'CAD',capabilities:['FOOD_SERVICE']} ]));
      if(url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`))return response({storeId,restaurantPosEnabled:true,kitchenDisplayName:"Joe's Kitchen"});
      if(url.pathname.endsWith('/food-menu/categories')||url.pathname.endsWith('/food-menu/items')||url.pathname.endsWith(`/stores/${storeId}/discounts`))return response([]);
      if(url.pathname.endsWith('/sales/checkout')&&init?.method==='POST'){checkoutBody=JSON.parse(String(init.body));return response(sale());}
      return response({},404);
    });
    render(<App initialEntries={['/pos/food']}/>);
    await screen.findByText("Joe's Kitchen");

    await userEvent.click(screen.getByRole('button',{name:'Taxable Item'}));
    const taxableDialog=await screen.findByRole('dialog',{name:'Taxable Item'});
    expect(taxableDialog).toHaveStyle({width:'430px',height:'auto'});
    expect(within(taxableDialog).queryByLabelText(/Description/i)).not.toBeInTheDocument();
    const taxableAmount=within(taxableDialog).getByLabelText('Amount');
    expect(taxableAmount).toHaveFocus();
    await userEvent.type(taxableAmount,'20');
    await userEvent.click(within(taxableDialog).getByRole('button',{name:'Increase custom item quantity'}));
    await userEvent.type(taxableAmount,'{Enter}');
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button',{name:'Non-Taxable Item'}));
    const nonTaxableDialog=await screen.findByRole('dialog',{name:'Non-Taxable Item'});
    await userEvent.type(within(nonTaxableDialog).getByLabelText('Amount'),'15');
    await userEvent.click(within(nonTaxableDialog).getByRole('button',{name:'Add to Order'}));
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getAllByText('Custom Food Item')).toHaveLength(2);
    expect(screen.getByText('Taxable')).toBeInTheDocument();
    expect(screen.getByText('Non-Taxable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'Calculate Tax'}));

    await waitFor(()=>expect(checkoutBody?.items).toEqual([
      {lineType:'CUSTOM_ITEM',description:'Custom Food Item',unitPrice:20,taxTreatment:'TAXABLE',quantity:2},
      {lineType:'CUSTOM_ITEM',description:'Custom Food Item',unitPrice:15,taxTreatment:'NON_TAXABLE',quantity:1}
    ]));
  });

  it('keeps the restaurant cart intact and shows a friendly error when checkout fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', active: true, name: 'Pizza' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/checkout')) return response({ message: 'INVALID_MENU_ITEM', correlationId: 'corr-123' }, 404);
      return response({}, 404);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));

    expect(await screen.findByText('Restaurant checkout could not be calculated. Your order is still in the cart.')).toBeInTheDocument();
    expect(screen.getAllByText('Pepperoni Pizza').length).toBeGreaterThan(0);
    expect(screen.getByText(/1 × .*12\.00 = .*12\.00/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps order, discount, and tender state when payment fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS', 'POS_SALE_DISCOUNT'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', active: true, name: 'Pizza' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/checkout')) return response(sale(1), 201);
      if (url.pathname.endsWith(`/sales/${saleId}/payments`) && init?.method === 'POST') return response({ code: 'PAYMENT_NOT_ALLOWED', message: 'Payment could not be recorded.' }, 400);
      return response({}, 404);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await openCustomDiscount();
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Percentage' }), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Apply Discount' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Apply Discount' })).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    const paymentDialog = await screen.findByRole('dialog', { name: 'Take payment' });
    await userEvent.click(within(paymentDialog).getByRole('button', { name: 'Exact' }));
    await userEvent.click(within(paymentDialog).getByRole('button', { name: 'Record payment' }));

    await waitFor(() => expect(screen.getByRole('dialog', { name: 'Take payment' })).toBeVisible());
    expect(within(paymentDialog).getAllByText(/CA\$13\.80/).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: 'New Order' })).not.toBeInTheDocument();
    await userEvent.click(within(paymentDialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.getByText('Custom Discount (10%)')).toBeInTheDocument();
    expect(screen.getAllByText('Pepperoni Pizza').length).toBeGreaterThan(0);
  });

  it('blocks users without food POS permission', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).endsWith('/auth/me') ? response({ userId: 'user', email: 'x', displayName: 'Retail', roles: ['CASHIER'], permissions: ['POS_ACCESS'] }) : response({}, 500));
    render(<App initialEntries={['/pos/food']} />);
    expect(await screen.findByText('FOOD_POS_ACCESS is required.')).toBeInTheDocument();
  });

  it('offers persisted print choices and Print Both queues exactly two jobs before permitting a kitchen reprint', async () => {
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'BROWSER' });
    const persistedReceipt = { id: 'receipt-food-1', saleId, receiptNumber: 'RCT-FOOD-1', document: { ...testReceiptDocument(), saleId, receiptNumber: 'RCT-FOOD-1', tokenNumber: 'A104' } };
    const persistedTicket = {
      documentType: 'KITCHEN_TICKET', saleId, tokenNumber: 'A104', storeName: 'Main', registerName: 'Restaurant Register',
      cashierName: 'Kitchen', orderTime: '2026-08-28T12:00:00Z', orderType: 'TAKEOUT', tableNumber: null,
      orderNotes: 'Extra napkins', reprint: false,
      items: [{ saleItemId: itemId, name: 'Pepperoni Pizza', quantity: 1, modifiers: [], preparationInstructions: null }]
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, code: 'MAIN', name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', storeId, name: 'Pizza', displayOrder: 1, active: true }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', storeId, displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/checkout')) return response(sale(1), 201);
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(sale(1, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response(sale(1, true, true));
      if (url.pathname.endsWith(`/sales/${saleId}/receipt/reprint`) && init?.method === 'POST') return response({ ...persistedReceipt, reprint: true });
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-ticket/reprint`) && init?.method === 'POST') return response({ ...persistedTicket, reprint: true });
      if (url.pathname.endsWith(`/sales/${saleId}/receipt`)) return response(persistedReceipt);
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-ticket`)) return response(persistedTicket);
      return response({ message: `Unexpected ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Exact' }));
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    expect(print).not.toHaveBeenCalled();
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    expect(await screen.findByRole('button', { name: 'Print Both' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Kitchen Ticket' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Customer Receipt' })).toBeEnabled();
    expect(print).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Print Both' }));
    await waitFor(() => expect(print).toHaveBeenCalledTimes(2));
    expect(print.mock.calls[0][1]).toContain('Kitchen ticket A104');
    expect(print.mock.calls[1][1]).toContain('Customer receipt A104');
    fireEvent.focus(window);
    await Promise.resolve();
    expect(print).toHaveBeenCalledTimes(2);
    await userEvent.click(screen.getByRole('button', { name: 'Reprint Kitchen Ticket' }));
    await waitFor(() => expect(print).toHaveBeenCalledTimes(3));
  });

  it('does not print when restaurant order completion fails', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({ receiptPrintMode: 'KIOSK_AUTO_PRINT', autoPrintReceipt: true }));
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'BROWSER' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD' }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', active: true, name: 'Pizza' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/checkout')) return response(sale(1), 201);
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(sale(1, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response({ message: 'Completion failed' }, 500);
      return response({}, 404);
    });
    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Exact' }));
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    await waitFor(() => expect(print).not.toHaveBeenCalled());
    expect(screen.queryByText('Order completed')).not.toBeInTheDocument();
  });

  it('holds and restores a restaurant order inside Restaurant POS', async () => {
    const menuItem = { id: 'menu-item', storeId, productId, displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true, modifierGroups: [] };
    const draft = { ...sale(1), items: [{ ...sale(1).items[0], foodMenuItemId: 'menu-item', foodMenuItemName: 'Pepperoni Pizza', foodMenuModifiers: [] }] };
    const held = { ...draft, status: 'HELD', heldAt: '2026-08-28T12:10:00Z' };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', active: true, name: 'Pizza' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([menuItem]);
      if (url.pathname.endsWith('/sales/checkout')) return response(draft, 201);
      if (url.pathname.endsWith(`/sales/${saleId}/hold`) && init?.method === 'POST') return response(held);
      if (url.pathname.endsWith(`/sales/${saleId}/resume`) && init?.method === 'POST') return response(draft);
      if (url.pathname.endsWith('/sales') && url.searchParams.get('status') === 'HELD') return response(page([held]));
      if (url.pathname.endsWith(`/stores/${storeId}/discounts`)) return response([]);
      return response({}, 404);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Hold' }));
    const dialog = await screen.findByRole('dialog', { name: 'Held Orders' });
    expect(within(dialog).getByText(/1 items/)).toBeInTheDocument();
    expect(screen.getByText('Tap a product tile to begin.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Resume' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Held Orders' })).not.toBeInTheDocument());
    expect(screen.getAllByText('Pepperoni Pizza').length).toBeGreaterThan(1);
    expect(screen.getByRole('button', { name: 'Checkout' })).toHaveTextContent(/CA\$13\.80/);
  });

  it('confirms an unpaid scheduled phone order without printing early, marks ready, and pays the same order at pickup', async () => {
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'BROWSER' });
    const menuItem = { id: 'menu-item', storeId, productId, displayName: 'Burger', price: 12, categoryId: 'food', available: true, modifierGroups: [] };
    const priced = { ...sale(1), status: 'PENDING_PAYMENT', items: [{ ...sale(1).items[0], productName: 'Burger', foodMenuItemId: 'menu-item', foodMenuItemName: 'Burger', foodMenuModifiers: [] }] };
    let pickup = { ...priced, status: 'PHONE_CONFIRMED', foodOrderToken: '1045', phoneCustomerName: 'John', phoneNumber: '506-555-1234', pickupAt: '2027-01-15T16:30:00Z', pickupAsap: false, orderNotes: 'Call on arrival', phoneConfirmedAt: '2027-01-15T14:00:00Z', kitchenStatus: 'PENDING', paidAmount: 0, balanceDue: 13.8, paymentComplete: false } as Sale;
    let paymentWrites = 0;
    let completionWrites = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register-2', status: 'OPEN', registerType: 'FOOD_SERVICE' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD', timezone: 'America/Moncton', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'food', active: true, name: 'Food' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([menuItem]);
      if (url.pathname.endsWith(`/stores/${storeId}/discounts`)) return response([]);
      if (url.pathname.endsWith('/sales/checkout')) return response(priced, 201);
      if (url.pathname.endsWith(`/sales/${saleId}/phone-order/confirm`)) return response(pickup);
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-ticket`)) return response({ documentType: 'KITCHEN_TICKET', saleId, tokenNumber: '1045', storeName: 'Main', registerName: 'Restaurant 1', cashierName: 'Kitchen', orderTime: pickup.phoneConfirmedAt, orderType: 'PHONE ORDER', tableNumber: null, customerName: 'John', pickupAt: pickup.pickupAt, pickupAsap: false, storeTimezone: 'America/Moncton', kitchenStatus: pickup.kitchenStatus, items: [{ saleItemId: itemId, name: 'Burger', quantity: 1, modifiers: [], preparationInstructions: null }], orderNotes: pickup.orderNotes, reprint: false });
      if (url.pathname.endsWith('/sales/phone-orders/pickup')) return response(pickup.kitchenStatus === 'COMPLETED' ? [] : [pickup]);
      if (url.pathname.endsWith('/sales/phone-orders/history')) return response(pickup.kitchenStatus === 'COMPLETED' ? [pickup] : []);
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-status`)) { const status = JSON.parse(String(init?.body)).status; pickup = { ...pickup, kitchenStatus: status }; return response(pickup); }
      if (url.pathname.endsWith(`/sales/${saleId}/phone-order/claim`)) return response({ ...pickup, registerId: 'register-2', registerSessionId: sessionId, businessDate: '2027-01-15' });
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) { paymentWrites += 1; const paid = sale(1, true); pickup = { ...pickup, payments: paid.payments as Sale['payments'], paidAmount: 13.8, balanceDue: 0, paymentComplete: true, paymentStatus: 'PAID' }; return response(pickup); }
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) { completionWrites += 1; pickup = { ...pickup, status: 'COMPLETED', paidAmount: 13.8, balanceDue: 0, paymentComplete: true, paymentStatus: 'PAID', completedAt: '2027-01-15T16:31:00Z' }; return response(pickup); }
      if (url.pathname.endsWith(`/sales/${saleId}/receipt`)) return response({ receiptNumber: 'RCT-1045', document: testReceiptDocument() });
      return response({}, 404);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Burger'));
    await userEvent.click(screen.getByRole('button', { name: 'Phone Order' }));
    const phoneDialog = screen.getByRole('dialog', { name: 'Phone Order' });
    await userEvent.type(within(phoneDialog).getByRole('textbox', { name: 'Customer Name' }), 'John');
    await userEvent.type(within(phoneDialog).getByRole('textbox', { name: 'Phone Number' }), '506-555-1234');
    await userEvent.click(within(phoneDialog).getByRole('combobox', { name: 'Pickup' }));
    await userEvent.click(screen.getByRole('option', { name: 'Scheduled Time' }));
    fireEvent.change(await screen.findByLabelText(/Scheduled Pickup/), { target: { value: '2027-01-15T12:30' } });
    await userEvent.click(within(phoneDialog).getByRole('button', { name: 'Confirm Phone Order' }));

    const pickupDialog = await screen.findByRole('dialog', { name: 'Pickup Orders' });
    expect(within(pickupDialog).getByText(/John · #1045/)).toBeVisible();
    expect(within(pickupDialog).getByText(/UNPAID/)).toBeVisible();
    expect(paymentWrites).toBe(0);
    expect(print).not.toHaveBeenCalled();
    await userEvent.click(within(pickupDialog).getByRole('button', { name: 'Open' }));
    expect(within(pickupDialog).getByRole('button', { name: 'Cancel & Rebuild' })).toBeVisible();
    await userEvent.click(within(pickupDialog).getByRole('button', { name: 'Mark Ready' }));
    await waitFor(() => expect(within(pickupDialog).getByText(/Kitchen: READY/)).toBeVisible());
    await userEvent.click(within(pickupDialog).getByRole('button', { name: 'Pay Order' }));
    const paymentDialog = await screen.findByRole('dialog', { name: 'Take payment' });
    await userEvent.click(within(paymentDialog).getByRole('button', { name: 'Exact' }));
    await userEvent.click(within(paymentDialog).getByRole('button', { name: 'Record payment' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    await waitFor(() => expect(completionWrites).toBe(1));
    expect(paymentWrites).toBe(1);
    expect(await screen.findByText('Order completed')).toBeVisible();
    expect(screen.getByText('TOKEN 1045')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'New Order' }));
    await userEvent.click(screen.getByRole('button', { name: 'Pickup Orders' }));
    const paidPickupDialog = await screen.findByRole('dialog', { name: 'Pickup Orders' });
    expect(within(paidPickupDialog).getByText(/CA\$13\.80/)).toHaveTextContent(/READY.*PAID/);
    await userEvent.click(within(paidPickupDialog).getByRole('button', { name: 'Open' }));
    await userEvent.click(within(paidPickupDialog).getByRole('button', { name: 'Mark Completed' }));
    expect(await within(paidPickupDialog).findByText(/CA\$13\.80/)).toHaveTextContent(/COMPLETED.*PAID/);
    await userEvent.click(within(paidPickupDialog).getByRole('tab', { name: 'Active' }));
    await waitFor(() => expect(within(paidPickupDialog).getByText('There are no active pickup orders.')).toBeVisible());
  });
});
