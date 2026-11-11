import { dinars } from './dinar';

// Intl puts a narrow no-break space between thousands: compare with plain spaces
const plat = (t: string) => t.replace(/[  ]/g, ' ');

describe('dinars', () => {
  it('writes an amount in Tunisian dinars, with the millimes', () => {
    expect(plat(dinars(3500))).toBe('3 500,000 DT');
    expect(plat(dinars(2345.678))).toBe('2 345,678 DT');
    expect(plat(dinars(0))).toBe('0,000 DT');
  });
  it('can leave the millimes out (totals, averages)', () => {
    expect(plat(dinars(62000.4, false))).toBe('62 000 DT');
  });
});
