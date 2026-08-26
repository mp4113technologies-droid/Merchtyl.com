import { z } from 'zod';

export const PASSWORD_POLICY_MESSAGE = 'Password must be between 8 and 20 characters and include at least one uppercase letter, one lowercase letter, one number, and one special character.';
export const PASSWORD_POLICY_HELP = `${PASSWORD_POLICY_MESSAGE} Allowed special characters: ! @ # $ % ^ & * ( ) _ + - = ? . ,`;

export const passwordValueSchema = z.string()
  .min(8, PASSWORD_POLICY_MESSAGE)
  .max(20, PASSWORD_POLICY_MESSAGE)
  .regex(/[A-Z]/, PASSWORD_POLICY_MESSAGE)
  .regex(/[a-z]/, PASSWORD_POLICY_MESSAGE)
  .regex(/[0-9]/, PASSWORD_POLICY_MESSAGE)
  .regex(/[!@#$%^&*()_+\-=?.,]/, PASSWORD_POLICY_MESSAGE)
  .regex(/^[A-Za-z0-9!@#$%^&*()_+\-=?.,]+$/, PASSWORD_POLICY_MESSAGE);

export function validPassword(password: string) {
  return passwordValueSchema.safeParse(password).success;
}
