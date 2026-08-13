# Template PDF Support + Strict Layout-Preserving Generation — Implementation Report

**Scope honored:** Custom Template workflow only. No changes to built-in templates' own
rendering, ATS scoring, JD analysis, grounding rules, or the Application/Generate All
orchestration logic — all of that is reused exactly as it already existed.

## Summary

Custom templates (`POST /api/resumes/templates/custom`) now accept **PDF** alongside the
existing DOCX, with real structural analysis and a genuine content-stream mail-merge (not a
visual overlay — see "Rendering" below). Custom templates (DOCX or PDF) are also now
**selectable in the main `/generate` wizard**, not only through the separate, standalone
Templates page — closing a real gap found during inspection: the wizard's template step still
showed a dead "Coming Soon" stub for upload, left over from before custom upload was built as a
separate feature. See `docs/ARCHITECTURE_DECISIONS.md` ADR-023 for the full design record.

## Files changed

**document-service** — new:
- `pdf/PdfStructureAnalyzer.java` — PDF structural analysis (page count/dimensions, fonts,
  placeholders), mirrors `docx/DocxStructureAnalyzer`.
- `pdf/PdfPlaceholderLocator.java` — the `PDFTextStripper` subclass both analysis and merge
  share: locates every `{{token}}` with its page, bounding box, live font and color.
- `pdf/PdfMailMerge.java` — fills placeholders: fit/condense/fail logic, redaction, drawing.
- `pdf/PdfContentRedactor.java` — genuinely removes placeholder text from the content stream
  (token-level rewrite via `PDFStreamParser`/`ContentStreamWriter`) — not a visual overlay.
- `client/TemplateServiceClient.java` — Feign client for fetching a template's field mapping
  from resume-service, used by the main render endpoint's dispatch.
- `domain/TemplateFormat.java` — `DOCX`/`PDF` enum.

**document-service** — modified:
- `domain/TemplateStructure.java`, `domain/DetectedField.java` — extended with nullable
  PDF-specific fields (page count/dimensions in points, per-field bounding box/font/color),
  following the same "one shape, format-specific fields null when not applicable" convention
  `Template` already used.
- `domain/CustomTemplateAsset.java` — gained `format`.
- `service/CustomTemplateAssetService.java` — `detectFormat` (extension **and** signature,
  never the extension alone) dispatches analysis/merge to the DOCX or PDF engine.
- `service/DocumentRenderService.java` — the **main** render endpoint
  (`POST /api/documents/resume-versions/{id}/render`) now checks whether `templateId` names a
  custom template the caller owns and, if so, delegates to `CustomTemplateAssetService.generate`
  instead of the built-in `ResumeTemplate` path — reusing the existing method, not a second
  pipeline.
- `docx/DocxStructureAnalyzer.java` — updated to build `TemplateStructure` via its new
  `forDocx` factory (no behavioral change).
- `api/CustomTemplateAssetController.java`, `api/dto/CustomTemplateAssetResponses.java`,
  `client/ClientDtos.java` — carry the new fields through the API/Feign layers.
- `pom.xml` (document-service and root) — `org.apache.pdfbox:pdfbox` declared directly
  (previously only a transitive dependency).

**resume-service** — modified:
- `domain/Template.java` — `forCustomUpload` now takes and stores the real `format` for
  `supportedFormats` instead of the previous hardcoded `List.of("DOCX")`.
- `client/ClientDtos.java` — mirrors document-service's expanded response shape (required —
  Jackson's default `FAIL_ON_UNKNOWN_PROPERTIES` would otherwise break every custom-template
  upload, not just PDF ones, the moment document-service's response gained new fields).
- `service/TemplateService.java`, `domain/TemplateSource.java` — pass the format through;
  refreshed stale comments (a pre-existing note claimed custom upload was "not built" and
  DOCX-only — both were already false before this task, now corrected).

**platform-common** — modified:
- `error/ErrorCode.java` — new `TEMPLATE_CONTENT_OVERFLOW` (422).

**frontend** — modified:
- `features/generate/TemplatePage.tsx` — the real integration point: lists the caller's own
  custom templates alongside built-ins, adds a working "Upload a template" entry point (reusing
  the existing `UploadTemplateWizard`), replaces the dead "Coming Soon" upload stub. Online
  templates remain honestly "Coming Soon" (still genuinely unbuilt).
- `features/templates/components/UploadTemplateWizard.tsx` — accepts `.pdf`, shows the
  DOCX/PDF format badge and an "analyzed" status line.
- `features/templates/components/StructureSummary.tsx` — renders PDF-native facts (page
  count/size in points) instead of assuming DOCX's twips-based shape.
