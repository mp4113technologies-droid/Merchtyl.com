export const industryTypeOptions = [
  ['CONVENIENCE_STORE', 'Convenience Store'], ['GROCERY_STORE', 'Grocery Store'],
  ['RESTAURANT', 'Restaurant'], ['CAFE', 'Cafe / Coffee Shop'], ['FAST_FOOD', 'Fast Food / Takeout'],
  ['RETAIL', 'Retail Store'], ['PHARMACY', 'Pharmacy'], ['GAS_STATION', 'Gas Station / Convenience'],
  ['LIQUOR_STORE', 'Liquor Store'], ['BAKERY', 'Bakery'], ['SPECIALTY_FOOD', 'Specialty Food Store'],
  ['GENERAL_MERCHANDISE', 'General Merchandise'], ['OTHER', 'Other']
] as const;

export type IndustryType = typeof industryTypeOptions[number][0];
export const industryTypeLabel = (value?: string | null) =>
  industryTypeOptions.find(([code]) => code === value)?.[1] ?? 'Other';
