import type { UserAdminSearchParams } from '../../api/client';

export const merchantUserKeys = {
  all: ['merchant-users'] as const,
  lists: () => [...merchantUserKeys.all, 'list'] as const,
  list: (params: UserAdminSearchParams) => [...merchantUserKeys.lists(), params] as const,
  detail: (id: string | undefined) => [...merchantUserKeys.all, 'detail', id] as const,
  assignableStores: (scope: string) => [...merchantUserKeys.all, 'assignable-stores', scope] as const
};
