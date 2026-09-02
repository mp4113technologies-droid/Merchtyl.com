import { describe, expect, it } from 'vitest';
import { ApiClientError, getApiErrorMessage, getApiFieldErrors } from './client';

describe('central API error resolver', () => {
  it.each([
    ['EMAIL_ALREADY_REGISTERED', 'This email address is already associated with another user. Please use a different email address.'],
    ['BARCODE_ALREADY_IN_USE', 'This barcode is already assigned to another product. Please enter a different barcode.'],
    ['PREVIOUS_BUSINESS_DAY_STILL_OPEN', "The previous business day is still open. Close it before opening today's business day."],
    ['REGISTER_ACCESS_DENIED', "You don't have access to this register."]
  ])('maps %s to business language', (code, expected) => {
    const error = new ApiClientError('raw technical message', 409, code);
    expect(getApiErrorMessage(error)).toBe(expected);
    expect(error.message).toBe(expected);
  });

  it('uses a safe fallback for unknown conflicts instead of raw backend text', () => {
    const error = new ApiClientError('duplicate key violates secret_constraint', 409, 'unknown_conflict');
    expect(getApiErrorMessage(error)).toBe("We couldn't complete this action because the information conflicts with the current state. Refresh and try again.");
  });

  it('uses a safe server message and includes correlation reference', () => {
    const error = new ApiClientError('relation secret_table does not exist', 500, 'database_error', 'support-123');
    expect(error.message).not.toContain('secret_table');
    expect(getApiErrorMessage(error, 'Something went wrong while saving.')).toBe('Something went wrong while saving. Reference: support-123');
  });

  it('preserves field violations and resolves known violation codes', () => {
    const error = new ApiClientError('invalid', 400, 'VALIDATION_FAILED', null, [
      { field: 'barcode', code: 'BARCODE_ALREADY_IN_USE', message: 'duplicate key' }
    ]);
    expect(getApiFieldErrors(error)).toEqual({
      barcode: 'This barcode is already assigned to another product. Please enter a different barcode.'
    });
  });
});
