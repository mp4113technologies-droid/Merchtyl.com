import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';

const storeId='00000000-0000-0000-0000-000000000801';
const json=(body:unknown,status=200)=>Promise.resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}));
const page=(content:unknown[])=>({content,page:0,size:100,totalElements:content.length,totalPages:1,first:true,last:true});

describe('Food menu management',()=>{
  beforeEach(()=>{const now=Date.now();localStorage.setItem('merchtyl.session',JSON.stringify({accessToken:'token',refreshToken:'refresh',tokenType:'Bearer',accessTokenExpiresAt:new Date(now+900000).toISOString(),refreshTokenExpiresAt:new Date(now+86400000).toISOString(),userId:'owner',email:'owner@test',displayName:'Owner',roles:['OWNER']}));vi.restoreAllMocks();});
  it('renders ordered store menu items and marks an item sold out',async()=>{
    const calls:string[]=[];
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);calls.push(`${init?.method??'GET'} ${url.pathname}`);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'owner@test',displayName:'Owner',roles:['OWNER'],permissions:['FOOD_POS_ACCESS','FOOD_ORDER_UPDATE','PRODUCT_MANAGE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'Kitchen',code:'K',capabilities:['FOOD_SERVICE']}]));
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Pizza',displayOrder:1,active:true,imageUrl:null,version:0}]);
      if(url.pathname.endsWith('/food-menu/items')&&(!init?.method||init.method==='GET'))return json([{id:'item',storeId,categoryId:'cat',categoryName:'Pizza',productId:null,productName:null,displayName:'Pepperoni',description:null,price:14,inventoryTracked:false,madeToOrder:true,displayOrder:2,available:true,imageUrl:null,version:0}]);
      if(url.pathname.endsWith('/products'))return json(page([]));
      if(url.pathname.endsWith('/availability'))return json({}); return json({},500);});
    render(<App initialEntries={['/food-menu']}/>);
    expect(await screen.findByText('Pepperoni')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox',{name:'Available'}));
    expect(calls).toContain(`PATCH /api/v1/stores/${storeId}/food-menu/items/item/availability`);
  });

  it('creates a made-to-order item without selecting a Retail Product',async()=>{
    let submitted:Record<string,unknown>|undefined;
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'adviamcreatives@gmail.com',displayName:'Owner',roles:['TENANT_OWNER'],permissions:['FOOD_POS_ACCESS','FOOD_ORDER_UPDATE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'adviam',code:'STORE1234',capabilities:['FOOD_SERVICE']}]))
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Snacks',displayOrder:1,active:true,imageUrl:null,version:0}]);
      if(url.pathname.endsWith('/food-menu/items')&&init?.method==='POST'){submitted=JSON.parse(String(init.body));return json({},201);}
      if(url.pathname.endsWith('/food-menu/items'))return json([]);
      if(url.pathname.endsWith('/products'))return json(page([]));return json({},500);});
    render(<App initialEntries={['/food-menu']}/>);
    expect(await screen.findByRole('heading',{name:'Restaurant Menu'})).toBeInTheDocument();
    const addButton=screen.getByRole('button',{name:'Add Menu Item'});await waitFor(()=>expect(addButton).toBeEnabled());await userEvent.click(addButton);
    await userEvent.click(screen.getByLabelText('Restaurant Category'));
    await userEvent.click(await screen.findByRole('option',{name:'Snacks'}));
    await userEvent.type(screen.getByLabelText('Name'),'Samosa');
    await userEvent.clear(screen.getByLabelText('Price'));await userEvent.type(screen.getByLabelText('Price'),'2.49');
    await userEvent.click(screen.getByRole('button',{name:'Add'}));
    expect(submitted).toEqual(expect.objectContaining({displayName:'Samosa',categoryId:'cat',price:2.49}));
    expect(submitted?.productId).toBeUndefined();
  });

  it('keeps stable focus and row identity while editing variants and modifiers', async () => {
    const requests:string[]=[];
    let saved:Record<string,unknown>|undefined;
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);requests.push(`${init?.method??'GET'} ${url.pathname}`);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'owner@test',displayName:'Owner',roles:['TENANT_OWNER'],permissions:['FOOD_ORDER_UPDATE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'Kitchen',code:'K',capabilities:['FOOD_SERVICE']}]));
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Entrees',displayOrder:1,active:true}]);
      if(url.pathname.endsWith('/food-menu/items')&&(!init?.method||init.method==='GET'))return json([{id:'item',storeId,categoryId:'cat',categoryName:'Entrees',productId:null,productName:null,displayName:'Monkey Fingers',description:null,price:8,inventoryTracked:false,madeToOrder:true,displayOrder:1,available:true,imageUrl:null,variants:[],modifierGroups:[],version:0}]);
      if(url.pathname.endsWith('/food-menu/items/item')&&init?.method==='PUT'){saved=JSON.parse(String(init.body));return json({});}
      if(url.pathname.endsWith('/products'))return json(page([]));
      return json({},404);
    });
    render(<App initialEntries={['/food-menu']}/>);
    await screen.findByText('Monkey Fingers');
    await userEvent.click(screen.getByLabelText('Menu item'));
    await userEvent.click(await screen.findByRole('option',{name:'Monkey Fingers'}));

    for (const [name,price] of [['Small','10.99'],['Medium','14.99'],['Large','18.99']]) {
      await userEvent.click(screen.getByRole('button',{name:'Add Variant'}));
      const names=screen.getAllByLabelText('Variant name');
      const prices=screen.getAllByLabelText('Variant price');
      const nameInput=names[names.length-1];
      await userEvent.type(nameInput,name);
      expect(nameInput).toHaveValue(name);
      expect(nameInput).toHaveFocus();
      await userEvent.clear(prices[prices.length-1]);
      await userEvent.type(prices[prices.length-1],price);
      expect(prices[prices.length-1]).toHaveValue(Number(price));
      expect(prices[prices.length-1]).toHaveFocus();
    }

    const mediumRow=screen.getAllByTestId(/variant-row-/)[1];
    await userEvent.click(within(mediumRow).getByRole('button',{name:'Remove Variant'}));
    expect(screen.getAllByLabelText('Variant name').map(input=>(input as HTMLInputElement).value)).toEqual(['Small','Large']);
    expect(screen.getAllByLabelText('Variant price').map(input=>(input as HTMLInputElement).valueAsNumber)).toEqual([10.99,18.99]);

    await userEvent.click(screen.getByRole('button',{name:'Add Modifier Group'}));
    const groupName=screen.getByLabelText('Modifier group name');
    await userEvent.type(groupName,'Seasoning');
    expect(groupName).toHaveValue('Seasoning');
    expect(groupName).toHaveFocus();
    for (const optionName of ['Mild','Medium','Hot','Peri Peri']) {
      await userEvent.click(screen.getByRole('button',{name:'Add Modifier'}));
      const inputs=screen.getAllByLabelText('Modifier name');
      const input=inputs[inputs.length-1];
      await userEvent.type(input,optionName);
      expect(input).toHaveValue(optionName);
      expect(input).toHaveFocus();
    }
    const modifierPrices=screen.getAllByLabelText('Added price');
    for (const [index,price] of ['0.25','0.50','0.75','1.00'].entries()) {
      await userEvent.clear(modifierPrices[index]);
      await userEvent.type(modifierPrices[index],price);
      expect(modifierPrices[index]).toHaveValue(Number(price));
      expect(modifierPrices[index]).toHaveFocus();
    }
    const mediumModifierRow=screen.getAllByTestId(/modifier-row-/)[1];
    await userEvent.click(within(mediumModifierRow).getByRole('button',{name:'Remove Modifier'}));
    expect(screen.getAllByLabelText('Modifier name').map(input=>(input as HTMLInputElement).value)).toEqual(['Mild','Hot','Peri Peri']);
    expect(screen.getAllByLabelText('Added price').map(input=>(input as HTMLInputElement).valueAsNumber)).toEqual([0.25,0.75,1]);
    expect(requests.some(request=>request.startsWith('PUT '))).toBe(false);
    await userEvent.click(screen.getByRole('button',{name:'Save configuration'}));
    await waitFor(()=>expect(saved).toBeDefined());
    expect((saved?.variants as Array<{name:string}>).map(value=>value.name)).toEqual(['Small','Large']);
    expect(((saved?.modifierGroups as Array<{name:string;options:Array<{name:string}>}>)[0]).options.map(value=>value.name)).toEqual(['Mild','Hot','Peri Peri']);
  });
});
