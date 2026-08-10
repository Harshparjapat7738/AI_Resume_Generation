import { SiteFooter } from './components/SiteFooter';
import { SiteHeader } from './components/SiteHeader';
import { AtsScore } from './sections/AtsScore';
import { Benefits } from './sections/Benefits';
import { Faq } from './sections/Faq';
import { FinalCta } from './sections/FinalCta';
import { Hero } from './sections/Hero';
import { Problem } from './sections/Problem';
import { Security } from './sections/Security';
import { Workflow } from './sections/Workflow';

export function LandingPage() {
  return (
    <div id="top" className="min-h-screen bg-void text-ink">
      <SiteHeader />
      <main>
        <Hero />
        <Problem />
        <Workflow />
        <AtsScore />
        <Benefits />
        <Security />
        <Faq />
        <FinalCta />
      </main>
      <SiteFooter />
    </div>
  );
}
