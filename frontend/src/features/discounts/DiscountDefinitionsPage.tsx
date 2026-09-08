import AddIcon from '@mui/icons-material/Add';
import { Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, InputLabel, ListItemText, MenuItem, Paper, Select, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { catalogueReferenceApi, createDiscountDefinition, deleteDiscountDefinition, listDiscountDefinitions, listFoodMenuCategories, listFoodMenuItems, listProducts, listStores, updateDiscountDefinition } from '../../api/client';
import type { DiscountDefinition, DiscountDefinitionPayload } from '../../api/types';
import { useSession } from '../../app/session';

const empty: DiscountDefinitionPayload = { name:'',type:'DISCOUNT_PERCENTAGE',value:0,description:'',eligibleCategoryIds:[],eligibleProductIds:[],allStores:true,storeIds:[],active:true };

export function DiscountDefinitionsPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const canView = currentUser?.permissions?.includes('DISCOUNT_VIEW') ?? false;
  const canManage = currentUser?.permissions?.includes('DISCOUNT_MANAGE') ?? false;
  const canDelete = (currentUser?.roles ?? []).some(role => role === 'OWNER' || role === 'TENANT_OWNER');
  const definitions = useQuery({ queryKey: ['discount-definitions'], queryFn: async () => listDiscountDefinitions(await getValidAccessToken()), enabled: canView });
  const stores=useQuery({queryKey:['stores','discount-management'],queryFn:async()=>listStores(await getValidAccessToken(),{size:100}),enabled:canView});
  const products=useQuery({queryKey:['products','discount-management'],queryFn:async()=>listProducts(await getValidAccessToken(),{size:100}),enabled:canView});
  const categories=useQuery({queryKey:['categories','discount-management'],queryFn:async()=>catalogueReferenceApi.categories.list(await getValidAccessToken(),{size:100,active:true}),enabled:canView});
  const foodReferences=useQueries({queries:(stores.data?.content??[]).flatMap(store=>[
    {queryKey:['food-categories','discount-management',store.id],queryFn:async()=>listFoodMenuCategories(await getValidAccessToken(),store.id),enabled:canView},
    {queryKey:['food-items','discount-management',store.id],queryFn:async()=>listFoodMenuItems(await getValidAccessToken(),store.id),enabled:canView}
  ])});
  const [search,setSearch]=React.useState('');
  const [status,setStatus]=React.useState<'ALL'|'ACTIVE'|'INACTIVE'>('ALL');
  const [editing, setEditing] = React.useState<DiscountDefinition | null | undefined>(undefined);
  const [form, setForm] = React.useState<DiscountDefinitionPayload>(empty);
  const save = useMutation({ mutationFn: async () => editing
    ? updateDiscountDefinition(await getValidAccessToken(), editing.id, form)
    : createDiscountDefinition(await getValidAccessToken(), form),
  onSuccess: async () => { setEditing(undefined); setForm(empty); await queryClient.invalidateQueries({ queryKey: ['discount-definitions'] }); } });
  const payload=(value:DiscountDefinition):DiscountDefinitionPayload=>({name:value.name,type:value.type,value:value.value,description:value.description??'',minimumPurchaseAmount:value.minimumPurchaseAmount??undefined,maximumPurchaseAmount:value.maximumPurchaseAmount??undefined,maximumDiscountAmount:value.maximumDiscountAmount??undefined,minimumQuantity:value.minimumQuantity??undefined,eligibleCategoryIds:value.eligibleCategoryIds??[],eligibleProductIds:value.eligibleProductIds??[],allStores:value.allStores??true,storeIds:value.storeIds??[],startsAt:value.startsAt??undefined,endsAt:value.endsAt??undefined,active:value.active});
  const toggle = useMutation({ mutationFn: async (value: DiscountDefinition) => updateDiscountDefinition(await getValidAccessToken(), value.id, { ...payload(value),active:!value.active }), onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['discount-definitions'] }) });
  const remove = useMutation({mutationFn:async(value:DiscountDefinition)=>deleteDiscountDefinition(await getValidAccessToken(),value.id),onSuccess:async()=>queryClient.invalidateQueries({queryKey:['discount-definitions']})});
  const open = (value: DiscountDefinition | null) => { setEditing(value); setForm(value ? payload(value) : empty); };
  const invalid = !form.name.trim() || !Number.isFinite(form.value) || form.value <= 0 || (form.type === 'DISCOUNT_PERCENTAGE' && form.value > 100);
  const filtered=(definitions.data??[]).filter(value=>(!search.trim()||value.name.toLowerCase().includes(search.trim().toLowerCase()))&&(status==='ALL'||value.active===(status==='ACTIVE')));
  const conditions=(value:DiscountDefinition)=>[value.minimumPurchaseAmount!=null?`Min ${value.minimumPurchaseAmount.toFixed(2)}`:null,value.maximumPurchaseAmount!=null?`Max purchase ${value.maximumPurchaseAmount.toFixed(2)}`:null,value.maximumDiscountAmount!=null?`Max discount ${value.maximumDiscountAmount.toFixed(2)}`:null,value.minimumQuantity!=null?`Qty ${value.minimumQuantity}+`:null].filter(Boolean).join(' / ')||'None';
  const retailCategories=categories.data?.content??[];
  const foodCategories=foodReferences.flatMap((query,index)=>index%2===0?(query.data as import('../../api/types').FoodMenuCategory[]|undefined)??[]:[]);
  const foodItems=foodReferences.flatMap((query,index)=>index%2===1?(query.data as import('../../api/types').FoodMenuItem[]|undefined)??[]:[]);
  const categoryOptions=Array.from(new Map([...retailCategories,...foodCategories].map(value=>[value.id,value])).values());
  const productOptions=Array.from(new Map([...(products.data?.content??[]).map(value=>({id:value.id,name:value.name})),...foodItems.map(value=>({id:value.id,name:value.displayName}))].map(value=>[value.id,value])).values());

  if (!canView) return <Alert severity="error">DISCOUNT_VIEW is required.</Alert>;
  return <Stack spacing={2} sx={{ maxWidth: 1000, mx: 'auto' }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center"><Box><Typography variant="h4">Discounts</Typography><Typography color="text.secondary">Reusable Retail and Restaurant POS discounts.</Typography></Box><Button startIcon={<AddIcon />} variant="contained" disabled={!canManage} onClick={() => open(null)}>Add Discount</Button></Stack>
    <Stack direction="row" spacing={1}><TextField size="small" fullWidth label="Search discounts" value={search} onChange={event=>setSearch(event.target.value)}/><TextField select size="small" label="Status" value={status} onChange={event=>setStatus(event.target.value as typeof status)} sx={{minWidth:150}}><MenuItem value="ALL">All</MenuItem><MenuItem value="ACTIVE">Active</MenuItem><MenuItem value="INACTIVE">Inactive</MenuItem></TextField></Stack>
    {definitions.isError ? <Alert severity="error">Discounts could not be loaded.</Alert> : null}
    <TableContainer component={Paper} variant="outlined"><Table size="small"><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Type</TableCell><TableCell>Value</TableCell><TableCell>Conditions</TableCell><TableCell>Status</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
      {filtered.map(value => <TableRow key={value.id}><TableCell><Typography fontWeight={700}>{value.name}</Typography>{value.description ? <Typography variant="caption" color="text.secondary">{value.description}</Typography> : null}</TableCell><TableCell>{value.type === 'DISCOUNT_PERCENTAGE' ? 'Percentage' : 'Fixed'}</TableCell><TableCell>{value.type === 'DISCOUNT_PERCENTAGE' ? `${value.value}%` : Number(value.value).toFixed(2)}</TableCell><TableCell><Typography variant="caption">{conditions(value)}</Typography></TableCell><TableCell><Chip size="small" color={value.active ? 'success' : 'default'} label={value.active ? 'Active' : 'Inactive'} /></TableCell><TableCell align="right"><Button size="small" disabled={!canManage} onClick={() => open(value)}>Edit</Button><Button size="small" disabled={!canManage || toggle.isPending} onClick={() => toggle.mutate(value)}>{value.active ? 'Deactivate' : 'Activate'}</Button>{canDelete?<Button color="error" size="small" disabled={remove.isPending} onClick={()=>remove.mutate(value)}>Delete</Button>:null}</TableCell></TableRow>)}
      {definitions.isSuccess && !filtered.length ? <TableRow><TableCell colSpan={6}>No discounts match these filters.</TableCell></TableRow> : null}
    </TableBody></Table></TableContainer>
    <Dialog open={editing !== undefined} onClose={() => setEditing(undefined)} maxWidth={false} PaperProps={{ sx: { width:'calc(100vw - 24px)',maxWidth:500,m:1.5 } }}>
      <DialogTitle>{editing ? 'Edit Discount' : 'Add Discount'}</DialogTitle><DialogContent><Stack spacing={1.5} sx={{ pt:1 }}>
        <TextField size="small" autoFocus label="Discount name" value={form.name} onChange={event=>setForm({...form,name:event.target.value})} />
        <Stack direction="row" spacing={1}><Button fullWidth sx={{minHeight:42}} variant={form.type==='DISCOUNT_PERCENTAGE'?'contained':'outlined'} onClick={()=>setForm({...form,type:'DISCOUNT_PERCENTAGE'})}>Percentage</Button><Button fullWidth sx={{minHeight:42}} variant={form.type==='DISCOUNT_AMOUNT'?'contained':'outlined'} onClick={()=>setForm({...form,type:'DISCOUNT_AMOUNT'})}>Fixed Amount</Button></Stack>
        <TextField size="small" type="number" label={form.type==='DISCOUNT_PERCENTAGE'?'Percentage':'Fixed amount'} value={form.value || ''} inputProps={{min:.01,max:form.type==='DISCOUNT_PERCENTAGE'?100:undefined,step:.01}} onChange={event=>setForm({...form,value:Number(event.target.value)})} />
        <TextField size="small" label="Description (optional)" value={form.description} onChange={event=>setForm({...form,description:event.target.value})} />
        <Typography fontWeight={700}>Conditions</Typography>
        <Stack direction="row" spacing={1}><TextField fullWidth size="small" type="number" label="Minimum purchase" value={form.minimumPurchaseAmount??''} onChange={e=>setForm({...form,minimumPurchaseAmount:e.target.value?Number(e.target.value):undefined})}/><TextField fullWidth size="small" type="number" label="Maximum purchase" value={form.maximumPurchaseAmount??''} onChange={e=>setForm({...form,maximumPurchaseAmount:e.target.value?Number(e.target.value):undefined})}/></Stack>
        <Stack direction="row" spacing={1}><TextField fullWidth size="small" type="number" disabled={form.type!=='DISCOUNT_PERCENTAGE'} label="Maximum discount" value={form.maximumDiscountAmount??''} onChange={e=>setForm({...form,maximumDiscountAmount:e.target.value?Number(e.target.value):undefined})}/><TextField fullWidth size="small" type="number" label="Minimum quantity" value={form.minimumQuantity??''} onChange={e=>setForm({...form,minimumQuantity:e.target.value?Number(e.target.value):undefined})}/></Stack>
        <FormControl size="small"><InputLabel>Available stores</InputLabel><Select multiple label="Available stores" value={form.allStores?['__all__']:form.storeIds} renderValue={selected=>selected.includes('__all__')?'All Stores':`${selected.length} selected`} onChange={event=>{const values=event.target.value as string[];setForm({...form,allStores:values.includes('__all__'),storeIds:values.includes('__all__')?[]:values})}}><MenuItem value="__all__"><Checkbox checked={form.allStores}/><ListItemText primary="All Stores"/></MenuItem>{(stores.data?.content??[]).map(store=><MenuItem key={store.id} value={store.id}><Checkbox checked={form.storeIds.includes(store.id)}/><ListItemText primary={store.name}/></MenuItem>)}</Select></FormControl>
        <FormControl size="small"><InputLabel>Eligible categories</InputLabel><Select multiple label="Eligible categories" value={form.eligibleCategoryIds} renderValue={selected=>selected.length?`${selected.length} selected`:'All categories'} onChange={event=>setForm({...form,eligibleCategoryIds:event.target.value as string[]})}>{categoryOptions.map(value=><MenuItem key={value.id} value={value.id}><Checkbox checked={form.eligibleCategoryIds.includes(value.id)}/><ListItemText primary={value.name}/></MenuItem>)}</Select></FormControl>
        <FormControl size="small"><InputLabel>Eligible products / menu items</InputLabel><Select multiple label="Eligible products / menu items" value={form.eligibleProductIds} renderValue={selected=>selected.length?`${selected.length} selected`:'All products'} onChange={event=>setForm({...form,eligibleProductIds:event.target.value as string[]})}>{productOptions.map(value=><MenuItem key={value.id} value={value.id}><Checkbox checked={form.eligibleProductIds.includes(value.id)}/><ListItemText primary={value.name}/></MenuItem>)}</Select></FormControl>
        <Stack direction="row" spacing={1}><TextField fullWidth size="small" type="datetime-local" label="Starts" InputLabelProps={{shrink:true}} value={form.startsAt?.slice(0,16)??''} onChange={e=>setForm({...form,startsAt:e.target.value?new Date(e.target.value).toISOString():undefined})}/><TextField fullWidth size="small" type="datetime-local" label="Ends" InputLabelProps={{shrink:true}} value={form.endsAt?.slice(0,16)??''} onChange={e=>setForm({...form,endsAt:e.target.value?new Date(e.target.value).toISOString():undefined})}/></Stack>
        <FormControlLabel control={<Switch checked={form.active} onChange={(_,active)=>setForm({...form,active})} />} label="Active" />
        {save.isError ? <Alert severity="error">Discount could not be saved.</Alert> : null}{remove.isError?<Alert severity="error">This discount cannot be deleted because it has historical use.</Alert>:null}
      </Stack></DialogContent><DialogActions><Button onClick={()=>setEditing(undefined)}>Cancel</Button><Button variant="contained" disabled={invalid||save.isPending} onClick={()=>save.mutate()}>Save Discount</Button></DialogActions>
    </Dialog>
  </Stack>;
}
