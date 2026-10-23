// Lab result interpretation shared by the doctor lab views.

/** Canonical status key: "Results Pending" / "results_pending" -> "results pending". */
export const normalizeStatus = (status) =>
   String(status || '').trim().replace(/_/g, ' ').toLowerCase();

const NUMBER = '(-?\\d+(?:[.,]\\d+)?)';

const toNumber = (text) => {
   const n = parseFloat(String(text).replace(',', '.'));
   return Number.isFinite(n) ? n : null;
};

/**
 * Compares a result with its reference range.
 * Understands "4.5-11.0", "4 - 6", "< 5", "<= 5", "> 3", ">= 3" (an optional unit after the numbers is ignored).
 * Returns 'normal', 'abnormal' or 'unknown' (no result, no range, or a format that is not understood).
 */
export const evaluateResult = (resultValue, referenceRange) => {
   if (resultValue === null || resultValue === undefined || String(resultValue).trim() === '') return 'unknown';
   if (!referenceRange || String(referenceRange).trim() === '') return 'unknown';

   const value = toNumber(String(resultValue).match(new RegExp(NUMBER))?.[1]);
   if (value === null) return 'unknown';
   const range = String(referenceRange).trim();

   const between = range.match(new RegExp('^' + NUMBER + '\\s*(?:-|–|to)\\s*' + NUMBER));
   if (between) {
      const low = toNumber(between[1]);
      const high = toNumber(between[2]);
      if (low === null || high === null) return 'unknown';
      return value >= low && value <= high ? 'normal' : 'abnormal';
   }

   const bound = range.match(new RegExp('^(<=|>=|≤|≥|<|>)\\s*' + NUMBER));
   if (bound) {
      const limit = toNumber(bound[2]);
      if (limit === null) return 'unknown';
      switch (bound[1]) {
         case '<': return value < limit ? 'normal' : 'abnormal';
         case '<=': case '≤': return value <= limit ? 'normal' : 'abnormal';
         case '>': return value > limit ? 'normal' : 'abnormal';
         default: return value >= limit ? 'normal' : 'abnormal';
      }
   }
   return 'unknown';
};

/** A published result whose value lies outside its reference range. */
export const isAbnormal = (lab) =>
   normalizeStatus(lab?.status) === 'completed' && evaluateResult(lab?.resultValue, lab?.referenceRange) === 'abnormal';

/** Badge colour for every lab status the backend can produce. */
export const statusBadgeType = (status) => {
   switch (normalizeStatus(status)) {
      case 'completed':
      case 'normal':
         return 'green';
      case 'collected':
         return 'blue';
      case 'results pending':
         return 'indigo';
      case 'pending':
         return 'yellow';
      default:
         return 'gray';
   }
};
