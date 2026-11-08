import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * PrimeNG preset for "Nuit et lumière" (see design-system/tokens.css and DESIGN.md).
 * Aura supplies the component structure; colours, radii and fields come from the RH App system.
 * Dark mode follows the "rh-dark" class that ThemeService puts on <html>.
 */
export const RhPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { none: '0', xs: '4px', sm: '7px', md: '10px', lg: '16px', xl: '20px' },
  },
  semantic: {
    primary: {
      50: '#eef3ff', 100: '#dce6ff', 200: '#bfd0ff', 300: '#8ab4ff', 400: '#6e92ff', 500: '#2d5bff',
      600: '#2450f0', 700: '#1d40c4', 800: '#1a3699', 900: '#172f7a', 950: '#0f1f52',
    },
    focusRing: { width: '3px', style: 'solid', color: 'rgb(91 140 255 / 0.3)', offset: '0' },
    formField: { paddingX: '0.875rem', paddingY: '0.625rem', borderRadius: '10px' },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff', 50: '#f4f6fb', 100: '#eef1f7', 200: '#e6e9f2', 300: '#d6dbe8', 400: '#a9b1c6',
          500: '#5d6783', 600: '#46506b', 700: '#2a3352', 800: '#141a2e', 900: '#0e1630', 950: '#080b16',
        },
        primary: { color: '{primary.500}', contrastColor: '#ffffff', hoverColor: '{primary.600}', activeColor: '{primary.700}' },
        highlight: { background: '#eaf0ff', focusBackground: '#dce6ff', color: '{primary.700}', focusColor: '{primary.800}' },
        formField: {
          background: '#ffffff', borderColor: '#d6dbe8', hoverBorderColor: '#a9b1c6', focusBorderColor: '#5b8cff',
          color: '#0e1630', placeholderColor: '#5d6783', floatLabelColor: '#46506b',
        },
        text: { color: '#0e1630', hoverColor: '#0e1630', mutedColor: '#5d6783', hoverMutedColor: '#46506b' },
        content: { background: '#ffffff', hoverBackground: '#f4f6fb', borderColor: '#e6e9f2', color: '#0e1630' },
      },
      dark: {
        surface: {
          0: '#ffffff', 50: '#eceff7', 100: '#b3bad0', 200: '#8d96b2', 300: '#5d6783', 400: '#3a4364', 500: '#2a3352',
          600: '#1e2640', 700: '#161d33', 800: '#111729', 900: '#0c1120', 950: '#080b16',
        },
        primary: { color: '{primary.300}', contrastColor: '#081028', hoverColor: '{primary.200}', activeColor: '{primary.100}' },
        highlight: { background: 'rgb(91 140 255 / 0.16)', focusBackground: 'rgb(91 140 255 / 0.24)', color: '#dce6ff', focusColor: '#ffffff' },
        formField: {
          background: '#0c1120', borderColor: '#2a3352', hoverBorderColor: '#3a4364', focusBorderColor: '#8ab4ff',
          color: '#eceff7', placeholderColor: '#8d96b2', floatLabelColor: '#b3bad0',
        },
        text: { color: '#eceff7', hoverColor: '#ffffff', mutedColor: '#8d96b2', hoverMutedColor: '#b3bad0' },
        content: { background: '#111729', hoverBackground: '#161d33', borderColor: '#1e2640', color: '#eceff7' },
      },
    },
  },
});
