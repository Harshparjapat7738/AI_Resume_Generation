import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import {
  deleteCustomTemplate,
  duplicateCustomTemplate,
  renameCustomTemplate,
  setDefaultTemplate,
  unsetDefaultTemplate,
  type Template,
} from '@/services/templateApi';
import { formatDate } from '@/features/dashboard/utils';
import { StructureSummary } from './StructureSummary';

const TEMPLATES_QUERY_PREFIX = ['templates'];

function useInvalidateTemplates() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_PREFIX });
}

/** A user's own uploaded template — visually distinct from a built-in card (see
 *  TemplatesPage): shows the "Custom" badge, owner, upload date, a compact real-structure
 *  summary instead of a rendered preview, and the full set of owner-only actions (point 4/7 of
 *  the feature spec). Generation ("Use template") is wired end-to-end only for RESUME today —
 *  see the disabled state's own tooltip for why. */
export function CustomTemplateCard({
  template,
  onUse,
  onEditMapping,
}: {
  template: Template;
  onUse: (() => void) | null;
  onEditMapping: () => void;
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
  });

  const duplicateMutation = useMutation({
    mutationFn: () => duplicateCustomTemplate(template.templateId),
    onSuccess: () => invalidate(),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteCustomTemplate(template.templateId),
    onSuccess: () => {
      setConfirmingDelete(false);
      invalidate();
    },
  });

  const defaultMutation = useMutation({
    mutationFn: () => (template.isDefault ? unsetDefaultTemplate(template.templateId) : setDefaultTemplate(template.templateId)),
    onSuccess: () => invalidate(),
  });

  const placeholderCount = template.detectedFields?.length ?? 0;

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border-strong p-4">
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
            Use this template
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