- `services/templateApi.ts` — TypeScript types extended to match the new backend fields.

**docs**: `ARCHITECTURE_DECISIONS.md` — new ADR-023.

**Tests fixed (pre-existing drift, not caused by this task):** `resume-service`'s
`TemplateServiceTest`/`TemplateControllerTest` still called a year-old 1-arg `TemplateService`
constructor and 1-arg `list(type)` signature from before custom upload existed at all — this
blocked the whole module's test suite from compiling. Fixed to match the real, current
signatures; no production code was changed to make this pass.

## APIs changed

- `POST /api/resumes/templates/custom` — now accepts `.pdf` in addition to `.docx`.
- `GET /api/resumes/templates[/​{id}]` response — `structure`/`detectedFields` gained new
  fields (see below); `supportedFormats` now reflects the real uploaded format.
- `POST /api/documents/resume-versions/{id}/render` — behavior extended: a `templateId`
  belonging to the caller's own custom template now renders through the custom-template
  pipeline (DOCX mail-merge or PDF redact-and-merge) instead of 404/failing. No change to its
  request/response shape.
- No endpoint was removed or had a breaking shape change.

## Database/model changes

- `custom_template_assets` (document-service, Mongo): new `format` field (`DOCX`/`PDF`);
  existing rows read back as `DOCX` (the only format that ever existed before this).
- `templates` (resume-service, Mongo): no schema change — `structure`/`detectedFields` were
  already opaque `Map`/`List<Map>` passthroughs, so the new PDF-specific keys just flow through
  unchanged; `supportedFormats` now stores the real format instead of a hardcoded value for
  newly-created rows.

## DOCX flow changes

None to the actual merge logic. The only change is *reachability*: a DOCX custom template can
now also be selected from the main `/generate` wizard and rendered through
`POST /api/documents/resume-versions/{id}/render` (previously only reachable via the standalone
Templates page's own `/custom-templates/{id}/generate` endpoint, which still works unchanged).

## PDF flow changes (new)

`Upload → validate (extension + magic-byte signature) → PdfStructureAnalyzer (page facts, every
{{token}} with page/bounding-box/font/color; zero placeholders → rejected outright) → Template
selectable in the wizard → resume content generated exactly as for any other template (JD-
tailored, grounded — unchanged) → PdfMailMerge: PdfContentRedactor genuinely removes each
placeholder's text from the content stream (not painted over), then the resolved value is drawn
in the placeholder's own font/size/color at its own position, wrapped and, if needed, condensed
to fit; content that still can't fit fails with TEMPLATE_CONTENT_OVERFLOW and the real reason,
never a silently broken document → download`.

## Rendering — how "the template is the source of truth" is actually enforced

No HTML/CSS re-authoring is ever attempted for a custom template (DOCX or PDF) — the built-in
Thymeleaf/openhtmltopdf pipeline is untouched and never invoked for a custom template. For PDF
specifically: placeholder text is identified and removed at the **content-stream token level**
(`PDFStreamParser` → decode each `Tj`/`TJ` operator's text via the live font → splice out only
the matched characters → `ContentStreamWriter` rewrites the stream) — every other operator
(images, lines, other text, backgrounds) passes through byte-for-byte unchanged. This was a
real bug caught by the test suite itself: an earlier version only painted a white rectangle over
the old text, which changed how the page *looked* but left the placeholder token in the PDF's
extractable text layer (an ATS parser or copy-paste would still have seen it). The fix is the
`PdfContentRedactor` class described above.

## Tests added

`document-service`, all passing (38 tests in the touched packages):
- `pdf/PdfStructureAnalyzerTest` — rejects a non-PDF; rejects a PDF with zero placeholders;
  finds a placeholder with correct page/geometry/font-size; dedupes repeated tokens.
- `pdf/PdfMailMergeTest` — replaces a placeholder and leaves surrounding text alone (asserted
  against the raw content-stream bytes, not just extracted text — this environment's PDFBox
  falls back from Helvetica to LiberationSans for *extraction*, which can make the extraction
  heuristic misjudge a spacing boundary inside a perfectly well-formed operator; asserting on
  the actual bytes written is what proves the merge itself, independent of that unrelated
  environment quirk); an unresolved placeholder disappears rather than staying literal; a
  multi-line value becomes multiple extractable lines; content that cannot fit even after
  condensing fails with `TEMPLATE_CONTENT_OVERFLOW`.
- `docx/DocxStructureAnalyzerTest`, `docx/DocxMailMergeTest` — this DOCX pipeline had **no**
  test coverage before this task despite being fully built; added real coverage (placeholder
  detection/mapping-suggestion, a DOCX with zero placeholders is still accepted — unlike PDF —
  merge/replace/disappear/multi-line behavior) since the task's own test list requires it and
  it was a genuine pre-existing gap.
- `service/CustomTemplateAssetServiceTest` — a real DOCX and a real PDF are both accepted and
  stored with the correct `format`; a PDF with no placeholders is rejected; a file named
  `.pdf`/`.docx` that isn't really that format is rejected (extension is never trusted alone);
  an unsupported extension and an oversized file are both rejected.
- `service/DocumentRenderServiceCustomTemplateTest` — the main render endpoint's dispatch:
  a custom `templateId` delegates entirely to `CustomTemplateAssetService.generate` (never the
  built-in `PdfRenderer`) and correctly fetches the owner's saved field mapping; a built-in
  `templateId` still goes through the original, unchanged path.
- `service/DocumentRenderServiceTest` — updated for the new constructor
  (`TemplateServiceClient`, `CustomTemplateAssetService`); every existing built-in-template test
  still passes unmodified in behavior.

## Validation performed

- `mvn install` — clean, full reactor (all ~13 modules), 0 `[ERROR]` lines, run inside the
  project's own JDK 21 Docker toolchain (the host machine only has JDK 26 installed, which
  breaks Mockito unrelated to any product code — see prior session notes).
