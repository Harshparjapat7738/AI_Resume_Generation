import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Select } from '@/components/ui/Select';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse, Skill } from '@/services/profileApi';
import { EmptyState } from '@/components/ui/EmptyState';
import { SparkleIcon } from '@/features/profile/components/icons';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';

const suggestedCategories = [
  'Programming Languages',
  'Frameworks',
  'Databases',
  'Cloud',
  'DevOps',
  'Tools',
  'Other',
];

// A finite, standard scale — unlike skill name/category (open-ended, so they stay free text
// with a suggestion list), proficiency genuinely only ever takes one of these values.
const PROFICIENCY_LEVELS = ['Beginner', 'Intermediate', 'Advanced', 'Expert'];
const PROFICIENCY_OTHER = 'Other';

/** Maps a persisted (freeform, possibly legacy) proficiency string onto the picklist,
 *  falling back to "Other" + the original text so nothing already saved is silently lost. */
function toProficiencyPicklist(stored: string | null | undefined): { select: string; other: string } {
  if (!stored) return { select: '', other: '' };
  return PROFICIENCY_LEVELS.includes(stored) ? { select: stored, other: '' } : { select: PROFICIENCY_OTHER, other: stored };
}

/** Buckets skills by their own `category` field (uncategorised ones fall into "Other") so the
 *  list reads as a few labelled groups instead of one long, unsorted wall of chips. Suggested
 *  categories keep their defined order; any custom category the user typed sorts after them
 *  alphabetically; "Other" (explicit or uncategorised) is always last. */
function groupSkillsByCategory(skills: Skill[]): { category: string; skills: Skill[] }[] {
  const groups = new Map<string, Skill[]>();
  for (const skill of skills) {
    const key = skill.category?.trim() || 'Other';
    const bucket = groups.get(key);
    if (bucket) bucket.push(skill);
    else groups.set(key, [skill]);
  }
  const known = suggestedCategories.filter((c) => c !== 'Other' && groups.has(c));
  const custom = [...groups.keys()].filter((c) => c !== 'Other' && !known.includes(c)).sort();
  const order = groups.has('Other') ? [...known, ...custom, 'Other'] : [...known, ...custom];
  return order.map((category) => ({ category, skills: groups.get(category) as Skill[] }));
}

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a skill').max(100),
  category: z.string().trim().max(60).optional(),
  proficiency: z.string().trim().max(40).optional(),
  proficiencyOther: z.string().trim().max(40).optional(),
  yearsOfExperience: z.coerce.number().int().min(0).max(70).optional().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;

/** One chip per skill, collapsed behind "+ Add Skill" when there's nothing (or nothing being
 *  edited) to show — same collapse/empty-state/transition pattern as every other repeatable
 *  section (see EducationManager), just chips instead of full cards since a skill is only a
 *  name + a couple of short attributes. */
