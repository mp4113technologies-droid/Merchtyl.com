import { describe, expect, it } from 'vitest';
import { validPassword } from './passwordPolicy';

describe('password policy', () => {
  it.each(['Test@123', 'Merchant@1', 'Admin#2026'])('accepts %s', (password) => {
    expect(validPassword(password)).toBe(true);
  });

  it.each([
    'test123',
    'TEST@123',
    'TestPassword',
    'Test1234',
    'Ab1!',
    'VeryLongPasswordForMerchtyl@12345',
    'Test/123'
  ])('rejects %s', (password) => {
    expect(validPassword(password)).toBe(false);
  });
});
