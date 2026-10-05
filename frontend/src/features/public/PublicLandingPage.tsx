import { useEffect, useRef, useState } from 'react';
import { getPublicCountries, getPublicRegions, submitPublicContact } from '../../api/client';
import type { PublicContactPayload, PublicCountry } from '../../api/types';
import landingMarkup from './landingPageMarkup.html?raw';
import './publicLandingPage.css';

type SubmitState = 'idle' | 'submitting' | 'success' | 'error';

const pageTitle = 'Merchtyl — Retail and restaurant POS for Canada and the United States';
const pageDescription = 'One system for merchants across Canada and the United States: retail, restaurant, lottery, inventory, registers, and end-of-day reporting.';

function setMeta(selector: string, attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(selector);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, key);
    document.head.appendChild(element);
  }
  element.content = content;
}

function formPayload(form: HTMLFormElement): PublicContactPayload {
  const values = new FormData(form);
  return {
    name: String(values.get('name') ?? '').trim(),
    business: String(values.get('business') ?? '').trim(),
    email: String(values.get('email') ?? '').trim(),
    phone: String(values.get('phone') ?? '').trim() || undefined,
    businessType: String(values.get('business_type') ?? '') as PublicContactPayload['businessType'],
    countryCode: String(values.get('countryCode') ?? '') as PublicContactPayload['countryCode'],
    regionCode: String(values.get('regionCode') ?? '').trim(),
    stores: (String(values.get('stores') ?? '') || undefined) as PublicContactPayload['stores'],
    registers: (String(values.get('registers') ?? '') || undefined) as PublicContactPayload['registers'],
    needs: values.getAll('needs').map(String) as PublicContactPayload['needs'],
    message: String(values.get('message') ?? '').trim() || undefined,
    demo: values.get('demo') === 'yes',
    website: String(values.get('website') ?? '').trim() || undefined
  };
}

export function PublicLandingPage() {
  const rootRef = useRef<HTMLDivElement>(null);
  const [submitState, setSubmitState] = useState<SubmitState>('idle');

  useEffect(() => {
    document.title = pageTitle;
    setMeta('meta[name="description"]', 'name', 'description', pageDescription);
    setMeta('meta[property="og:title"]', 'property', 'og:title', pageTitle);
    setMeta('meta[property="og:description"]', 'property', 'og:description', pageDescription);
    setMeta('meta[property="og:type"]', 'property', 'og:type', 'website');
    setMeta('meta[property="og:url"]', 'property', 'og:url', 'https://merchtyl.com/');
    setMeta('meta[name="twitter:card"]', 'name', 'twitter:card', 'summary');
    setMeta('meta[name="twitter:title"]', 'name', 'twitter:title', pageTitle);
    setMeta('meta[name="twitter:description"]', 'name', 'twitter:description', pageDescription);
    let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!canonical) {
      canonical = document.createElement('link');
      canonical.rel = 'canonical';
      document.head.appendChild(canonical);
    }
    canonical.href = 'https://merchtyl.com/';
  }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const form = root.querySelector<HTMLFormElement>('[data-public-contact-form]');
    const reset = root.querySelector<HTMLButtonElement>('[data-contact-reset]');
    if (!form || !reset) return;
    const country = form.elements.namedItem('countryCode') as HTMLSelectElement | null;
    const region = form.elements.namedItem('regionCode') as HTMLSelectElement | null;
    const regionLabel = form.querySelector<HTMLElement>('[data-region-label]');
    let countries: PublicCountry[] = [];
    let regionRequest = 0;

    const setOptions = (select: HTMLSelectElement, placeholder: string,
      options: Array<{ code: string; name: string }>) => {
      select.replaceChildren(new Option(placeholder, ''), ...options.map(option => new Option(option.name, option.code)));
    };
    const loadCountries = async () => {
      if (!country) return;
      try {
        countries = await getPublicCountries();
        country.setCustomValidity('');
        setOptions(country, 'Select country', countries);
      } catch {
        country.setCustomValidity('Countries could not be loaded. Please refresh and try again.');
      }
    };
    const onCountryChange = async () => {
      if (!country || !region) return;
      const requestId = ++regionRequest;
      region.disabled = true;
      country.setCustomValidity('');
      setOptions(region, country.value ? 'Loading…' : 'Select country first', []);
      const selected = countries.find(item => item.code === country.value);
      if (regionLabel) regionLabel.textContent = selected ? `${selected.regionLabel} *` : 'Province / Territory or State *';
      if (!selected) return;
      try {
        const regions = await getPublicRegions(selected.code);
        if (requestId !== regionRequest || country.value !== selected.code) return;
        setOptions(region, selected.code === 'CA' ? 'Select province or territory' : 'Select state', regions);
        region.disabled = false;
      } catch {
        if (requestId !== regionRequest) return;
        setOptions(region, 'Regions could not be loaded', []);
        country.setCustomValidity('Regions could not be loaded. Please try selecting the country again.');
      }
    };
    void loadCountries();
    country?.addEventListener('change', onCountryChange);

    const honeypot = document.createElement('div');
    honeypot.className = 'landing-honeypot';
    honeypot.setAttribute('aria-hidden', 'true');
    honeypot.innerHTML = '<label for="q-website">Website</label><input id="q-website" name="website" type="text" tabindex="-1" autocomplete="off">';
    form.appendChild(honeypot);

    const status = document.createElement('p');
    status.className = 'landing-form-status';
    status.setAttribute('role', 'alert');
    status.setAttribute('aria-live', 'assertive');
    form.appendChild(status);

    const onSubmit = async (event: SubmitEvent) => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      const submit = form.querySelector<HTMLButtonElement>('button[type="submit"]');
      if (submit?.disabled) return;
      const original = submit?.innerHTML ?? '';
      setSubmitState('submitting');
      status.textContent = '';
      if (submit) {
        submit.disabled = true;
        submit.textContent = 'Sending…';
      }
      try {
        await submitPublicContact(formPayload(form));
        const firstName = String(new FormData(form).get('name') ?? '').trim().split(/\s+/)[0];
        const heading = root.querySelector<HTMLElement>('[data-contact-success-view] h3');
        if (heading) heading.textContent = firstName
          ? `Thanks, ${firstName} — we’ve got your details.`
          : 'Thanks — we’ve got your details.';
        form.reset();
        void onCountryChange();
        setSubmitState('success');
      } catch {
        status.textContent = "We couldn't send your message right now. Please try again in a moment.";
        setSubmitState('error');
        if (submit) {
          submit.disabled = false;
          submit.innerHTML = original;
        }
      }
    };
    const onReset = () => setSubmitState('idle');
    form.addEventListener('submit', onSubmit);
    reset.addEventListener('click', onReset);
    return () => {
      form.removeEventListener('submit', onSubmit);
      reset.removeEventListener('click', onReset);
      country?.removeEventListener('change', onCountryChange);
    };
  }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const formView = root.querySelector<HTMLElement>('[data-contact-form-view]');
    const successView = root.querySelector<HTMLElement>('[data-contact-success-view]');
    if (formView) formView.hidden = submitState === 'success';
    if (successView) successView.hidden = submitState !== 'success';
  }, [submitState]);

  return <div
    ref={rootRef}
    className="public-landing-root"
    data-submit-state={submitState}
    dangerouslySetInnerHTML={{ __html: landingMarkup }}
  />;
}
