import { render, screen } from '@testing-library/react';
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
      if(url.pathname.endsWith('/food-menu/items')&&(!init?.method||init.method==='GET'))return json([{id:'item',storeId,categoryId:'cat',categoryName:'Pizza',productId:'product',productName:'Base Pizza',displayName:'Pepperoni',price:14,displayOrder:2,available:true,imageUrl:null,version:0}]);
      if(url.pathname.endsWith('/products'))return json(page([]));
      if(url.pathname.endsWith('/availability'))return json({}); return json({},500);});
    render(<App initialEntries={['/food-menu']}/>);
    expect(await screen.findByText('Pepperoni')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox',{name:'Available'}));
    expect(calls).toContain(`PATCH /api/v1/stores/${storeId}/food-menu/items/item/availability`);
  });
});