export function SkillsManager({
  profile,
  onChanged,
}: {
  profile: ProfileResponse;
  onChanged: (profile: ProfileResponse) => void;
}) {
  const [formError, setFormError] = useState<unknown>(null);
  const [deleteError, setDeleteError] = useState<unknown>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const proficiencyValue = watch('proficiency');

  const startAdd = () => {
    reset({ name: '', category: '', proficiency: '', proficiencyOther: '', yearsOfExperience: undefined });
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.skills.find((s) => s.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
    const proficiency = toProficiencyPicklist(item.proficiency);
    reset({
      name: item.name ?? '',
      category: item.category ?? '',
      proficiency: proficiency.select,
      proficiencyOther: proficiency.other,
      yearsOfExperience: item.yearsOfExperience ?? undefined,
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setIsAdding(false);
    setFormError(null);
    reset({ name: '', category: '', proficiency: '', proficiencyOther: '', yearsOfExperience: undefined });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    const proficiency =
      values.proficiency === PROFICIENCY_OTHER ? (values.proficiencyOther ?? '').trim() || undefined : values.proficiency;
    const input = {
      name: values.name,
      category: values.category,
      proficiency,
      yearsOfExperience: values.yearsOfExperience === '' ? undefined : values.yearsOfExperience,
    };
    try {
      const updated = editingId
        ? await profileApi.updateSkill(editingId, input)
        : await profileApi.addSkill(input);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    setDeleteError(null);
    try {
      onChanged(await profileApi.deleteSkill(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setDeletingId(null);
    }
  };

  const filteredSkills = search.trim()
    ? profile.skills.filter((s) => s.name?.toLowerCase().includes(search.trim().toLowerCase()))
    : profile.skills;
  const skillGroups = groupSkillsByCategory(filteredSkills);
  const chips = (
    <div className="space-y-4">
      {profile.skills.length > 5 && (
        <div className="relative">
          <svg
            viewBox="0 0 20 20"
            fill="none"
            aria-hidden="true"
            className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint"
          >
            <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.5" />
            <path d="m17 17-3.8-3.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search skills…"
            aria-label="Search skills"
            className="w-full rounded-xl border border-border bg-surface py-2.5 pl-10 pr-4 text-sm text-ink placeholder:text-ink-faint transition-colors focus:outline-none focus:ring-2 focus:ring-ember-soft/40"
          />
        </div>
      )}
      {skillGroups.length === 0 && (
        <p className="text-sm text-ink-faint">No skills match "{search}".</p>
      )}
      {skillGroups.map((group) => (
        <div key={group.category}>
          {skillGroups.length > 1 && (
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-faint">{group.category}</p>
          )}
          <div className="flex flex-wrap gap-2">
            {group.skills.map((skill) => (
              <div
                key={skill.evidenceId}
                className={`animate-card-in flex items-center gap-2 rounded-full border border-border bg-surface py-1.5 pl-3.5 pr-2 text-sm transition-all duration-150 hover:border-border-strong ${
                  deletingId === skill.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
                }`}
              >
                <span className="text-ink">{skill.name}</span>
                {skill.proficiency && <span className="text-xs text-ink-faint">· {skill.proficiency}</span>}
                <button type="button" onClick={() => startEdit(skill.evidenceId)} className="ml-1 text-xs text-ink-faint hover:text-ink">
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(skill.evidenceId)}
                  disabled={deletingId === skill.evidenceId}
                  aria-label={`Remove skill: ${skill.name}`}
                  title="Remove"
                  className="flex h-5 w-5 items-center justify-center rounded-full text-xs text-ink-faint transition-colors hover:bg-rose/10 hover:text-rose disabled:opacity-50"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );

  const form = (
    <>
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit skill' : 'Add skill'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Skill" placeholder="Kubernetes" error={errors.name?.message} {...register('name')} />
          <TextField
            label="Category (optional)"
            list="skill-categories"
            placeholder="DevOps"
            {...register('category')}
          />
          <datalist id="skill-categories">
            {suggestedCategories.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <div className="space-y-4">
            <Select label="Proficiency (optional)" {...register('proficiency')}>
              <option value="">Select proficiency</option>
              {PROFICIENCY_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {level}
                </option>
              ))}
              <option value={PROFICIENCY_OTHER}>{PROFICIENCY_OTHER}</option>
            </Select>
            {proficiencyValue === PROFICIENCY_OTHER && (
              <TextField label="Specify proficiency" {...register('proficiencyOther')} />
            )}
          </div>
          <TextField
            label="Years of experience (optional)"
            type="number"
            min={0}
            max={70}
            error={errors.yearsOfExperience?.message}
            {...register('yearsOfExperience')}
          />
        </div>
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add skill'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.skills.length === 0;

  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<SparkleIcon className="h-5 w-5" />}
          title="No skills added yet."
          hint="Languages, frameworks, tools — anything relevant to the roles you're targeting."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Skill
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {deleteError !== null ? <ErrorBanner error={deleteError} /> : null}
      {!isEmpty && chips}
      <SectionEditorToggle open={isOpen} addLabel="Add Skill" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
