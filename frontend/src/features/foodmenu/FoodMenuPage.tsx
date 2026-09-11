import AddIcon from '@mui/icons-material/Add';
import { Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { createFoodMenuCategory, createFoodMenuItem, deleteFoodMenuCategory, deleteFoodMenuItem, listFoodMenuCategories, listFoodMenuItems, listProducts, listStores, updateFoodMenuCategory, updateFoodMenuItem, updateFoodMenuItemAvailability } from '../../api/client';
import type { FoodMenuItem } from '../../api/types';
import { useSession } from '../../app/session';

const emptyItem = { productId: '', categoryId: '', displayName: '', description: '', price: 0, displayOrder: 0, available: true, imageUrl: '', variants: [] as Array<{name:string;price:number;displayOrder:number;available:boolean}>, modifierGroups: [] as Array<{name:string;minimumSelections:number;maximumSelections:number;displayOrder:number;options:Array<{name:string;priceAdjustment:number;displayOrder:number;available:boolean}>}>, components: [] as Array<{clientId:string;name:string;includedByDefault:boolean;removable:boolean;allowExtra:boolean;extraPrice:number;displayOrder:number;active:boolean}> };

export function FoodMenuPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const stores = useQuery({ queryKey: ['stores', 'restaurant-menu'], queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }) });
  const eligible = (stores.data?.content ?? []).filter((store) => store.capabilities?.includes('FOOD_SERVICE'));
  const [storeId, setStoreId] = React.useState('');
  const selected = storeId || eligible[0]?.id || '';
  const categories = useQuery({ queryKey: ['food-menu-categories', selected], queryFn: async () => listFoodMenuCategories(await getValidAccessToken(), selected), enabled: Boolean(selected) });
  const items = useQuery({ queryKey: ['food-menu-items', selected], queryFn: async () => listFoodMenuItems(await getValidAccessToken(), selected), enabled: Boolean(selected) });
  const products = useQuery({ queryKey: ['products', 'restaurant-menu', selected], queryFn: async () => listProducts(await getValidAccessToken(), { storeId: selected, active: true, size: 200 }), enabled: Boolean(selected) });
  const [categoryOpen, setCategoryOpen] = React.useState(false);
  const [itemOpen, setItemOpen] = React.useState(false);
  const [linkProduct, setLinkProduct] = React.useState(false);
  const [category, setCategory] = React.useState({ name: '', displayOrder: 0, active: true, imageUrl: '' });
  const [item, setItem] = React.useState(emptyItem);
  const canManage = currentUser?.permissions?.some((permission) => permission === 'PRODUCT_MANAGE' || permission === 'FOOD_ORDER_UPDATE');
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['food-menu-categories', selected] });
    void queryClient.invalidateQueries({ queryKey: ['food-menu-items', selected] });
  };
  const addCategory = useMutation({ mutationFn: async () => createFoodMenuCategory(await getValidAccessToken(), selected, category), onSuccess: () => { setCategoryOpen(false); setCategory({ name: '', displayOrder: 0, active: true, imageUrl: '' }); refresh(); } });
  const addItem = useMutation({ mutationFn: async () => createFoodMenuItem(await getValidAccessToken(), selected, { ...item, productId: linkProduct ? item.productId : undefined,components:item.components.map(({clientId:_,...value})=>value) }), onSuccess: () => { setItemOpen(false); setItem(emptyItem); setLinkProduct(false); refresh(); } });
  const availability = useMutation({ mutationFn: async (value: { id: string; available: boolean }) => updateFoodMenuItemAvailability(await getValidAccessToken(), selected, value.id, value.available), onSuccess: refresh });
  const changeCategory = useMutation({ mutationFn: async (value: { id: string; name: string; displayOrder: number; active: boolean; imageUrl?: string }) => updateFoodMenuCategory(await getValidAccessToken(), selected, value.id, value), onSuccess: refresh });
  const moveItem = useMutation({ mutationFn: async (value: FoodMenuItem) => updateFoodMenuItem(await getValidAccessToken(), selected, value.id, { productId: value.productId ?? undefined, categoryId: value.categoryId, displayName: value.displayName, description: value.description ?? undefined, price: value.price, displayOrder: value.displayOrder, available: value.available, imageUrl: value.imageUrl ?? undefined, variants:value.variants??[], modifierGroups:value.modifierGroups??[],components:value.components??[] }), onSuccess: refresh });
  const removeCategory = useMutation({ mutationFn: async (id: string) => deleteFoodMenuCategory(await getValidAccessToken(), selected, id), onSuccess: refresh });
  const removeItem = useMutation({ mutationFn: async (id: string) => deleteFoodMenuItem(await getValidAccessToken(), selected, id), onSuccess: refresh });

  if (stores.isSuccess && !eligible.length) return <Alert severity="info">No FOOD_SERVICE-enabled stores are available.</Alert>;
  return <Stack spacing={3} sx={{ minWidth: 0 }}>
    <Box><Typography variant="h4">Restaurant Menu</Typography><Typography color="text.secondary">Made-to-order items are separate from Retail Products and Inventory.</Typography></Box>
    <TextField select label="Store" value={selected} onChange={(event) => setStoreId(event.target.value)} sx={{ maxWidth: 420 }}>{eligible.map((store) => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}</TextField>
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap"><Button startIcon={<AddIcon />} variant="contained" disabled={!selected || !canManage} onClick={() => setCategoryOpen(true)}>Add Category</Button><Button startIcon={<AddIcon />} variant="contained" disabled={!selected || !categories.data?.length || !canManage} onClick={() => setItemOpen(true)}>Add Menu Item</Button></Stack>
    {categories.isSuccess && !categories.data.length ? <Alert severity="info" action={canManage ? <Button onClick={() => setCategoryOpen(true)}>Create Restaurant Menu</Button> : undefined}>No restaurant menu has been created for this store yet.</Alert> : null}
    {(categories.data ?? []).map((group) => <Box key={group.id} sx={{ minWidth: 0 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1 }}><Typography variant="h5" sx={{ flex: 1 }}>{group.name} <Typography component="span" color="text.secondary">#{group.displayOrder}{group.active ? '' : ' · Inactive'}</Typography></Typography><Stack direction="row" useFlexGap flexWrap="wrap"><Button size="small" disabled={!canManage} onClick={() => changeCategory.mutate({ ...group, displayOrder: Math.max(0, group.displayOrder - 1), imageUrl: group.imageUrl ?? undefined })}>Move Up</Button><Button size="small" disabled={!canManage} onClick={() => changeCategory.mutate({ ...group, displayOrder: group.displayOrder + 1, imageUrl: group.imageUrl ?? undefined })}>Move Down</Button><Button size="small" disabled={!canManage} onClick={() => changeCategory.mutate({ ...group, active: !group.active, imageUrl: group.imageUrl ?? undefined })}>{group.active ? 'Deactivate' : 'Activate'}</Button><Button size="small" color="error" disabled={!canManage} onClick={() => removeCategory.mutate(group.id)}>Delete</Button></Stack></Stack>
      <Grid container spacing={2}>{(items.data ?? []).filter((value) => value.categoryId === group.id).map((value) => <Grid item xs={12} sm={6} md={4} key={value.id}><Card variant="outlined"><CardContent><Stack spacing={1}><Typography variant="h6">{value.displayName}</Typography>{value.description ? <Typography variant="body2">{value.description}</Typography> : null}<Typography>${Number(value.price).toFixed(2)} · position {value.displayOrder}</Typography><Typography variant="caption">{value.madeToOrder ? 'Made to Order · No inventory tracking' : `Linked Product: ${value.productName}`}</Typography><FormControlLabel control={<Switch checked={value.available} disabled={!canManage || availability.isPending} onChange={(_, checked) => availability.mutate({ id: value.id, available: checked })} />} label={value.available ? 'Available' : 'Sold Out'} /><Stack direction="row" useFlexGap flexWrap="wrap"><Button size="small" disabled={!canManage} onClick={() => moveItem.mutate({ ...value, displayOrder: Math.max(0, value.displayOrder - 1) })}>Move Up</Button><Button size="small" disabled={!canManage} onClick={() => moveItem.mutate({ ...value, displayOrder: value.displayOrder + 1 })}>Move Down</Button><Button size="small" color="error" disabled={!canManage} onClick={() => removeItem.mutate(value.id)}>Remove</Button></Stack></Stack></CardContent></Card></Grid>)}</Grid>
    </Box>)}
    <Dialog open={categoryOpen} onClose={() => setCategoryOpen(false)} fullWidth maxWidth="sm"><DialogTitle>Create Restaurant Menu Category</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField label="Name" value={category.name} onChange={(event) => setCategory({ ...category, name: event.target.value })} /><TextField label="Display Order" type="number" value={category.displayOrder} onChange={(event) => setCategory({ ...category, displayOrder: Number(event.target.value) })} /></Stack></DialogContent><DialogActions><Button onClick={() => setCategoryOpen(false)}>Cancel</Button><Button onClick={() => addCategory.mutate()} disabled={!category.name.trim()}>Add</Button></DialogActions></Dialog>
    <Dialog open={itemOpen} onClose={() => setItemOpen(false)} fullWidth maxWidth="md"><DialogTitle>Add Restaurant Menu Item</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><FormControlLabel control={<Switch checked={!linkProduct} onChange={(_, madeToOrder) => setLinkProduct(!madeToOrder)} />} label="Made to Order / No Inventory Tracking" />{linkProduct ? <TextField select label="Link Existing Product" value={item.productId} onChange={(event) => { const product = products.data?.content.find((candidate) => candidate.id === event.target.value); setItem({ ...item, productId: event.target.value, displayName: product?.name ?? '', price: product?.price ?? 0 }); }}>{(products.data?.content ?? []).filter((product) => product.capabilities.includes('FOOD_SERVICE')).map((product) => <MenuItem key={product.id} value={product.id}>{product.name}</MenuItem>)}</TextField> : null}<TextField select label="Restaurant Category" value={item.categoryId} onChange={(event) => setItem({ ...item, categoryId: event.target.value })}>{(categories.data ?? []).map((value) => <MenuItem key={value.id} value={value.id}>{value.name}</MenuItem>)}</TextField><TextField label="Name" value={item.displayName} onChange={(event) => setItem({ ...item, displayName: event.target.value })} /><TextField label="Description (optional)" multiline minRows={2} value={item.description} onChange={(event) => setItem({ ...item, description: event.target.value })} /><TextField label="Price" type="number" value={item.price} onChange={(event) => setItem({ ...item, price: Number(event.target.value) })} /><TextField label="Tile Position / Display Order" type="number" value={item.displayOrder} onChange={(event) => setItem({ ...item, displayOrder: Number(event.target.value) })} /><TextField label="Image URL (optional)" value={item.imageUrl} onChange={(event) => setItem({ ...item, imageUrl: event.target.value })} /><Typography fontWeight={800}>Included ingredients</Typography>{item.components.map(component=><Stack key={component.clientId} direction={{xs:'column',md:'row'}} spacing={1}><TextField label="Ingredient name" value={component.name} onChange={event=>setItem(current=>({...current,components:current.components.map(value=>value.clientId===component.clientId?{...value,name:event.target.value}:value)}))}/><FormControlLabel control={<Switch checked={component.removable} onChange={(_,checked)=>setItem(current=>({...current,components:current.components.map(value=>value.clientId===component.clientId?{...value,removable:checked}:value)}))}/>} label="Removable"/><FormControlLabel control={<Switch checked={component.allowExtra} onChange={(_,checked)=>setItem(current=>({...current,components:current.components.map(value=>value.clientId===component.clientId?{...value,allowExtra:checked}:value)}))}/>} label="Allow extra"/>{component.allowExtra?<TextField label="Extra price" type="number" value={component.extraPrice} onChange={event=>setItem(current=>({...current,components:current.components.map(value=>value.clientId===component.clientId?{...value,extraPrice:Number(event.target.value)}:value)}))}/>:null}<Button color="error" onClick={()=>setItem(current=>({...current,components:current.components.filter(value=>value.clientId!==component.clientId)}))}>Remove</Button></Stack>)}<Button onClick={()=>setItem(current=>({...current,components:[...current.components,{clientId:newRowId('new-component'),name:'',includedByDefault:true,removable:true,allowExtra:false,extraPrice:0,displayOrder:current.components.length,active:true}]}))}>Add Ingredient</Button></Stack></DialogContent><DialogActions><Button onClick={() => setItemOpen(false)}>Cancel</Button><Button onClick={() => addItem.mutate()} disabled={!item.categoryId || !item.displayName.trim() || item.components.some(value=>!value.name.trim()||value.extraPrice<0) || (linkProduct && !item.productId)}>Add</Button></DialogActions></Dialog>
    {canManage && selected ? <MenuConfigurationEditor items={items.data ?? []} save={async value => { await updateFoodMenuItem(await getValidAccessToken(), selected, value.id, { productId:value.productId??undefined,categoryId:value.categoryId,displayName:value.displayName,description:value.description??undefined,price:value.price,displayOrder:value.displayOrder,available:value.available,imageUrl:value.imageUrl??undefined,variants:value.variants??[],modifierGroups:value.modifierGroups??[],components:value.components??[] }); refresh(); }} /> : null}
  </Stack>;
}

