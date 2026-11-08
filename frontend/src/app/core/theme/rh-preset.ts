import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * PrimeNG preset for the « Linear » system (see design-system/tokens.css and DESIGN.md).
 * Aura supplies the component structure. Every colour role points at the RH App semantic
 * tokens with var(), so the same values serve light, dark and the forced-dark login subtree:
 * switching the CSS variables is enough. Only the surface palettes keep literal values
 * (Aura derives component shades from them); they mirror the --rh-gray-* primitives.
 */
const roles = {
  primary: { color: 'var(--rh-accent)', contrastColor: 'var(--rh-accent-ink)', hoverColor: 'var(--rh-accent-hover)', activeColor: 'var(--rh-accent-hover)' },
  highlight: { background: 'var(--rh-surface-3)', focusBackground: 'var(--rh-surface-3)', color: 'var(--rh-ink)', focusColor: 'var(--rh-ink)' },
  mask: { background: 'var(--rh-scrim)', color: 'var(--rh-ink)' },
  formField: {
    background: 'var(--rh-surface-2)', disabledBackground: 'var(--rh-surface-3)',
    filledBackground: 'var(--rh-surface-2)', filledHoverBackground: 'var(--rh-surface-2)', filledFocusBackground: 'var(--rh-surface-2)',
    borderColor: 'var(--rh-line-strong)', hoverBorderColor: 'var(--rh-muted)', focusBorderColor: 'var(--rh-accent)', invalidBorderColor: 'var(--rh-danger)',
    color: 'var(--rh-ink)', disabledColor: 'var(--rh-muted)', placeholderColor: 'var(--rh-muted)', invalidPlaceholderColor: 'var(--rh-danger)',
    floatLabelColor: 'var(--rh-muted)', floatLabelFocusColor: 'var(--rh-accent-text)', floatLabelActiveColor: 'var(--rh-muted)',
    iconColor: 'var(--rh-muted)', shadow: 'none',
  },
  text: { color: 'var(--rh-ink)', hoverColor: 'var(--rh-ink)', mutedColor: 'var(--rh-muted)', hoverMutedColor: 'var(--rh-ink-2)' },
  content: { background: 'var(--rh-surface)', hoverBackground: 'var(--rh-surface-2)', borderColor: 'var(--rh-line)', color: 'var(--rh-ink)', hoverColor: 'var(--rh-ink)' },
  overlay: {
    select: { background: 'var(--rh-surface)', borderColor: 'var(--rh-line)', color: 'var(--rh-ink)', shadow: 'var(--rh-shadow-overlay)' },
    popover: { background: 'var(--rh-surface)', borderColor: 'var(--rh-line)', color: 'var(--rh-ink)', shadow: 'var(--rh-shadow-overlay)' },
    modal: { background: 'var(--rh-surface)', borderColor: 'var(--rh-line)', color: 'var(--rh-ink)', shadow: 'var(--rh-shadow-overlay)' },
  },
  list: {
    option: {
      focusBackground: 'var(--rh-surface-2)', selectedBackground: 'var(--rh-surface-3)', selectedFocusBackground: 'var(--rh-surface-3)',
      color: 'var(--rh-ink)', focusColor: 'var(--rh-ink)', selectedColor: 'var(--rh-ink)', selectedFocusColor: 'var(--rh-ink)',
      icon: { color: 'var(--rh-muted)', focusColor: 'var(--rh-ink-2)' },
    },
    optionGroup: { background: 'transparent', color: 'var(--rh-muted)' },
  },
  navigation: {
    item: {
      focusBackground: 'var(--rh-surface-2)', activeBackground: 'var(--rh-surface-3)',
      color: 'var(--rh-ink)', focusColor: 'var(--rh-ink)', activeColor: 'var(--rh-ink)',
      icon: { color: 'var(--rh-muted)', focusColor: 'var(--rh-ink-2)', activeColor: 'var(--rh-ink-2)' },
    },
  },
};

export const RhPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { none: '0', xs: '4px', sm: '6px', md: '6px', lg: '8px', xl: '12px' },
  },
  semantic: {
    transitionDuration: 'var(--rh-fast)',
    primary: {
      50: '#eff0fb', 100: '#dfe1f7', 200: '#c3c7f2', 300: '#a4abf2', 400: '#8f98ef', 500: '#5e6ad2',
      600: '#535fc4', 700: '#4752b3', 800: '#3a4391', 900: '#2f3673', 950: '#1d2147',
    },
    focusRing: { width: '2px', style: 'solid', color: 'var(--rh-accent)', offset: '2px' },
    // 13px text + 2 x 7px padding + 2 x 1px border = 32px (--rh-control-height)
    formField: {
      paddingX: '0.625rem', paddingY: '0.4375rem', borderRadius: '6px',
      // A real ring on focus: a border hue change alone is not visible enough (dark: ~1.1:1)
      focusRing: { width: '0', style: 'none', color: 'transparent', offset: '0', shadow: 'var(--rh-focus)' },
    },
    colorScheme: {
      light: {
        // mirror of --rh-gray-* (light)
        surface: { 0: '#ffffff', 50: '#f7f7f8', 100: '#f4f4f5', 200: '#ececee', 300: '#e6e6e9', 400: '#8e8f96', 500: '#6b6d73', 600: '#4b4d53', 700: '#3c3e44', 800: '#26282c', 900: '#1b1c1e', 950: '#0f1011' },
        ...roles,
      },
      dark: {
        // mirror of --rh-gray-d* (dark)
        surface: { 0: '#ffffff', 50: '#f7f8f8', 100: '#d0d6e0', 200: '#8a8f98', 300: '#686b73', 400: '#3a3c42', 500: '#26282c', 600: '#1f2023', 700: '#1c1d1f', 800: '#141516', 900: '#0f1011', 950: '#08090a' },
        ...roles,
      },
    },
  },
});
