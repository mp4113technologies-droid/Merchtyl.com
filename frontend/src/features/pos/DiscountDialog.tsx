import CloseIcon from '@mui/icons-material/Close';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, InputAdornment, Stack, TextField } from '@mui/material';
import * as React from 'react';

export type OrderDiscount = { definitionId?: string; name: string; type: 'DISCOUNT_PERCENTAGE' | 'DISCOUNT_AMOUNT'; value: number; reason: string };
const money=(value:number,currency:string)=>new Intl.NumberFormat(undefined,{style:'currency',currency}).format(value);
export function DiscountDialog({open,initial,currencyCode,onClose,onApply}:{open:boolean;initial:OrderDiscount|null;currencyCode:string;onClose:()=>void;onApply:(value:OrderDiscount)=>void}){
 const [type,setType]=React.useState<OrderDiscount['type']>('DISCOUNT_PERCENTAGE');const [value,setValue]=React.useState('');const [reason,setReason]=React.useState('');
 React.useEffect(()=>{if(open){setType(initial?.type??'DISCOUNT_PERCENTAGE');setValue(initial?String(initial.value):'');setReason(initial?.reason??'');}},[initial,open]);
 const numeric=Number(value);const invalid=!Number.isFinite(numeric)||numeric<=0||(type==='DISCOUNT_PERCENTAGE'&&numeric>100);
 return <Dialog open={open} onClose={onClose} maxWidth={false} PaperProps={{'data-testid':'discount-dialog-paper',sx:{width:'calc(100vw - 24px)',maxWidth:500,m:1.5,overflow:'hidden'}}}>
  <DialogTitle sx={{px:3,py:2,fontSize:'1.25rem'}}><Stack direction="row" justifyContent="space-between" alignItems="center">Apply Discount<IconButton aria-label="Close discount dialog" onClick={onClose} size="small"><CloseIcon fontSize="small"/></IconButton></Stack></DialogTitle>
  <DialogContent sx={{px:3,py:'8px !important'}}><Stack spacing={1.5}><Stack direction="row" spacing={1}><Button fullWidth sx={{minHeight:42}} variant={type==='DISCOUNT_PERCENTAGE'?'contained':'outlined'} onClick={()=>setType('DISCOUNT_PERCENTAGE')}>Percentage</Button><Button fullWidth sx={{minHeight:42}} variant={type==='DISCOUNT_AMOUNT'?'contained':'outlined'} onClick={()=>setType('DISCOUNT_AMOUNT')}>Fixed Amount</Button></Stack><TextField autoFocus size="small" type="number" label={type==='DISCOUNT_PERCENTAGE'?'Percentage':'Discount amount'} placeholder={type==='DISCOUNT_PERCENTAGE'?'10':money(0,currencyCode)} value={value} onChange={e=>setValue(e.target.value)} inputProps={{min:.01,max:type==='DISCOUNT_PERCENTAGE'?100:undefined,step:.01}} InputProps={{endAdornment:<InputAdornment position="end">{type==='DISCOUNT_PERCENTAGE'?'%':currencyCode}</InputAdornment>}}/><TextField size="small" label="Reason (optional)" value={reason} onChange={e=>setReason(e.target.value)}/></Stack></DialogContent>
  <DialogActions sx={{px:3,py:2}}><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={invalid} onClick={()=>onApply({name:'Custom Discount',type,value:numeric,reason:reason.trim()})}>Apply Discount</Button></DialogActions>
 </Dialog>;
}
