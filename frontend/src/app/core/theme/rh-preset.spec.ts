import { RhPreset } from './rh-preset';

describe('RhPreset', () => {
  const semantic = (RhPreset as { semantic: Record<string, any> }).semantic;

  it('gives form fields a visible focus ring, not just a border hue change', () => {
    expect(semantic['formField'].focusRing?.shadow).toBe('var(--rh-focus)');
  });

  it('times transitions with the motion token, so reduced motion turns them off', () => {
    expect(semantic['transitionDuration']).toBe('var(--rh-fast)');
  });
});
