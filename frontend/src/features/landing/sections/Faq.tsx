import { useState } from 'react';
import { Reveal } from '../components/Reveal';
import { ChevronDownIcon } from '../components/icons';
import { faqItems } from '../content';

export function Faq() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);

  return (
    <section id="faq" className="scroll-mt-20 border-t border-border bg-surface py-20 sm:py-24">
      <div className="mx-auto max-w-3xl px-6">
        <Reveal className="text-center">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand">FAQ</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Questions people actually ask
          </h2>
        </Reveal>

        <Reveal delay={0.1} className="mt-12 divide-y divide-border rounded-2xl border border-border bg-void">
          {faqItems.map((item, i) => {
            const isOpen = openIndex === i;
            return (
              <div key={item.question}>
                <button
                  type="button"
                  onClick={() => setOpenIndex(isOpen ? null : i)}
                  aria-expanded={isOpen}
                  className="flex w-full items-center justify-between gap-4 px-6 py-5 text-left"
                >
                  <span className="text-sm font-medium text-ink sm:text-base">
                    {item.question}
                  </span>
                  <ChevronDownIcon
                    className={`h-4 w-4 shrink-0 text-ink-faint transition-transform duration-300 ${
                      isOpen ? 'rotate-180' : ''
                    }`}
                  />
                </button>
                <div
                  className={`grid transition-all duration-300 ease-in-out ${
                    isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                  }`}
                >
                  <div className="overflow-hidden">
                    <p className="px-6 pb-5 text-sm leading-relaxed text-ink-muted">
                      {item.answer}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}
