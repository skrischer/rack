// Type declarations for the pure seed-import transforms in transform.mjs, so the
// rack-MCP test suite (TypeScript strict) can import them type-safely without an
// `any`. The runtime implementation lives in transform.mjs.

export interface WgerTranslation {
  name?: string;
  language?: number;
}

export interface WgerExercise {
  id?: number;
  category?: { name?: string } | null;
  muscles?: Array<{ name?: string; name_en?: string }>;
  equipment?: Array<{ name?: string }>;
  translations?: WgerTranslation[];
  license?: { short_name?: string };
  license_author?: string;
}

export interface SeedRow {
  name: string;
  category: string | null;
  muscles: string[];
  equipment: string[];
  license: string;
  licenseAuthor: string;
  aliases: string[];
  isCanonical: boolean;
}

export interface CuratedCanonical {
  name: string;
  category: string;
  muscles: string[];
  equipment: string[];
  aliases: string[];
}

export interface BuildRowsStats {
  source: number;
  dupes: number;
  skipped: number;
  canonical: number;
}

export interface BuildRowsResult {
  rows: SeedRow[];
  stats: BuildRowsStats;
}

export const LANGUAGE_EN: number;
export const DEFAULT_LICENSE: string;
export const DEFAULT_AUTHOR: string;
export const CURATED_CANONICAL: CuratedCanonical[];

export function fail(message: string): never;
export function nameKey(name: string): string;
export function englishName(exercise: WgerExercise): string | null;
export function extractAliases(exercise: WgerExercise, english: string): string[];
export function deriveEquipment(name: string, equipment: string[]): string[];
export function toRow(exercise: WgerExercise): SeedRow | null;
export function applyCuration(byKey: Map<string, SeedRow>): Map<string, SeedRow>;
export function buildRows(exercises: WgerExercise[]): BuildRowsResult;
export function buildSql(rows: SeedRow[]): string;
