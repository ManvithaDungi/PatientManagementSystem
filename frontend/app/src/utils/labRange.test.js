import { evaluateResult, isAbnormal, normalizeStatus, statusBadgeType } from './labRange';

describe('evaluateResult', () => {
   test.each([
      ['5.0', '4-6', 'normal'],
      ['4', '4-6', 'normal'],
      ['6', '4 - 6', 'normal'],
      ['3.9', '4-6', 'abnormal'],
      ['6.1', '4.5-11.0', 'normal'],
      ['12', '4.5 - 11.0', 'abnormal'],
      ['150', '<200', 'normal'],
      ['200', '< 200', 'abnormal'],
      ['200', '<= 200', 'normal'],
      ['3', '>5', 'abnormal'],
      ['5', '>=5', 'normal'],
      ['14.5 g/dL', '13.8 - 17.2', 'normal'],
      ['5,4', '4-6', 'normal'],
   ])('%s against %s is %s', (value, range, expected) => {
      expect(evaluateResult(value, range)).toBe(expected);
   });

   test.each([
      [null, '4-6'], ['', '4-6'], ['5', null], ['5', ''], ['negative', '4-6'], ['5', 'see report'],
   ])('%s with range %s cannot be judged', (value, range) => {
      expect(evaluateResult(value, range)).toBe('unknown');
   });
});

describe('status helpers', () => {
   test('normalizeStatus ignores case and underscores', () => {
      expect(normalizeStatus('RESULTS_PENDING')).toBe('results pending');
      expect(normalizeStatus(' Completed ')).toBe('completed');
      expect(normalizeStatus(undefined)).toBe('');
   });

   test('every backend status has its own badge colour (nothing falls through to red)', () => {
      expect(statusBadgeType('Pending')).toBe('yellow');
      expect(statusBadgeType('Collected')).toBe('blue');
      expect(statusBadgeType('Results Pending')).toBe('indigo');
      expect(statusBadgeType('Completed')).toBe('green');
      expect(statusBadgeType('PENDING')).toBe('yellow');
      expect(statusBadgeType('whatever')).toBe('gray');
   });

   test('only a published result outside its range is abnormal', () => {
      expect(isAbnormal({ status: 'Completed', resultValue: '9', referenceRange: '4-6' })).toBe(true);
      expect(isAbnormal({ status: 'Completed', resultValue: '5', referenceRange: '4-6' })).toBe(false);
      expect(isAbnormal({ status: 'Completed', resultValue: '9', referenceRange: null })).toBe(false);
      expect(isAbnormal({ status: 'Collected', resultValue: '9', referenceRange: '4-6' })).toBe(false);
   });
});
