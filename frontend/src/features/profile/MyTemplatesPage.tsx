import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { describeError } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { showToast } from '@/components/ui/toast';
import { ChevronLeftIcon, DocumentIcon, PlusCircleIcon } from '@/features/dashboard/icons';
import {
  deleteTemplate,
  downloadTemplate,
  listTemplates,
  setDefaultTemplate,
  type TemplateResponse,
} from '@/services/templateApi';
import { RenameTemplateModal } from './components/RenameTemplateModal';
import { TemplateCard } from './components/TemplateCard';
import { UploadTemplateModal } from './components/UploadTemplateModal';

const TEMPLATES_QUERY_KEY = ['templates'] as const;

/** Downloads the blob and either opens it in a new tab (preview) or saves it (download) — the
 *  same object-URL pattern OptimizationResultPage.tsx already uses for the JSON export, needed
 *  here because every request must carry the in-memory Bearer token (apiFetchBlob), so a plain
 *  `<a href>`/`window.open(url)` straight at the API can't be used. */
async function openOrSaveTemplate(id: string, filename: string, mode: 'preview' | 'download') {
  const blob = await downloadTemplate(id);
  const url = URL.createObjectURL(blob);
  if (mode === 'preview') {
    window.open(url, '_blank', 'noopener,noreferrer');
  } else {
    const link = window.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
  }
  // Revoked on a delay rather than immediately — a just-opened preview tab (and Firefox's
  // download handling in particular) needs the URL to still resolve after this call returns.
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

/**
 * Profile → My Templates (ADR-034). A user's own Resume/Cover Letter files, uploaded once and
 * reused at JD-optimization handoff time — see OptimizationResultPage.tsx's "Choose your
 * template" section, the only other place these are ever surfaced.
 */
export function MyTemplatesPage() {
  const queryClient = useQueryClient();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<TemplateResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<TemplateResponse | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const templatesQuery = useQuery({ queryKey: TEMPLATES_QUERY_KEY, queryFn: listTemplates });

  const setDefault = useMutation({
    mutationFn: (id: string) => setDefaultTemplate(id),
    onSuccess: () => {
      showToast('Default template updated.');
      queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_KEY });
    },
    onError: (error) => showToast(describeError(error)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteTemplate(id),
    onSuccess: () => {
      showToast('Template deleted.');
      queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_KEY });
    },
    onError: (error) => showToast(describeError(error)),
    onSettled: () => setDeleteTarget(null),
  });

  const runFileAction = async (template: TemplateResponse, mode: 'preview' | 'download') => {
    setBusyId(template.id);
    try {
      await openOrSaveTemplate(template.id, template.fileName, mode);
    } catch (error) {
      showToast(describeError(error));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />

      {uploadOpen && (
        <UploadTemplateModal
          onClose={() => setUploadOpen(false)}
          onUploaded={() => {
            setUploadOpen(false);
            queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_KEY });
          }}
        />
      )}

      {renameTarget && (
        <RenameTemplateModal
          template={renameTarget}
          onClose={() => setRenameTarget(null)}
          onRenamed={() => {
            setRenameTarget(null);
            queryClient.invalidateQueries({ queryKey: TEMPLATES_QUERY_KEY });
          }}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="Delete this template?"
          message={`"${deleteTarget.name}" will be permanently removed. This can't be undone.`}
          confirmLabel="Delete"
          danger
          loading={remove.isPending}
          onConfirm={() => remove.mutate(deleteTarget.id)}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:py-10">
        <Link
          to="/profile"
          className="inline-flex items-center gap-1 text-xs font-medium text-ink-faint transition-colors hover:text-ink"
        >
          <ChevronLeftIcon className="h-3.5 w-3.5" />
          Profile
        </Link>

        <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-ink">My Templates</h1>
            <p className="mt-1 text-sm text-ink-muted">
              Upload your Resume or Cover Letter templates once, then pick one at generation time —
              no need to upload the same file again.
            </p>
          </div>
          <Button onClick={() => setUploadOpen(true)} className="!px-4 !py-2.5 !text-sm">
            <PlusCircleIcon className="h-4 w-4" />
            Add Template
          </Button>
        </div>

        <div className="mt-6">
          {templatesQuery.isLoading && <FullScreenSpinner label="Loading your templates…" />}

          {templatesQuery.isError && (
            <p className="text-sm text-rose">{describeError(templatesQuery.error)}</p>
          )}

          {templatesQuery.data && templatesQuery.data.length === 0 && (
            <EmptyState
              icon={<DocumentIcon className="h-5 w-5" />}
              title="No saved templates yet."
              hint="Upload a Resume or Cover Letter file to reuse across every generation."
              action={
                <Button onClick={() => setUploadOpen(true)} className="!px-5 !py-2.5 !text-sm">
                  Add your first template
                </Button>
              }
            />
          )}

          {templatesQuery.data && templatesQuery.data.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {templatesQuery.data.map((template) => (
                <TemplateCard
                  key={template.id}
                  template={template}
                  busy={busyId === template.id}
                  onPreview={() => runFileAction(template, 'preview')}
                  onDownload={() => runFileAction(template, 'download')}
                  onRename={() => setRenameTarget(template)}
                  onSetDefault={() => setDefault.mutate(template.id)}
                  onDelete={() => setDeleteTarget(template)}
                />
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
