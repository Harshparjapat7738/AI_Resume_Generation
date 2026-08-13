import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/services/apiClient';
import {
  deleteCustomTemplate,
  duplicateCustomTemplate,
  renameCustomTemplate,
  setDefaultTemplate,
  unsetDefaultTemplate,
  type Template,
} from '@/services/templateApi';
import { formatDate } from '@/features/dashboard/utils';
import { DocumentPreview } from './DocumentPreview';
import { StructureSummary } from './StructureSummary';

const TEMPLATES_QUERY_PREFIX = ['templates'];

function useInvalidateTemplates() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_PREFIX });
}

/** A 404 here means the catalogue row is already gone server-side — most often because an
 *  earlier action on it already succeeded (e.g. a double-clicked delete) or the row never
 *  durably persisted in the first place (a dropped write during a backend hiccup). Either way
 *  the card the user is looking at is stale, not "stuck": the right move is to make it vanish
 *  by refetching, not to pin a permanent error banner on a row that can never succeed again. */
function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

/** A user's own uploaded template — visually distinct from a built-in card (see
 *  TemplatesPage): shows the "Custom" badge, owner, upload date, the same real rendered preview
 *  a built-in template's card shows (redesign brief &sect;15 — never just a filename), a
 *  collapsible structural fact table as secondary detail, and the full set of owner-only
 *  actions (point 4/7 of the feature spec). Generation ("Use template") is wired end-to-end
 *  only for RESUME today — see the disabled state's own tooltip for why. */
