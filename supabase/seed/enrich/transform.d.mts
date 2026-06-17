// Type declarations for the pure enrichment transforms in transform.mjs, so the
// rack-MCP test suite (TypeScript strict) can import them type-safely without an
// `any`. The runtime implementation lives in transform.mjs.

export interface WgerImage {
  image?: string;
  is_main?: boolean;
}

export interface WgerTranslation {
  name?: string;
  language?: number;
  description?: string;
}

export interface WgerExercise {
  translations?: WgerTranslation[];
  muscles?: Array<{ name?: string; name_en?: string }>;
  muscles_secondary?: Array<{ name?: string; name_en?: string }>;
  images?: WgerImage[];
}

export interface FreeExerciseDbExercise {
  name?: string;
  instructions?: string[];
  primaryMuscles?: string[];
  secondaryMuscles?: string[];
}

export interface WgerRecord {
  key: string;
  name: string;
  instructions: string[];
  primaryMuscles: string[];
  secondaryMuscles: string[];
  imageUrl: string | null;
  imageKey: string | null;
}

export interface FreeExerciseDbRecord {
  key: string;
  instructions: string[];
  primaryMuscles: string[];
  secondaryMuscles: string[];
}

export interface MergedRecord {
  instructions: string[];
  primaryMuscles: string[];
  secondaryMuscles: string[];
  imageKey: string | null;
  instructionsSource: 'wger' | 'free-exercise-db' | 'none';
}

export interface EnrichmentPatch extends MergedRecord {
  key: string;
  name: string;
  imageUrl: string | null;
}

export function fail(message: string): never;
export function nameKey(name: string): string;
export function parseWgerSteps(descriptionHtml: unknown): string[];
export function wgerMainImageUrl(exercise: WgerExercise): string | null;
export function isFreeExerciseDbImage(value: unknown): boolean;
export function imageStorageKey(url: string): string;
export function wgerRecord(exercise: WgerExercise): WgerRecord | null;
export function freeExerciseDbRecord(
  exercise: FreeExerciseDbExercise,
): FreeExerciseDbRecord | null;
export function mergeRecords(
  wger: WgerRecord | null,
  fed: FreeExerciseDbRecord | null,
): MergedRecord;
export function buildEnrichSql(patches: EnrichmentPatch[]): string;
export function buildPatches(
  wgerExport: WgerExercise[],
  fedExport: FreeExerciseDbExercise[],
): EnrichmentPatch[];
