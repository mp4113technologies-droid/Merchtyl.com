import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PublicLandingPage } from './PublicLandingPage';

function response(body: unknown, status = 202) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

describe('PublicLandingPage', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('submits the supplied form contract, disables while pending, resets, and shows success', async () => {
    let resolveRequest: ((value: Response) => void) | undefined;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(resolve => { resolveRequest = resolve; }));
    render(<PublicLandingPage />);

    await userEvent.type(screen.getByLabelText('Full name *'), 'Ada Lovelace');
    await userEvent.type(screen.getByLabelText('Business name *'), 'Analytical Engines');
    await userEvent.type(screen.getByLabelText('Email *'), 'ada@example.test');
    await userEvent.selectOptions(screen.getByLabelText('Type of business *'), 'retail');
    await userEvent.click(screen.getByLabelText('Retail POS'));
    await userEvent.click(screen.getByRole('button', { name: /Request my quote/i }));

    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled();
    resolveRequest?.(await response({ success: true, message: 'Your message has been received.' }));
    expect(await screen.findByRole('heading', { name: /Thanks, Ada/i })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body).toMatchObject({
      name: 'Ada Lovelace', business: 'Analytical Engines', email: 'ada@example.test',
      businessType: 'retail', needs: ['Retail POS'], demo: false
    });

    await userEvent.click(screen.getByRole('button', { name: 'Send another request' }));
    expect(screen.getByRole('heading', { name: 'Request your quote' })).toBeInTheDocument();
    expect(screen.getByLabelText('Full name *')).toHaveValue('');
  });

  it('keeps entered values and shows a friendly error when delivery fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({ message: 'internal provider detail' }, 503));
    render(<PublicLandingPage />);

    await userEvent.type(screen.getByLabelText('Full name *'), 'Grace Hopper');
    await userEvent.type(screen.getByLabelText('Business name *'), 'Compiler Shop');
    await userEvent.type(screen.getByLabelText('Email *'), 'grace@example.test');
    await userEvent.selectOptions(screen.getByLabelText('Type of business *'), 'retail');
    await userEvent.click(screen.getByRole('button', { name: /Request my quote/i }));

    expect(await screen.findByText("We couldn't send your message right now. Please try again in a moment.")).toBeInTheDocument();
    expect(screen.getByLabelText('Full name *')).toHaveValue('Grace Hopper');
    expect(screen.queryByText('internal provider detail')).not.toBeInTheDocument();
  });

  it('provides responsive source sections and SEO metadata', async () => {
    render(<PublicLandingPage />);
    expect(document.querySelectorAll('section')).toHaveLength(8);
    expect(document.querySelector('meta[property="og:url"]')).toHaveAttribute('content', 'https://merchtyl.com/');
    await waitFor(() => expect(document.querySelector('link[rel="canonical"]')).toHaveAttribute('href', 'https://merchtyl.com/'));
  });
});
