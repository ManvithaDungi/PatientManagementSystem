import React, { useState, useEffect } from 'react';
import Modal from '../../components/common/Modal';
import Button from '../../components/common/Button';
import api, { consentAPI } from '../../services/api';

// Must match the data categories the backend enforces (ConsentService.CONSENT_TYPES).
export const CONSENT_TYPE_OPTIONS = [
   { value: 'MEDICAL_RECORDS', label: 'Medical records', description: 'Diagnoses, notes and health history.' },
   { value: 'LAB_RESULTS', label: 'Lab results', description: 'Laboratory test orders and results.' },
   { value: 'PRESCRIPTIONS', label: 'Prescriptions', description: 'Current and past prescriptions.' },
   { value: 'VITAL_SIGNS', label: 'Vital signs', description: 'Recorded vitals and measurements.' },
   { value: 'ALL', label: 'All health data', description: 'Everything above, in one grant.' },
];

const EXPIRY_OPTIONS = [
   { value: 'none', label: 'No expiry (until I revoke it)' },
   { value: '90', label: '90 days' },
   { value: '180', label: '6 months' },
   { value: '365', label: '1 year' },
];

const doctorName = (d) => {
   const name = [d.firstName, d.lastName].filter(Boolean).join(' ').trim();
   return `${name ? `Dr. ${name}` : d.email}${d.specialty ? ` — ${d.specialty}` : ''}`;
};

/**
 * Lets a patient grant a provider access to one category of their data. This is
 * the only way to create a first consent: the list of existing consents can
 * only re-grant or revoke what already exists.
 */
const NewConsentModal = ({ isOpen, onClose, onGranted }) => {
   const [doctors, setDoctors] = useState([]);
   const [loadingDoctors, setLoadingDoctors] = useState(false);
   const [providerId, setProviderId] = useState('');
   const [consentType, setConsentType] = useState('MEDICAL_RECORDS');
   const [expiry, setExpiry] = useState('none');
   const [acknowledged, setAcknowledged] = useState(false);
   const [submitting, setSubmitting] = useState(false);
   const [error, setError] = useState(null);

   useEffect(() => {
      if (!isOpen) return undefined;
      let cancelled = false;
      setError(null);
      setProviderId('');
      setConsentType('MEDICAL_RECORDS');
      setExpiry('none');
      setAcknowledged(false);
      setLoadingDoctors(true);
      api.doctors.getAll()
         .then((list) => {
            if (!cancelled) setDoctors((list || []).filter((d) => d.userId));
         })
         .catch(() => {
            if (!cancelled) setError('Unable to load the list of providers. Please try again.');
         })
         .finally(() => {
            if (!cancelled) setLoadingDoctors(false);
         });
      return () => { cancelled = true; };
   }, [isOpen]);

   const canSubmit = providerId && consentType && acknowledged && !submitting;

   const handleSubmit = async (e) => {
      e.preventDefault();
      if (!canSubmit) return;
      setSubmitting(true);
      setError(null);
      try {
         const expiresAt = expiry === 'none'
            ? null
            : new Date(Date.now() + Number(expiry) * 24 * 60 * 60 * 1000).toISOString();
         const provider = doctors.find((d) => String(d.userId) === String(providerId));
         const typeLabel = CONSENT_TYPE_OPTIONS.find((o) => o.value === consentType)?.label;
         const consent = await consentAPI.grantConsent({
            grantedToId: Number(providerId),
            consentType,
            reason: `Granted by patient for ${typeLabel}`,
            expiresAt,
         });
         onGranted?.(consent, provider);
      } catch (err) {
         setError(err.message || 'Could not save your consent. Please try again.');
      } finally {
         setSubmitting(false);
      }
   };

   const selectedType = CONSENT_TYPE_OPTIONS.find((o) => o.value === consentType);

   return (
      <Modal isOpen={isOpen} onClose={onClose} title="Grant access to a provider" maxWidth="max-w-lg">
         <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-gray-600 dark:text-slate-300">
               Providers other than your assigned doctor can only see the data you choose to share. You can
               withdraw this access at any time.
            </p>

            <div>
               <label htmlFor="consent-provider" className="block text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">
                  Provider
               </label>
               <select
                  id="consent-provider"
                  value={providerId}
                  onChange={(e) => setProviderId(e.target.value)}
                  disabled={loadingDoctors}
                  className="w-full border border-gray-300 dark:border-slate-600 rounded px-2 py-2 text-sm dark:bg-slate-700 dark:text-slate-100"
               >
                  <option value="">{loadingDoctors ? 'Loading providers...' : 'Select a provider'}</option>
                  {doctors.map((d) => (
                     <option key={d.userId} value={d.userId}>{doctorName(d)}</option>
                  ))}
               </select>
            </div>

            <div>
               <label htmlFor="consent-type" className="block text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">
                  Data to share
               </label>
               <select
                  id="consent-type"
                  value={consentType}
                  onChange={(e) => setConsentType(e.target.value)}
                  className="w-full border border-gray-300 dark:border-slate-600 rounded px-2 py-2 text-sm dark:bg-slate-700 dark:text-slate-100"
               >
                  {CONSENT_TYPE_OPTIONS.map((o) => (
                     <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
               </select>
               {selectedType && (
                  <p className="text-xs text-gray-500 dark:text-slate-400 mt-1">{selectedType.description}</p>
               )}
            </div>

            <div>
               <label htmlFor="consent-expiry" className="block text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">
                  Access lasts for
               </label>
               <select
                  id="consent-expiry"
                  value={expiry}
                  onChange={(e) => setExpiry(e.target.value)}
                  className="w-full border border-gray-300 dark:border-slate-600 rounded px-2 py-2 text-sm dark:bg-slate-700 dark:text-slate-100"
               >
                  {EXPIRY_OPTIONS.map((o) => (
                     <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
               </select>
            </div>

            <label className="flex items-start gap-2 text-sm text-gray-700 dark:text-slate-200">
               <input
                  type="checkbox"
                  checked={acknowledged}
                  onChange={(e) => setAcknowledged(e.target.checked)}
                  className="mt-0.5"
               />
               <span>I understand this provider will be able to view the selected data until I withdraw access.</span>
            </label>

            {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

            <div className="flex justify-end gap-2 pt-2">
               <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
               <Button type="submit" disabled={!canSubmit}>
                  {submitting ? 'Saving...' : 'Grant access'}
               </Button>
            </div>
         </form>
      </Modal>
   );
};

export default NewConsentModal;
