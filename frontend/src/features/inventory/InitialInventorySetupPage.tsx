import DownloadIcon from '@mui/icons-material/Download';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { Alert, Box, Button, Chip, CircularProgress, Divider, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { confirmInitialInventory, downloadInitialInventoryTemplate, listStores, validateInitialInventory } from '../../api/client';
import type { InitialInventoryResult, InitialInventoryValidation } from '../../api/types';
import { useSession } from '../../app/session';

const actionLabel: Record<string,string>={CREATE_PRODUCT:'New Product',REUSE_PRODUCT:'Existing Product',CREATE_VARIANT:'New Variant',REUSE_VARIANT:'Existing Variant',ASSIGN_PRODUCT_TO_STORE:'Assign to Store',CREATE_OPENING_INVENTORY:'Opening Inventory'};
const errorLabel=(value:string)=>value.replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase());

export function InitialInventorySetupPage(){
  const {currentUser,session,getValidAccessToken}=useSession();const permissions=currentUser?.permissions??[];const roles=currentUser?.roles??session?.roles??[];
  const allowed=roles.some(role=>['OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER'].includes(role))&&permissions.includes('INVENTORY_MANAGE')&&permissions.includes('PRODUCT_CREATE');
  const stores=useQuery({queryKey:['stores','initial-inventory'],queryFn:async()=>listStores(await getValidAccessToken(),{active:true,size:100}),enabled:allowed});
  const [storeId,setStoreId]=useState(()=>window.localStorage.getItem('merchtyl.activeStoreId')??'');const [file,setFile]=useState<File|null>(null);const [preview,setPreview]=useState<InitialInventoryValidation|null>(null);const [result,setResult]=useState<InitialInventoryResult|null>(null);
  const selected=stores.data?.content.find(store=>store.id===storeId);
  const download=useMutation({mutationFn:async()=>downloadInitialInventoryTemplate(await getValidAccessToken(),storeId),onSuccess:blob=>{const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=`Merchtyl-Initial-Inventory-${selected?.code??'Store'}.xlsx`;anchor.click();URL.revokeObjectURL(url);}});
  const validate=useMutation({mutationFn:async()=>{if(!file)throw new Error('Choose an XLSX file first.');return validateInitialInventory(await getValidAccessToken(),storeId,file);},onSuccess:data=>{setPreview(data);setResult(null);}});
  const confirm=useMutation({mutationFn:async()=>{if(!preview)throw new Error('Validate the workbook first.');return confirmInitialInventory(await getValidAccessToken(),storeId,preview.importId);},onSuccess:setResult});
  if(!allowed)return <Navigate to="/unauthorized" replace/>;
  if(result)return <Completion result={result} storeName={selected?.name??'Selected Store'}/>;
  return <Stack spacing={3} sx={{maxWidth:1280,minWidth:0}}>
    <Box><Typography variant="h4" component="h1">Initial Inventory Setup</Typography><Typography color="text.secondary">Quickly set up your Store catalog and opening inventory using our Excel template.</Typography></Box>
    <Paper variant="outlined" sx={{p:{xs:2,md:3}}}><Stack spacing={2.5}>
      <TextField select label="Store" value={storeId} onChange={e=>{setStoreId(e.target.value);setPreview(null);}} fullWidth>{(stores.data?.content??[]).map(store=><MenuItem key={store.id} value={store.id}>{store.name} ({store.code})</MenuItem>)}</TextField>
      <Divider/><Box><Typography variant="h6">1. Download Template</Typography><Typography color="text.secondary">Product References are created automatically. SKUs are generated when left blank.</Typography></Box>
      <Button variant="outlined" startIcon={<DownloadIcon/>} disabled={!storeId||download.isPending} onClick={()=>download.mutate()} sx={{alignSelf:'flex-start'}}>Download Excel Template</Button>
      <Divider/><Box><Typography variant="h6">2. Upload Completed Template</Typography><Typography color="text.secondary">One row represents one sellable Product Variant. Maximum file size: 10 MB.</Typography></Box>
      <Button component="label" variant="outlined" startIcon={<UploadFileIcon/>} sx={{alignSelf:'flex-start'}}>Choose XLSX File<input hidden type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={e=>{setFile(e.target.files?.[0]??null);setPreview(null);}}/></Button>
      {file?<Typography variant="body2">Selected: {file.name}</Typography>:null}
      <Button variant="contained" disabled={!storeId||!file||validate.isPending} onClick={()=>validate.mutate()} sx={{alignSelf:'flex-start'}}>{validate.isPending?<CircularProgress size={20}/>: 'Validate Inventory'}</Button>
      {download.isError||validate.isError?<Alert severity="error">{String((download.error??validate.error) instanceof Error?(download.error??validate.error)?.message:'Unable to process workbook.')}</Alert>:null}
    </Stack></Paper>
    {preview?<Preview value={preview} confirming={confirm.isPending} confirmError={confirm.error} onConfirm={()=>confirm.mutate()}/>:null}
  </Stack>;
}