function newRowId(prefix: string) {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`;
}

function MenuConfigurationEditor({items,save}:{items:FoodMenuItem[];save:(item:FoodMenuItem)=>Promise<void>}) {
  const [selected,setSelected]=React.useState('');
  const [draft,setDraft]=React.useState<FoodMenuItem|null>(null);

  React.useEffect(() => {
    const selectedItem = items.find(value => value.id === selected);
    setDraft(selectedItem ? structuredClone(selectedItem) : null);
    // Form initialization intentionally follows selection only. Query refreshes and local edits must not reset it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected]);

  if(!items.length)return null;
  return <Card variant="outlined"><CardContent><Stack spacing={2}>
    <Typography variant="h5">Variants, included ingredients & modifiers</Typography>
    <TextField select label="Menu item" value={selected} onChange={event=>setSelected(event.target.value)}>{items.map(value=><MenuItem key={value.id} value={value.id}>{value.displayName}</MenuItem>)}</TextField>
    {draft?<>
      <Typography fontWeight={800}>Variants</Typography>
      {(draft.variants??[]).map(variant=><Stack direction={{xs:'column',sm:'row'}} spacing={1} key={variant.id} data-testid={`variant-row-${variant.id}`}>
        <TextField label="Variant name" value={variant.name} onChange={event=>setDraft(current=>current?{...current,variants:(current.variants??[]).map(value=>value.id===variant.id?{...value,name:event.target.value}:value)}:current)}/>
        <TextField label="Variant price" type="number" value={variant.price} onChange={event=>setDraft(current=>current?{...current,variants:(current.variants??[]).map(value=>value.id===variant.id?{...value,price:Number(event.target.value)}:value)}:current)}/>
        <Button color="error" onClick={()=>setDraft(current=>current?{...current,variants:(current.variants??[]).filter(value=>value.id!==variant.id)}:current)}>Remove Variant</Button>
      </Stack>)}
      <Button onClick={()=>setDraft(current=>current?{...current,variants:[...(current.variants??[]),{id:newRowId('variant'),name:'',price:0,displayOrder:current.variants?.length??0,available:true}]}:current)}>Add Variant</Button>

      <Typography fontWeight={800}>Included ingredients / components</Typography>
      {(draft.components??[]).map(component=><Card key={component.id} variant="outlined"><CardContent><Stack direction={{xs:'column',md:'row'}} spacing={1} alignItems={{md:'center'}}>
        <TextField label="Ingredient name" value={component.name} onChange={event=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,name:event.target.value}:value)}:current)}/>
        <FormControlLabel control={<Switch checked={component.removable} onChange={(_,checked)=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,removable:checked}:value)}:current)}/>} label="Removable" />
        <FormControlLabel control={<Switch checked={component.allowExtra} onChange={(_,checked)=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,allowExtra:checked,extraPrice:checked?value.extraPrice:0}:value)}:current)}/>} label="Allow extra" />
        {component.allowExtra?<TextField label="Extra price" type="number" value={component.extraPrice} onChange={event=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,extraPrice:Number(event.target.value)}:value)}:current)}/>:null}
        <TextField label="Order" type="number" value={component.displayOrder} onChange={event=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,displayOrder:Number(event.target.value)}:value)}:current)} sx={{width:90}}/>
        <FormControlLabel control={<Switch checked={component.active} onChange={(_,checked)=>setDraft(current=>current?{...current,components:(current.components??[]).map(value=>value.id===component.id?{...value,active:checked}:value)}:current)}/>} label="Active" />
      </Stack></CardContent></Card>)}
      <Button onClick={()=>setDraft(current=>current?{...current,components:[...(current.components??[]),{id:newRowId('component'),name:'',includedByDefault:true,removable:true,allowExtra:false,extraPrice:0,displayOrder:current.components?.length??0,active:true}]}:current)}>Add Ingredient</Button>

      <Typography fontWeight={800}>Modifier groups</Typography>
      {(draft.modifierGroups??[]).map(group=><Card key={group.id} variant="outlined"><CardContent><Stack spacing={1.5}>
        <Stack direction={{xs:'column',sm:'row'}} spacing={1}>
          <TextField fullWidth label="Modifier group name" value={group.name} onChange={event=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).map(value=>value.id===group.id?{...value,name:event.target.value}:value)}:current)}/>
          <Button color="error" onClick={()=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).filter(value=>value.id!==group.id)}:current)}>Remove Group</Button>
        </Stack>
        {group.options.map(option=><Stack direction={{xs:'column',sm:'row'}} spacing={1} key={option.id} data-testid={`modifier-row-${option.id}`}>
          <TextField label="Modifier name" value={option.name} onChange={event=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).map(value=>value.id===group.id?{...value,options:value.options.map(candidate=>candidate.id===option.id?{...candidate,name:event.target.value}:candidate)}:value)}:current)}/>
          <TextField label="Added price" type="number" value={option.priceAdjustment} onChange={event=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).map(value=>value.id===group.id?{...value,options:value.options.map(candidate=>candidate.id===option.id?{...candidate,priceAdjustment:Number(event.target.value)}:candidate)}:value)}:current)}/>
          <Button color="error" onClick={()=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).map(value=>value.id===group.id?{...value,options:value.options.filter(candidate=>candidate.id!==option.id)}:value)}:current)}>Remove Modifier</Button>
        </Stack>)}
        <Button onClick={()=>setDraft(current=>current?{...current,modifierGroups:(current.modifierGroups??[]).map(value=>value.id===group.id?{...value,options:[...value.options,{id:newRowId('modifier'),name:'',priceAdjustment:0,displayOrder:value.options.length,available:true}]}:value)}:current)}>Add Modifier</Button>
      </Stack></CardContent></Card>)}
      <Button onClick={()=>setDraft(current=>current?{...current,modifierGroups:[...(current.modifierGroups??[]),{id:newRowId('modifier-group'),name:'',minimumSelections:0,maximumSelections:20,displayOrder:current.modifierGroups?.length??0,options:[]}]}:current)}>Add Modifier Group</Button>
      <Button variant="contained" disabled={(draft.variants??[]).some(value=>!value.name.trim())||(draft.components??[]).some(value=>!value.name.trim()||value.extraPrice<0)||(draft.modifierGroups??[]).some(group=>!group.name.trim()||group.options.some(option=>!option.name.trim()))} onClick={()=>void save(draft)}>Save configuration</Button>
    </>:null}
  </Stack></CardContent></Card>;
}
