import { useLayoutEffect, useRef } from 'react';
import { gsap } from '@/lib/gsap';

/**
 * Animates a number counting up from 0 once it scrolls into view — same
 * once-per-mount, reduced-motion-aware ScrollTrigger pattern as `Reveal`, just tweening
 * a number instead of opacity/position.
 */
export function CountUp({
  value,
  suffix = '',
  className,
}: {
  value: number;
  suffix?: string | undefined;
  className?: string | undefined;
}) {
  const ref = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      el.textContent = `${value}${suffix}`;
      return;
    }

    const counter = { n: 0 };
    const ctx = gsap.context(() => {
      gsap.to(counter, {
        n: value,
        duration: 1.4,
        ease: 'power2.out',
        scrollTrigger: { trigger: el, start: 'top 85%', once: true },
        onUpdate: () => {
          el.textContent = `${Math.round(counter.n)}${suffix}`;
        },
      });
    }, ref);

    return () => ctx.revert();
  }, [value, suffix]);

  return (
    <span ref={ref} className={className}>
      0{suffix}
    </span>
  );
}
