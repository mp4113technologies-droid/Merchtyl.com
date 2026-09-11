import { render,screen,waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';

const json=(body:unknown,status=200)=>Promise.resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}));

describe('saved discount management',()=>{
  beforeEach(()=>{const now=Date.now();localStorage.setItem('merchtyl.session',JSON.stringify({accessToken:'token',refreshToken:'refresh',tokenType:'Bearer',accessTokenExpiresAt:new Date(now+900000).toISOString(),refreshTokenExpiresAt:new Date(now+86400000).toISOString(),userId:'manager',email:'manager@test',displayName:'Manager',roles:['MANAGER']}));vi.restoreAllMocks();});

  it('lets a manager create a compact saved percentage discount and deactivate existing definitions',async()=>{
    let submitted:unknown; const calls:string[]=[];
    const existing={id:'five-off',name:'Five Off',type:'DISCOUNT_AMOUNT',value:5,description:null,active:true,createdAt:'2026-09-01T00:00:00Z',updatedAt:'2026-09-01T00:00:00Z',version:0};
    vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),location.origin);calls.push(`${init?.method??'GET'} ${url.pathname}`);
      if(url.pathname.endsWith('/auth/me'))return json({userId:'manager',email:'manager@test',displayName:'Manager',roles:['MANAGER'],permissions:['DISCOUNT_VIEW','DISCOUNT_MANAGE']});
      if(url.pathname.endsWith('/stores'))return json({content:[],page:0,size:100,totalElements:0,totalPages:0,first:true,last:true});
      if(url.pathname.endsWith('/discounts')&&init?.method==='POST'){submitted=JSON.parse(String(init.body));return json({...submitted as Record<string,unknown>,id:'staff',createdAt:'',updatedAt:'',version:0},201);}
      if(url.pathname.endsWith('/discounts/five-off')&&init?.method==='PUT')return json({...existing,active:false});
      if(url.pathname.endsWith('/discounts'))return json([existing]); return json({},200);});
    render(<App initialEntries={['/discounts']}/>);
    expect(await screen.findByText('Five Off')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'Add Discount'}));
    expect(screen.getByRole('dialog',{name:'Add Discount'})).not.toHaveClass('MuiDialog-paperFullScreen');
    await userEvent.type(screen.getByLabelText('Discount name'),'Staff Discount');
    await userEvent.type(screen.getByLabelText('Percentage'),'10');
    await userEvent.click(screen.getByRole('button',{name:'Save Discount'}));
    await waitFor(()=>expect(submitted).toEqual({name:'Staff Discount',type:'DISCOUNT_PERCENTAGE',value:10,description:'',eligibleCategoryIds:[],eligibleProductIds:[],allStores:true,storeIds:[],active:true,priority:0,stackable:false,targets:[]}));
    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'Add Discount'})).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button',{name:'Deactivate'}));
    await waitFor(()=>expect(calls).toContain('PUT /api/v1/discounts/five-off'));
  });
});