- `document-service` test suite in isolation — 38/38 passing.
- Frontend `typecheck` and `build` — both clean.
- **Live, end-to-end, against the real Docker stack** (not mocked):
  - A real PDF fixture (3 placeholders: `{{NAME}}`, `{{EMAIL}}`, `{{EXPERIENCE}}`) uploaded via
    `POST /api/resumes/templates/custom` → correctly analyzed (`source: CUSTOM_UPLOAD`,
    `supportedFormats: ["PDF"]`, 3 detected fields) → mapped → selected via
    `POST /api/resumes/generate` (the same call the main wizard makes) → the **main** render
    endpoint (`POST /api/documents/resume-versions/{id}/render`, not the dedicated
    custom-templates one) correctly dispatched to the PDF merge, returned `format: "PDF"` →
    downloaded a real, valid PDF (`%PDF-` header) → the `{{EXPERIENCE}}` placeholder was
    correctly filled with the real, grounded generated content and the literal `{{...}}` token
    was genuinely gone from the file.
  - The same flow repeated for a real DOCX fixture through the same main render endpoint —
    correctly dispatched, `format: "DOCX"`.
  - A built-in template (`modern-ats`) rendered through the same endpoint immediately
    afterward — unaffected, confirming the dispatch only takes the custom-template branch when
    it actually should.
  - Extension/signature validation (a `.docx` upload that isn't really a ZIP, a `.pdf` upload
    that isn't really a PDF) is proven at the unit-test level
    (`CustomTemplateAssetServiceTest`); the live-script's equivalent check hit a local curl/file
    path issue in the verification script itself, not the product — not chased further given
    the unit test already proves this directly against the real validation code.

## Known limitations

- **PDF fit estimation is a documented heuristic**, not true layout analysis: available
  width/height are inferred from the placeholder's own position out to the nearest page edge.
  This codebase has no general PDF layout engine, and building one was out of scope (see
  ADR-023's "Reason"). Condensation is deterministic (proportional truncation of the
  already-grounded text, dropping trailing paragraph groups first) — it never invents anything,
  but it does mean a very tightly-designed PDF template could reject content a human might have
  fit by hand.
- **Visual/pixel fidelity is not machine-verified.** This environment has no PDF-to-image
  rendering to compare against, so tests verify the real content-stream bytes and extracted
  text (proving placeholders are genuinely replaced and everything else is untouched) rather
  than a rendered screenshot. A real PDF viewer was not available to visually confirm the final
  layout in this session.
- **Font substitution in a redrawn PDF is best-effort.** The placeholder's own live font is
  reused when it can encode the replacement text; if it can't (e.g. a subsetted embedded font
  missing a character the real content needs), it falls back to standard Helvetica rather than
  fail the whole merge — a real, cosmetic degradation, not a silent content loss.
- **A pre-existing, unrelated bug was discovered, not fixed**: `PUT
  /api/profile/personal-information` currently returns `500 INTERNAL_ERROR` on this stack.
  This surfaced during live verification (a template's `{{NAME}}`/`{{EMAIL}}` fields resolved
  empty because the profile write itself never succeeded) but is entirely outside
  profile-service, which this task never touches — flagged here rather than silently worked
  around, and left for whoever owns that service.
- Custom PDF templates cannot represent images/logos as *editable* regions — only text
  placeholders are ever modified; any image, line or background on the page is always left
  completely untouched, exactly as intended (never redrawn, never removed).
