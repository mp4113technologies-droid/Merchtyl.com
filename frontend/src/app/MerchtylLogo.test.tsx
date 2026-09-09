import { render, screen } from '@testing-library/react';
import { MerchtylLogo } from './MerchtylLogo';

describe('MerchtylLogo', () => {
  it('uses the official primary wordmark by default', () => {
    render(<MerchtylLogo />);
    expect(screen.getByRole('img', { name: 'Merchtyl' })).toHaveAttribute('src', '/branding/Full main.svg');
  });

  it('uses the official black wordmark for monochrome contexts', () => {
    render(<MerchtylLogo variant="black" />);
    expect(screen.getByRole('img', { name: 'Merchtyl' })).toHaveAttribute('src', '/branding/Full black.svg');
  });

  it('can be hidden from accessibility APIs when decorative', () => {
    const { container } = render(<MerchtylLogo variant="icon" decorative />);
    expect(container.querySelector('img')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
