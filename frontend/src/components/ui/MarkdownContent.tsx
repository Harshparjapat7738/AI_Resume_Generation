import { useMemo, type JSX, type ReactNode } from 'react';

/**
 * Minimal, dependency-free Markdown renderer for plain-text content the app never generated as
 * HTML — job descriptions (pasted by the user or extracted from a URL) today, any similar
 * AI/user-authored text tomorrow. Supports exactly what that kind of text actually uses:
 * `#`-style headings, `-`/`*`/numbered lists, `**bold**`/`*italic*` emphasis, and blank-line
 * paragraphs. Deliberately not a full CommonMark implementation (no links, tables, code blocks) —
 * and deliberately not `dangerouslySetInnerHTML` either: every node below is a real React
 * element built from the parsed text, so there's no HTML-injection surface even though the
 * source is fully user-controlled.
 */

type Block =
  | { type: 'heading'; level: number; text: string }
  | { type: 'ul'; items: string[] }
  | { type: 'ol'; items: string[] }
  | { type: 'p'; lines: string[] };

const HEADING_RE = /^(#{1,6})\s+(.+)$/;
const BULLET_RE = /^\s*[-*•]\s+(.+)$/;
const NUMBERED_RE = /^\s*\d+[.)]\s+(.+)$/;

function parseBlocks(raw: string): Block[] {
  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  const blocks: Block[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i] ?? '';
    if (line.trim() === '') {
      i++;
      continue;
    }

    const heading = HEADING_RE.exec(line);
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1]!.length, text: heading[2]!.trim() });
      i++;
      continue;
    }

    if (BULLET_RE.test(line)) {
      const items: string[] = [];
      let current = lines[i] ?? '';
      while (i < lines.length && BULLET_RE.test(current)) {
        items.push(BULLET_RE.exec(current)![1]!);
        i++;
        current = lines[i] ?? '';
      }
      blocks.push({ type: 'ul', items });
      continue;
    }

    if (NUMBERED_RE.test(line)) {
      const items: string[] = [];
      let current = lines[i] ?? '';
      while (i < lines.length && NUMBERED_RE.test(current)) {
        items.push(NUMBERED_RE.exec(current)![1]!);
        i++;
        current = lines[i] ?? '';
      }
      blocks.push({ type: 'ol', items });
      continue;
    }

    const paraLines: string[] = [];
    let current = lines[i] ?? '';
    while (i < lines.length && current.trim() !== '' && !HEADING_RE.test(current) && !BULLET_RE.test(current) && !NUMBERED_RE.test(current)) {
      paraLines.push(current);
      i++;
      current = lines[i] ?? '';
    }
    blocks.push({ type: 'p', lines: paraLines });
  }

  return blocks;
}

/** `**bold**` and `*italic*`/`_italic_` — bold's `**` pair is tried first so it never gets
 *  mistaken for two adjacent italic markers. */
const INLINE_RE = /(\*\*[^*]+\*\*|\*[^*]+\*|_[^_]+_)/g;

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  return text.split(INLINE_RE).map((part, i) => {
    if (/^\*\*[^*]+\*\*$/.test(part)) return <strong key={`${keyPrefix}-${i}`}>{part.slice(2, -2)}</strong>;
    if (/^\*[^*]+\*$/.test(part) || /^_[^_]+_$/.test(part)) return <em key={`${keyPrefix}-${i}`}>{part.slice(1, -1)}</em>;
    return part;
  });
}

const HEADING_TAG: Record<number, keyof JSX.IntrinsicElements> = { 1: 'h2', 2: 'h3', 3: 'h4', 4: 'h5', 5: 'h6', 6: 'h6' };
const HEADING_CLASS: Record<number, string> = {
  1: 'text-xl font-semibold text-ink',
  2: 'text-lg font-semibold text-ink',
  3: 'text-base font-semibold text-ink',
  4: 'text-sm font-semibold text-ink',
  5: 'text-sm font-semibold text-ink',
  6: 'text-sm font-semibold text-ink',
};

export function MarkdownContent({ text, className = '' }: { text: string; className?: string }) {
  const blocks = useMemo(() => parseBlocks(text), [text]);

  return (
    <div className={className}>
      {blocks.map((block, idx) => {
        const key = `b${idx}`;
        const spacing = idx === 0 ? '' : 'mt-4';

        if (block.type === 'heading') {
          const level = Math.min(Math.max(block.level, 1), 6);
          const Tag = HEADING_TAG[level] ?? 'h4';
          return (
            <Tag key={key} className={`${spacing} ${HEADING_CLASS[level] ?? HEADING_CLASS[3]}`}>
              {renderInline(block.text, key)}
            </Tag>
          );
        }

        if (block.type === 'ul') {
          return (
            <ul key={key} className={`${spacing} list-disc space-y-1.5 pl-5 marker:text-ink-faint`}>
              {block.items.map((item, i) => (
                <li key={`${key}-${i}`}>{renderInline(item, `${key}-${i}`)}</li>
              ))}
            </ul>
          );
        }

        if (block.type === 'ol') {
          return (
            <ol key={key} className={`${spacing} list-decimal space-y-1.5 pl-5 marker:text-ink-faint`}>
              {block.items.map((item, i) => (
                <li key={`${key}-${i}`}>{renderInline(item, `${key}-${i}`)}</li>
              ))}
            </ol>
          );
        }

        return (
          <p key={key} className={spacing}>
            {block.lines.map((line, i) => (
              <span key={`${key}-${i}`}>
                {renderInline(line, `${key}-${i}`)}
                {i < block.lines.length - 1 && <br />}
              </span>
            ))}
          </p>
        );
      })}
    </div>
  );
}
