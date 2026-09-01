import { describe, expect, it } from 'vitest';
import { ApiClientError, getApiErrorMessage, getApiFieldErrors } from './client';

describe('central API error mapping', () => {
  it('maps backend violations to form fields', () => {
    const error = new ApiClientError('Duplicate tenant', 409, 'TENANT_CODE_ALREADY_EXISTS', 'corr-1', [
      { field: 'tenantCode', code: 'TENANT_CODE_ALREADY_EXISTS', message: 'Tenant code is already in use' }
    ]);

    expect(getApiFieldErrors(error)).toEqual({ tenantCode: 'Tenant code is already in use' });
    expect(getApiErrorMessage(error)).toBe('Duplicate tenant');
  });

  it('sanitizes server failures and retains the correlation reference', () => {
    const error = new ApiClientError('raw internal message', 500, 'database_error', 'corr-500');

    expect(getApiErrorMessage(error, 'Something went wrong while saving the merchant.'))
      .toBe('Something went wrong while saving the merchant. Reference: corr-500');
  });
});
