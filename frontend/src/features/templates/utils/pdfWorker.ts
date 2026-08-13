/**
 * pdf.js is a large dependency (see the "chunks are larger than 500kB" build warning without
 * this) — dynamically imported here, on first actual use, rather than statically, so it only
 * ever loads into a session that opens the Templates page and actually previews a PDF, not into
 * every visitor's initial bundle. The worker's URL is resolved the same way (Vite's `?url`) and
 * configured once per page load, memoized so a second preview doesn't redo the import.
 */
let pdfjsPromise: Promise<typeof import('pdfjs-dist')> | null = null;

export function loadPdfjs(): Promise<typeof import('pdfjs-dist')> {
  if (!pdfjsPromise) {
    pdfjsPromise = Promise.all([
      import('pdfjs-dist'),
      import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
    ]).then(([pdfjs, worker]) => {
      pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
      return pdfjs;
    });
  }
  return pdfjsPromise;
}
