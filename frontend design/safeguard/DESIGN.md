---
name: SafeGuard
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f3'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#424752'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f1f1f1'
  outline: '#727783'
  outline-variant: '#c2c6d4'
  surface-tint: '#005db7'
  primary: '#004d99'
  on-primary: '#ffffff'
  primary-container: '#1565c0'
  on-primary-container: '#dae5ff'
  inverse-primary: '#a9c7ff'
  secondary: '#2b5bb5'
  on-secondary: '#ffffff'
  secondary-container: '#759efd'
  on-secondary-container: '#00337c'
  tertiary: '#853600'
  on-tertiary: '#ffffff'
  tertiary-container: '#ab4800'
  on-tertiary-container: '#ffded0'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#a9c7ff'
  on-primary-fixed: '#001b3d'
  on-primary-fixed-variant: '#00468c'
  secondary-fixed: '#d9e2ff'
  secondary-fixed-dim: '#b0c6ff'
  on-secondary-fixed: '#001945'
  on-secondary-fixed-variant: '#00429c'
  tertiary-fixed: '#ffdbcb'
  tertiary-fixed-dim: '#ffb691'
  on-tertiary-fixed: '#341100'
  on-tertiary-fixed-variant: '#793100'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: 0px
  headline-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: 0px
  title-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '700'
    lineHeight: 24px
    letterSpacing: 0.15px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0.4px
  label-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  margin-mobile: 16px
  margin-tablet: 24px
  gutter: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
---

## Brand & Style
The design system for this security application prioritizes authority, vigilance, and clarity. It adheres to the **Material Design 3 (M3)** framework, emphasizing a structured, reliable interface that instills confidence in the user. 

The aesthetic is characterized by a "High-Contrast Security" style. While the core surfaces are light to ensure maximum legibility of logs and alerts, the use of deep primary blues creates a "dark mood" of professional stability. Every element is designed to feel intentional and protective, avoiding unnecessary ornamentation in favor of functional precision.

## Colors
The palette is rooted in a spectrum of blues to signify trust. The **Primary** blue (#1565C0) is used for key actions and branding, while **Primary Dark** (#0D47A1) is reserved for header backgrounds or critical navigation to anchor the "dark mood" within a light-themed container.

**Accent** orange (#FF6F00) provides high-visibility calls to action, such as "Resolve Issues" or "Scan Now." The system utilizes a strict semantic color logic for status: **Success Green** for "Protected" states and **Error Red** for "Threats Detected." Surfaces remain pure white or light grey to maintain a clean, laboratory-like environment for data analysis.

## Typography
This design system utilizes **Roboto Flex** for its systematic, mechanical precision which aligns with Android's native environment. 

Headings are bold and impactful to clearly communicate system status. Card titles use a strict 16sp bold weight to ensure secondary hierarchy is maintained within complex dashboards. Body text is kept tight (13-14sp) to allow for high information density in security logs, while button labels utilize a medium weight for distinct clickability.

## Layout & Spacing
The layout follows a 12-column fluid grid for tablet/desktop and a 4-column grid for mobile. A strict 8dp baseline grid governs all vertical rhythm.

Padding within cards and containers is generous (16px) to prevent the security data from feeling cramped. Elements are grouped using a "stack" logic: 8px for related items (e.g., an icon and its label), and 16px-24px for distinct sections. Margins are kept at a standard 16px on mobile to maximize horizontal screen real estate for tabular data.

## Elevation & Depth
In accordance with Material Design 3, depth is communicated through a combination of **Tonal Elevation** and **Ambient Shadows**.

Cards and primary containers use a 2dp elevation. This is expressed through a subtle, soft shadow that distinguishes the white surface from the light grey background. For interactive elements like "Scan" buttons, elevation should increase slightly on press. Card borders are utilized in tandem with elevation to provide a "contained" feel, using a 1px solid stroke in the specified border color (#E0E0E0) to define structural boundaries.

## Shapes
The shape language is modern and approachable yet structured. Cards and major containers utilize a **12px (12dp)** corner radius, providing a softer look that balances the serious nature of the app. 

Smaller components like checkboxes and input fields follow the "Soft" logic (4px-8px), while primary buttons may use a higher roundedness (Pill-shaped) to distinguish them from informational cards.

## Components
- **Buttons:** Primary buttons use a solid #1565C0 fill with white labels. Secondary buttons use the Accent #FF6F00 for high-urgency tasks.
- **Cards:** White surfaces with 12dp rounded corners and a 1px #E0E0E0 border. They should include a 2dp shadow to separate them from the #F5F5F5 background.
- **Input Fields:** Outlined style is mandatory. The border-color should be #E0E0E0 in default state, shifting to Primary #1565C0 on focus. Labels should float within the outline.
- **Chips:** Used for filtering security logs; they should be low-profile with 8dp rounded corners and a thin stroke.
- **Lists:** High-density with 13sp body text. Each list item should have a clear leading icon (e.g., a shield or warning triangle) to denote status.
- **Progress Indicators:** A thick, circular "Scan" indicator using Primary or Accent colors depending on the threat level detected during the process.
- **Status Banners:** Full-width banners at the top of the UI using Status Red or Status Green to communicate immediate device safety.