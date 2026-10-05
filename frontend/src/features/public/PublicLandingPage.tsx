import { useEffect, useRef, useState } from 'react';
import { submitPublicContact } from '../../api/client';
import type { PublicContactPayload } from '../../api/types';
import landingMarkup from './landingPageMarkup.html?raw';
import './publicLandingPage.css';

type SubmitState = 'idle' | 'submitting' | 'success' | 'error';

const pageTitle = 'Merchtyl — Retail and restaurant POS for Canadian stores';
const pageDescription = 'One system for Canadian retail, restaurant, lottery, inventory, registers, and end-of-day reporting.';

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
    province: String(values.get('province') ?? '').trim() || undefined,
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