function Preview({value,confirming,confirmError,onConfirm}:{value:InitialInventoryValidation;confirming:boolean;confirmError:unknown;onConfirm:()=>void}){return <Paper variant="outlined" sx={{p:{xs:2,md:3}}}><Stack spacing={2}>
  <Typography variant="h5">Initial Inventory Preview</Typography><Stack direction="row" spacing={1} useFlexGap flexWrap="wrap"><Chip label={`${value.productGroups} Products`}/><Chip label={`${value.totalVariantRows} Variants`}/><Chip color="success" label={`${value.validRows} Ready`}/><Chip color="warning" label={`${value.warningRows} Warnings`}/><Chip color={value.errorRows?'error':'default'} label={`${value.errorRows} Errors`}/></Stack>
  {value.errorRows?<Alert severity="error">Correct all workbook errors and validate again before importing.</Alert>:<Alert severity="success">Workbook is ready to import.</Alert>}
  <TableContainer sx={{maxHeight:480}}><Table stickyHeader size="small"><TableHead><TableRow>{['Row','Product Ref','Product','Variant','Barcode','SKU','Price','Opening Qty','Action','Status'].map(h=><TableCell key={h}>{h}</TableCell>)}</TableRow></TableHead><TableBody>{value.rows.map(row=><TableRow key={row.rowNumber}><TableCell>{row.rowNumber}</TableCell><TableCell>{row.productReference??'Generated on import'}</TableCell><TableCell>{row.productName}</TableCell><TableCell>{row.variantName}</TableCell><TableCell sx={{fontFamily:'monospace'}}>{row.barcode??'—'}</TableCell><TableCell sx={{fontFamily:'monospace'}}>{row.sku}</TableCell><TableCell>{row.sellingPrice}</TableCell><TableCell>{row.openingQuantity}</TableCell><TableCell>{row.actions.slice(0,2).map(a=><Chip key={a} size="small" label={actionLabel[a]??a} sx={{mr:.5,mb:.5}}/>)}</TableCell><TableCell>{row.errors.length?<Stack>{row.errors.map(e=><Typography key={e} color="error" variant="caption">Row {row.rowNumber}: {errorLabel(e)}</Typography>)}</Stack>:<Chip size="small" color="success" label="Ready"/>}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
  {confirmError?<Alert severity="error">{confirmError instanceof Error?confirmError.message:'Import failed.'}</Alert>:null}<Button variant="contained" disabled={!value.canImport||confirming} onClick={onConfirm} sx={{alignSelf:'flex-start'}}>Confirm Import</Button>
  </Stack></Paper>}

function Completion({result,storeName}:{result:InitialInventoryResult;storeName:string}){return <Stack spacing={3}><Box><Typography variant="h4" component="h1">Initial Inventory Setup Complete</Typography><Typography color="text.secondary">{storeName}</Typography></Box><Paper variant="outlined" sx={{p:3}}><Stack spacing={1}>{[['Products created',result.productsCreated],['Products reused',result.productsReused],['Variants created',result.variantsCreated],['Variants reused',result.variantsReused],['Store assignments',result.storeAssignments],['Opening inventory',result.openingInventory]].map(([label,value])=><Stack key={String(label)} direction="row" justifyContent="space-between"><Typography>{label}</Typography><Typography fontWeight={700}>{value}</Typography></Stack>)}</Stack></Paper><Stack direction={{xs:'column',sm:'row'}} spacing={2}><Button component={Link} to="/inventory" variant="contained">View Inventory</Button><Button component={Link} to="/products" variant="outlined">View Products</Button></Stack></Stack>}