export function CustomTemplateCard({
  template,
  onUse,
  onEditMapping,
  onPreview,
  useLabel = 'Use this template',
  selected = false,
}: {
  template: Template;
  onUse: (() => void) | null;
  onEditMapping: () => void;
  /** Opens the shared full-preview modal (redesign brief &sect;8) — optional because the generate
   *  wizard's own template step (TemplatePage) reuses this same card without that modal; the
   *  card's own inline preview still renders either way, only the "view it larger" affordance
   *  is caller-provided. */
  onPreview?: (() => void) | undefined;
  /** Lets a caller in a *selection* context (the generate wizard's template step, as opposed
   *  to TemplatesPage's own "mail-merge an already-generated resume" flow) relabel the same
   *  action truthfully — "Use this template" there would misleadingly imply an immediate
   *  generation, when it really just selects this template for the wizard to use next. */
  useLabel?: string;
  /** Highlights the card as the wizard's current selection — purely visual, TemplatesPage never
   *  sets this since a template isn't "selected" there the way it is mid-wizard. */
  selected?: boolean;
}) {
  const invalidate = useInvalidateTemplates();
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [showStructure, setShowStructure] = useState(false);
  const [name, setName] = useState(template.name);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const onPointerDown = (event: PointerEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [menuOpen]);

  const renameMutation = useMutation({
    mutationFn: () => renameCustomTemplate(template.templateId, name.trim()),
    onSuccess: () => {
      setRenaming(false);
      invalidate();
    },
    onError: (error) => {
      // The template was renamed/deleted elsewhere already — drop out of edit mode and let the
      // refetch remove or update the card instead of leaving the rename form stuck open on a
      // row that will never save.
      if (isNotFound(error)) {
        setRenaming(false);
        invalidate();
      }
    },
  });

  const duplicateMutation = useMutation({
    mutationFn: () => duplicateCustomTemplate(template.templateId),
    onSuccess: () => invalidate(),
    onError: (error) => {
      if (isNotFound(error)) invalidate();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteCustomTemplate(template.templateId),
    onSuccess: () => {
      setConfirmingDelete(false);
      invalidate();
    },
    onError: (error) => {
      // Already gone server-side — this is exactly what the user wanted, so finish the delete
      // flow the same way a real 204 would: close the dialog and refetch so the ghost card
      // disappears, rather than leaving a permanent "not found" banner on a row the user can
      // never successfully delete.
      if (isNotFound(error)) {
        setConfirmingDelete(false);
        invalidate();
      }
    },
  });

  const defaultMutation = useMutation({
    mutationFn: () => (template.isDefault ? unsetDefaultTemplate(template.templateId) : setDefaultTemplate(template.templateId)),
    onSuccess: () => invalidate(),
    onError: (error) => {
      if (isNotFound(error)) invalidate();
    },
  });

  const placeholderCount = template.detectedFields?.length ?? 0;

  return (
    <div
      className={`flex flex-col overflow-hidden rounded-2xl border transition-colors ${
        selected ? 'border-ember-soft ring-2 ring-ember-soft/40' : 'border-border-strong'
      }`}
    >
      <div
        role={onPreview ? 'button' : undefined}
        tabIndex={onPreview ? 0 : undefined}
        aria-label={onPreview ? `Preview ${template.name}` : undefined}
        onClick={onPreview}
        onKeyDown={
          onPreview
            ? (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onPreview();
                }
              }
            : undefined
        }
        className={`group relative aspect-[8.5/11] w-full overflow-hidden bg-white outline-none ${
          onPreview ? 'cursor-pointer focus-visible:ring-2 focus-visible:ring-ember-soft' : ''
        }`}
      >
        <DocumentPreview templateId={template.templateId} renderWidth={640} className="h-full w-full" />
        {onPreview && (
          <div className="pointer-events-none absolute inset-0 flex items-end justify-center bg-linear-to-t from-black/70 via-black/10 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100">
            <span className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-white px-4 py-2 text-xs font-semibold text-void shadow-lg">
              View full preview
            </span>
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            {renaming ? (
              <div className="flex items-center gap-2">
                <TextField
                  label=""
                  aria-label="Template name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={120}
                  className="!mt-0 !py-1.5 !text-sm"
                />
              </div>
            ) : (
              <h3 className="truncate text-sm font-semibold text-ink">{template.name}</h3>
            )}
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
              <span className="rounded-full bg-ember/10 px-2 py-0.5 text-[11px] font-medium text-ember-soft">Custom</span>
              {selected && (
                <span className="rounded-full bg-ember-soft px-2 py-0.5 text-[11px] font-medium text-void">Selected</span>
              )}
              {template.isDefault && (
                <span className="rounded-full bg-mint/10 px-2 py-0.5 text-[11px] font-medium text-mint">Default</span>
              )}
              <span className="text-[11px] text-ink-faint">You · {formatDate(template.createdAt)}</span>
            </div>
          </div>

          <div ref={menuRef} className="relative shrink-0">
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              aria-label="Template actions"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:bg-surface-2 hover:text-ink"
            >
              ⋯
            </button>
            {menuOpen && (
              <div
                role="menu"
                className="absolute right-0 top-full z-10 mt-1 w-44 rounded-xl border border-border bg-surface p-1.5 shadow-xl"
              >
                <MenuItem
                  label="Edit mapping"
                  onClick={() => {
                    setMenuOpen(false);
                    onEditMapping();
                  }}
                />
                <MenuItem
                  label="Rename"
                  onClick={() => {
                    setMenuOpen(false);
                    setRenaming(true);
                  }}
                />
                <MenuItem
                  label="Duplicate"
                  onClick={() => {
                    setMenuOpen(false);
                    duplicateMutation.mutate();
                  }}
                />
                <MenuItem
                  label={template.isDefault ? 'Remove as default' : 'Set as default'}
                  onClick={() => {
                    setMenuOpen(false);
                    defaultMutation.mutate();
                  }}
                />
                <MenuItem
                  label="Delete"
                  danger
                  onClick={() => {
                    setMenuOpen(false);
                    setConfirmingDelete(true);
                  }}
                />
              </div>
            )}
          </div>
        </div>

        {renaming ? (
          <div className="flex items-center gap-2">
            <Button
              type="button"
              className="!px-3 !py-1.5 !text-xs"
              loading={renameMutation.isPending}
              onClick={() => renameMutation.mutate()}
            >
              Save
            </Button>
            <Button
              type="button"
              variant="secondary"
              className="!px-3 !py-1.5 !text-xs"
              onClick={() => {
                setRenaming(false);
                setName(template.name);
              }}
            >
              Cancel
            </Button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setShowStructure((v) => !v)}
            className="rounded-xl border border-border bg-void px-3 py-2 text-left text-xs text-ink-faint transition-colors hover:border-border-strong"
          >
            {template.originalFilename} · {placeholderCount} placeholder{placeholderCount === 1 ? '' : 's'} ·{' '}
            {showStructure ? 'Hide structure ▲' : 'View structure ▼'}
          </button>
        )}

        {showStructure && template.structure && (
          <div className="rounded-xl border border-border bg-void p-3">
            <StructureSummary structure={template.structure} />
          </div>
        )}

        {(renameMutation.isError || duplicateMutation.isError || deleteMutation.isError || defaultMutation.isError) && (
          <ErrorBanner
            error={renameMutation.error ?? duplicateMutation.error ?? deleteMutation.error ?? defaultMutation.error}
          />
        )}

        <div className="mt-1">
          {onUse ? (
            <Button type="button" variant="secondary" className="w-full !py-2 !text-xs" onClick={onUse}>
              {useLabel}
            </Button>
          ) : (
            <span
              title="Generation for cover letters and emails is coming soon — upload, mapping and preview already work for every type."
              className="block w-full cursor-not-allowed rounded-full border border-border px-4 py-2 text-center text-xs text-ink-faint"
            >
              Generation coming soon
            </span>
          )}
        </div>

        {confirmingDelete && (
          <ConfirmDialog
            title="Delete this template?"
            message={`"${template.name}" will be removed from your templates. This can't be undone.`}
            confirmLabel="Delete"
            danger
            loading={deleteMutation.isPending}
            onConfirm={() => deleteMutation.mutate()}
            onCancel={() => setConfirmingDelete(false)}
          />
        )}
      </div>
    </div>
  );
}

function MenuItem({ label, onClick, danger = false }: { label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={`block w-full rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-surface-2 ${
        danger ? 'text-rose' : 'text-ink-muted hover:text-ink'
      }`}
    >
      {label}
    </button>
  );
}
