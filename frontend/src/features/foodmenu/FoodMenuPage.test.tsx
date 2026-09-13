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

  it('appends and independently configures multiple reusable choice groups',async()=>{
    vi.spyOn(globalThis,'fetch').mockImplementation((input)=>{const url=new URL(String(input),location.origin);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'owner@test',displayName:'Owner',roles:['TENANT_OWNER'],permissions:['FOOD_POS_ACCESS','FOOD_ORDER_UPDATE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'Kitchen',code:'K',capabilities:['FOOD_SERVICE']} ]));
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Combos',displayOrder:1,active:true}]);
      if(url.pathname.endsWith('/food-menu/choice-groups'))return json([
        {id:'sauce',storeId,name:'Wing Sauce',active:true,usageCount:0,usedBy:[],options:[{id:'hot',name:'Hot',priceAdjustment:0,displayOrder:0,available:true}],version:0},
        {id:'nachos',storeId,name:'Nacho Choice',active:true,usageCount:0,usedBy:[],options:[{id:'loaded',name:'Loaded Nachos',priceAdjustment:3,displayOrder:0,available:true}],version:0},
        {id:'extras',storeId,name:'Extras',active:true,usageCount:0,usedBy:[],options:[{id:'cheese',name:'Cheese',priceAdjustment:1,displayOrder:0,available:true}],version:0}
      ]);
      if(url.pathname.endsWith('/food-menu/items'))return json([]);
      if(url.pathname.endsWith('/products'))return json(page([]));
      return json({},404);
    });
    render(<App initialEntries={['/food-menu']}/>);
    const add=await screen.findByRole('button',{name:'Add Menu Item'});
    await waitFor(()=>expect(add).toBeEnabled());
    await userEvent.click(add);
    for(const name of ['Wing Sauce','Nacho Choice','Extras']){
      await userEvent.click(screen.getByLabelText('Attach Choice Group'));
      await userEvent.click(await screen.findByRole('option',{name}));
    }
    expect(screen.getAllByText('Wing Sauce').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Nacho Choice').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Extras').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('button',{name:'Detach'})).toHaveLength(3);
    const orderFields=screen.getAllByLabelText('Order');
    await userEvent.clear(orderFields[1]);
    await userEvent.type(orderFields[1],'7');
    expect(orderFields[0]).toHaveValue(0);
    expect(orderFields[1]).toHaveValue(7);
    expect(orderFields[2]).toHaveValue(2);
  });

  it('edits core fields and complete configuration from the menu item card', async () => {
    let item = {id:'item',storeId,categoryId:'cat',categoryName:'Entrees',productId:null,productName:null,displayName:'Monkey Fingers',description:'Chicken fingers',price:10,inventoryTracked:false,madeToOrder:true,displayOrder:1,available:true,imageUrl:null,variants:[{id:'small',name:'Small',price:10,displayOrder:0,available:true}],modifierGroups:[],components:[{id:'tomato',name:'Tomato',includedByDefault:true,removable:true,allowExtra:false,extraPrice:0,displayOrder:0,active:true}],version:0};
    let submitted:Record<string,unknown>|undefined;
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'owner@test',displayName:'Owner',roles:['TENANT_OWNER'],permissions:['FOOD_POS_ACCESS','FOOD_ORDER_UPDATE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'Kitchen',code:'K',capabilities:['FOOD_SERVICE']}]));
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Entrees',displayOrder:1,active:true},{id:'combo',storeId,name:'Combos',displayOrder:2,active:true}]);
      if(url.pathname.endsWith('/food-menu/choice-groups'))return json([]);
      if(url.pathname.endsWith('/food-menu/items/item')&&init?.method==='PUT'){
        const payload=JSON.parse(String(init.body)) as Record<string,unknown>;
        submitted=payload;
        item={...item,...payload,categoryName:payload.categoryId==='combo'?'Combos':'Entrees'} as typeof item;
        return json(item);
      }
      if(url.pathname.endsWith('/food-menu/items'))return json([item]);
      if(url.pathname.endsWith('/products'))return json(page([]));
      return json({},404);
    });

    render(<App initialEntries={['/food-menu']}/>);
    await screen.findByText('Monkey Fingers');
    await userEvent.click(screen.getByRole('button',{name:'Edit'}));
    expect(await screen.findByRole('dialog',{name:'Edit Food Item'})).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveValue('Monkey Fingers');
    expect(screen.getByLabelText('Description (optional)')).toHaveValue('Chicken fingers');
    expect(screen.getByLabelText('Price')).toHaveValue(10);
    expect(screen.getByLabelText('Variant name')).toHaveValue('Small');
    expect(screen.getByLabelText('Ingredient name')).toHaveValue('Tomato');
    await userEvent.clear(screen.getByLabelText('Description (optional)'));
    await userEvent.type(screen.getByLabelText('Description (optional)'),'Crispy chicken fingers');
    await userEvent.clear(screen.getByLabelText('Price'));
    await userEvent.type(screen.getByLabelText('Price'),'11');
    await userEvent.click(screen.getByLabelText('Restaurant Category'));
    await userEvent.click(await screen.findByRole('option',{name:'Combos'}));
    await userEvent.click(screen.getByRole('button',{name:'Save Changes'}));

    await waitFor(()=>expect(submitted).toEqual(expect.objectContaining({displayName:'Monkey Fingers',description:'Crispy chicken fingers',price:11,categoryId:'combo',available:true})));
    expect(await screen.findByText('Crispy chicken fingers')).toBeInTheDocument();
    expect(screen.getByText('$11.00 · position 1')).toBeInTheDocument();
    expect(screen.getByText('Restaurant menu item saved.')).toBeInTheDocument();
  });

  it('keeps stable focus and row identity while editing variants and reusable choices', async () => {
    const requests:string[]=[];
    let saved:Record<string,unknown>|undefined;
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);requests.push(`${init?.method??'GET'} ${url.pathname}`);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'owner',email:'owner@test',displayName:'Owner',roles:['TENANT_OWNER'],permissions:['FOOD_ORDER_UPDATE']});
      if(url.pathname.endsWith('/stores'))return json(page([{id:storeId,name:'Kitchen',code:'K',capabilities:['FOOD_SERVICE']}]));
      if(url.pathname.endsWith('/food-menu/categories'))return json([{id:'cat',storeId,name:'Entrees',displayOrder:1,active:true}]);
      if(url.pathname.endsWith('/food-menu/choice-groups')&&(!init?.method||init.method==='GET'))return json([]);
      if(url.pathname.endsWith('/food-menu/choice-groups')&&init?.method==='POST')return json({id:'seasoning',storeId,name:'Seasoning',active:true,usageCount:0,usedBy:[],options:JSON.parse(String(init.body)).options,version:0});
      if(url.pathname.endsWith('/food-menu/items')&&(!init?.method||init.method==='GET'))return json([{id:'item',storeId,categoryId:'cat',categoryName:'Entrees',productId:null,productName:null,displayName:'Monkey Fingers',description:null,price:8,inventoryTracked:false,madeToOrder:true,displayOrder:1,available:true,imageUrl:null,variants:[],modifierGroups:[],version:0}]);
      if(url.pathname.endsWith('/food-menu/items/item')&&init?.method==='PUT'){saved=JSON.parse(String(init.body));return json({});}
      if(url.pathname.endsWith('/products'))return json(page([]));
      return json({},404);
    });
    render(<App initialEntries={['/food-menu']}/>);
    await screen.findByText('Monkey Fingers');
    await userEvent.click(screen.getByRole('button',{name:'Edit'}));

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

    await userEvent.click(screen.getByRole('button',{name:'Create New Choice Group'}));
    const choiceDialog=await screen.findByRole('dialog',{name:'Create Choice Group'});
    const groupName=within(choiceDialog).getByLabelText('Name');
    await userEvent.type(groupName,'Seasoning');
    expect(groupName).toHaveValue('Seasoning');
    expect(groupName).toHaveFocus();
    for (const optionName of ['Mild','Medium','Hot','Peri Peri']) {
      await userEvent.click(screen.getByRole('button',{name:'Add Choice'}));
      const inputs=within(choiceDialog).getAllByLabelText('Choice');
      const input=inputs[inputs.length-1];
      await userEvent.type(input,optionName);
      expect(input).toHaveValue(optionName);
      expect(input).toHaveFocus();
    }
    const modifierPrices=within(choiceDialog).getAllByLabelText('Added price');
    for (const [index,price] of ['0.25','0.50','0.75','1.00'].entries()) {
      await userEvent.clear(modifierPrices[index]);
      await userEvent.type(modifierPrices[index],price);
      expect(modifierPrices[index]).toHaveValue(Number(price));
      expect(modifierPrices[index]).toHaveFocus();
    }
    await userEvent.click(screen.getAllByRole('button',{name:'Remove'})[1]);
    expect(within(choiceDialog).getAllByLabelText('Choice').map(input=>(input as HTMLInputElement).value)).toEqual(['Mild','Hot','Peri Peri']);
    expect(within(choiceDialog).getAllByLabelText('Added price').map(input=>(input as HTMLInputElement).valueAsNumber)).toEqual([0.25,0.75,1]);
    expect(requests.some(request=>request.startsWith('PUT '))).toBe(false);
    await userEvent.click(screen.getByRole('button',{name:'Save Choice Group'}));
    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'Create Choice Group'})).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button',{name:'Save Changes'}));
    await waitFor(()=>expect(saved).toBeDefined());
    expect((saved?.variants as Array<{name:string}>).map(value=>value.name)).toEqual(['Small','Large']);
    expect(saved?.modifierGroupAssignments).toEqual([expect.objectContaining({modifierGroupId:'seasoning'})]);
  });
});
