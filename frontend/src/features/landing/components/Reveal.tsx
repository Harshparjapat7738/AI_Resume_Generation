import { useLayoutEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { gsap } from '@/lib/gsap';

interface RevealProps {
  children: ReactNode;
  className?: string;
  /** Animate direct children in a stagger instead of the wrapper as one block. */
  stagger?: boolean;
  delay?: number;
}

/**
 * Fades and lifts content in as it enters the viewport. A no-op (content stays
 * visible, no ScrollTrigger) when the OS requests reduced motion.
 */
export function Reveal({ children, className, stagger = false, delay = 0 }: RevealProps) {
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;

    const targets: Element[] = stagger ? Array.from(el.children) : [el];
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (prefersReducedMotion) return;

    const ctx = gsap.context(() => {
      gsap.set(targets, { autoAlpha: 0, y: 28 });
      gsap.to(targets, {
        autoAlpha: 1,
        y: 0,
        duration: 0.8,
        delay,
        ease: 'power3.out',
        stagger: stagger ? 0.12 : 0,
        scrollTrigger: { trigger: el, start: 'top 82%', once: true },
      });
    }, ref);

    return () => ctx.revert();
  }, [stagger, delay]);

  return (
    <div ref={ref} className={className}>
      {children}
    </div>
  );
}
