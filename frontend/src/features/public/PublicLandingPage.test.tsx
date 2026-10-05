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

  const countries = [
    { code: 'CA', name: 'Canada', regionLabel: 'Province / Territory' },
    { code: 'US', name: 'United States', regionLabel: 'State' }
  ];
  const regions = {
    CA: [{ code: 'NB', name: 'New Brunswick' }, { code: 'BC', name: 'British Columbia' }],
    US: [{ code: 'NY', name: 'New York' }, { code: 'CA', name: 'California' }]
  };

  function mockApi(contact: () => Promise<Response>) {
    return vi.spyOn(globalThis, 'fetch').mockImplementation(input => {
      const url = String(input);
      if (url.includes('/public/geography/countries/CA/regions')) return response(regions.CA, 200);
      if (url.includes('/public/geography/countries/US/regions')) return response(regions.US, 200);
      if (url.includes('/public/geography/countries')) return response(countries, 200);
      return contact();
    });
  }

  it('submits the supplied form contract, disables while pending, resets, and shows success', async () => {
    let resolveRequest: ((value: Response) => void) | undefined;
    const fetchMock = mockApi(() => new Promise(resolve => { resolveRequest = resolve; }));
    render(<PublicLandingPage />);

    await userEvent.selectOptions(await screen.findByLabelText('Country *'), 'CA');
    await userEvent.selectOptions(await screen.findByLabelText('Province / Territory *'), 'NB');

    await userEvent.type(screen.getByLabelText('Full name *'), 'Ada Lovelace');
    await userEvent.type(screen.getByLabelText('Business name *'), 'Analytical Engines');
    await userEvent.type(screen.getByLabelText('Email *'), 'ada@example.test');
    await userEvent.selectOptions(screen.getByLabelText('Type of business *'), 'retail');
    await userEvent.click(screen.getByLabelText('Retail POS'));
    await userEvent.click(screen.getByRole('button', { name: /Request my quote/i }));

    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled();
    resolveRequest?.(await response({ success: true, message: 'Your message has been received.' }));
    expect(await screen.findByRole('heading', { name: /Thanks, Ada/i })).toBeInTheDocument();
    const contactCalls = fetchMock.mock.calls.filter(call => String(call[0]).includes('/public/contact'));
    expect(contactCalls).toHaveLength(1);
    const body = JSON.parse(String(contactCalls[0][1]?.body));
    expect(body).toMatchObject({
      name: 'Ada Lovelace', business: 'Analytical Engines', email: 'ada@example.test',
      businessType: 'retail', countryCode: 'CA', regionCode: 'NB', needs: ['Retail POS'], demo: false
    });
    expect(body).not.toHaveProperty('province');

    await userEvent.click(screen.getByRole('button', { name: 'Send another request' }));
    expect(screen.getByRole('heading', { name: 'Request your quote' })).toBeInTheDocument();
    expect(screen.getByLabelText('Full name *')).toHaveValue('');
  });

  it('keeps entered values and shows a friendly error when delivery fails', async () => {
    mockApi(() => response({ message: 'internal provider detail' }, 503));
    render(<PublicLandingPage />);

    await userEvent.selectOptions(await screen.findByLabelText('Country *'), 'CA');
    await userEvent.selectOptions(await screen.findByLabelText('Province / Territory *'), 'NB');

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
    mockApi(() => response({}, 202));
    render(<PublicLandingPage />);
    expect(document.querySelectorAll('section')).toHaveLength(8);
    expect(document.querySelector('meta[property="og:url"]')).toHaveAttribute('content', 'https://merchtyl.com/');
    await waitFor(() => expect(document.querySelector('link[rel="canonical"]')).toHaveAttribute('href', 'https://merchtyl.com/'));
  });

  it('loads only supported countries and resets regions when country changes', async () => {
    mockApi(() => response({}, 202));
    render(<PublicLandingPage />);

    const country = await screen.findByLabelText('Country *');
    expect(country).toHaveTextContent('Canada');
    expect(country).toHaveTextContent('United States');
    expect(country.querySelectorAll('option')).toHaveLength(3);

    await userEvent.selectOptions(country, 'CA');
    const canadianRegion = await screen.findByLabelText('Province / Territory *');
    expect(canadianRegion).toHaveTextContent('New Brunswick');
    expect(canadianRegion).toHaveTextContent('British Columbia');
    await userEvent.selectOptions(canadianRegion, 'NB');

    await userEvent.selectOptions(country, 'US');
    const usRegion = await screen.findByLabelText('State *');
    await waitFor(() => expect(usRegion).toHaveTextContent('New York'));
    expect(usRegion).toHaveTextContent('California');
    expect(usRegion).toHaveValue('');
  });

  it('requires country and region before submission', async () => {
    const fetchMock = mockApi(() => response({}, 202));
    render(<PublicLandingPage />);
    await screen.findByLabelText('Country *');
    await userEvent.type(screen.getByLabelText('Full name *'), 'Ada Lovelace');
    await userEvent.type(screen.getByLabelText('Business name *'), 'Analytical Engines');
    await userEvent.type(screen.getByLabelText('Email *'), 'ada@example.test');
    await userEvent.selectOptions(screen.getByLabelText('Type of business *'), 'retail');
    await userEvent.click(screen.getByRole('button', { name: /Request my quote/i }));
    expect(fetchMock.mock.calls.filter(call => String(call[0]).includes('/public/contact'))).toHaveLength(0);
  });
});
