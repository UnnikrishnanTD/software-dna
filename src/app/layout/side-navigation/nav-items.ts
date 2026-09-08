import { IconName } from '../../shared/ui/icon.component';

export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: IconName;
  /** Shown in the mobile bar; the full label is used elsewhere. */
  readonly shortLabel: string;
}

/** The analysis navigation, in the order the product intends it to be read. */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: '.', label: 'Overview', icon: 'overview', shortLabel: 'Overview' },
  { path: 'architecture', label: 'Architecture', icon: 'architecture', shortLabel: 'Arch' },
  { path: 'codebase', label: 'Codebase', icon: 'codebase', shortLabel: 'Code' },
  { path: 'hotspots', label: 'Hotspots', icon: 'hotspots', shortLabel: 'Hot' },
  { path: 'dependencies', label: 'Dependencies', icon: 'dependencies', shortLabel: 'Deps' },
  { path: 'evolution', label: 'Evolution', icon: 'evolution', shortLabel: 'Evo' },
  { path: 'ai-doctor', label: 'AI Doctor', icon: 'doctor', shortLabel: 'Doctor' },
  { path: 'compare', label: 'Compare', icon: 'compare', shortLabel: 'Compare' },
];
