import { SiteFooter } from './components/SiteFooter';
import { SiteHeader } from './components/SiteHeader';
import { AtsScore } from './sections/AtsScore';
import { Benefits } from './sections/Benefits';
import { Faq } from './sections/Faq';
import { FinalCta } from './sections/FinalCta';
import { Hero } from './sections/Hero';
import { Prepare } from './sections/Prepare';
import { Resources } from './sections/Resources';
import { Security } from './sections/Security';
import { Stats } from './sections/Stats';
import { UnderstandProfile } from './sections/UnderstandProfile';
import { Workflow } from './sections/Workflow';

/**
 * The public marketing page. Its light/dark theme (see `html[data-theme]` in
 * index.css) is no longer scoped to this page — `App.tsx`'s `ThemeEffect` applies the
 * same `useThemeStore` value to every route, so a preference set here follows the
 * visitor into the authenticated app after they sign in, and vice versa.
 */
export function LandingPage() {
  return (
    <div id="top" className="min-h-screen bg-void text-ink">
      <SiteHeader />
      <main>
        <Hero />
        <Workflow />
        <Benefits />
        <Prepare />
        <Stats />
        <AtsScore />
        <Resources />
        <UnderstandProfile />
        <Security />
        <Faq />
        <FinalCta />
      </main>
      <SiteFooter />
    </div>
  );
}
