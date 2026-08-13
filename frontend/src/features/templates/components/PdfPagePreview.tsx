import { useEffect, useRef, useState } from 'react';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { loadPdfjs } from '../utils/pdfWorker';

/**
 * Renders one page of a real, already-rendered PDF (built-in templates and PDF-sourced custom
 * uploads — see `documentApi.getTemplatePreview`) onto a canvas via pdf.js. This is the actual
 * document, not a mockup: same fonts, colors, columns and spacing the final generated PDF has,
 * because it *is* that same rendering pipeline (`DocumentRenderService#renderPreview`), just fed
 * sample data instead of the user's real profile.
 */
export function PdfPagePreview({
  blob,
  page = 1,
  renderWidth = 700,
  onDocumentReady,
  onError,
}: {
  blob: Blob;
  page?: number | undefined;
  /** Internal raster width in CSS px — the canvas is styled `width: 100%`, so this only
   *  controls crispness, not layout size. Bump it for the full-preview modal. */
  renderWidth?: number | undefined;
  onDocumentReady?: ((info: { numPages: number }) => void) | undefined;
  onError?: (() => void) | undefined;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const docRef = useRef<PDFDocumentProxy | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setReady(false);
    Promise.all([loadPdfjs(), blob.arrayBuffer()])
      .then(([pdfjs, buffer]) => pdfjs.getDocument({ data: buffer }).promise)
      .then((pdf) => {
        if (cancelled) {
          void pdf.destroy();
          return;
        }
        docRef.current = pdf;
        onDocumentReady?.({ numPages: pdf.numPages });
        setReady(true);
      })
      .catch(() => {
        if (!cancelled) onError?.();
      });
    return () => {
      cancelled = true;
      void docRef.current?.destroy();
      docRef.current = null;
    };
    // onDocumentReady/onError are stable enough in practice (inline callbacks recreated per
    // render would otherwise re-trigger this on every parent re-render, which is worse) —
    // only the blob itself should re-run the load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blob]);

  useEffect(() => {
    if (!ready || !docRef.current || !canvasRef.current) return;
    let cancelled = false;
    const pdf = docRef.current;
    const clampedPage = Math.min(Math.max(1, page), pdf.numPages);

    pdf
      .getPage(clampedPage)
      .then((pdfPage) => {
        if (cancelled) return;
        const canvas = canvasRef.current;
        const ctx = canvas?.getContext('2d');
        if (!canvas || !ctx) return;
        const baseViewport = pdfPage.getViewport({ scale: 1 });
        const scale = renderWidth / baseViewport.width;
        const viewport = pdfPage.getViewport({ scale });
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        return pdfPage.render({ canvasContext: ctx, viewport }).promise;
      })
      .catch(() => {
        if (!cancelled) onError?.();
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, page, renderWidth]);

  return <canvas ref={canvasRef} className="block w-full" role="img" aria-label="Document preview" />;
}
