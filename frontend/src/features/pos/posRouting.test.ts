import { activeSessionAllowsPos, posRouteForRegisterType } from './posRouting';

describe('POS register routing', () => {
  it('maps only known register types to POS routes', () => {
    expect(posRouteForRegisterType('RETAIL')).toBe('/pos');
    expect(posRouteForRegisterType('FOOD_SERVICE')).toBe('/pos/food');
    expect(posRouteForRegisterType(undefined)).toBeNull();
    expect(posRouteForRegisterType('UNKNOWN')).toBeNull();
  });

  it('allows both modes without a session and only the matching mode with one', () => {
    expect(activeSessionAllowsPos(null, 'RETAIL')).toBe(true);
    expect(activeSessionAllowsPos(null, 'FOOD_SERVICE')).toBe(true);
    expect(activeSessionAllowsPos(undefined, 'RETAIL')).toBe(false);
    expect(activeSessionAllowsPos('RETAIL', 'RETAIL')).toBe(true);
    expect(activeSessionAllowsPos('RETAIL', 'FOOD_SERVICE')).toBe(false);
    expect(activeSessionAllowsPos('FOOD_SERVICE', 'FOOD_SERVICE')).toBe(true);
    expect(activeSessionAllowsPos('FOOD_SERVICE', 'RETAIL')).toBe(false);
  });
});
